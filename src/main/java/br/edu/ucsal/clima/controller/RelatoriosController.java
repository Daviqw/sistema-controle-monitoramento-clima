package br.edu.ucsal.clima.controller;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.util.*;

@RestController
@RequestMapping("/api/v1/relatorios")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','COLABORADOR','USUARIO')")
public class RelatoriosController {
 private final NamedParameterJdbcTemplate db;
 public RelatoriosController(NamedParameterJdbcTemplate db){this.db=db;}
 private List<Map<String,Object>> registros(String tipo, org.springframework.security.core.Authentication a) {
  boolean admin=a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMINISTRADOR"));
  boolean colaborador=a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_COLABORADOR"));
  String sql=switch(tipo) {
   case "ogms" -> "SELECT o.*,(SELECT string_agg(c.nome||': '||c.nomeado, '; ') FROM ogm_coordenadoria c WHERE c.ogm_id=o.id) AS coordenadorias FROM ogm o WHERE :admin ORDER BY o.nome";
   case "dmcs" -> "SELECT d.*,o.sigla AS ogm FROM dmc d JOIN ogm o ON o.id=d.ogm_id WHERE :admin ORDER BY d.nome";
   case "redes" -> "SELECT r.*,d.nome AS dmc,o.sigla AS ogm,(SELECT string_agg(tipo,', ') FROM rede_tipo_monitoramento WHERE rede_id=r.id) AS tipos FROM rede_monitoramento r JOIN dmc d ON d.id=r.dmc_id JOIN ogm o ON o.id=d.ogm_id WHERE :admin ORDER BY r.sigla";
   case "tecnicos" -> "SELECT t.matricula,t.nome_completo,t.email_contato,t.telefone_contato,t.status,d.nome AS dmc FROM tecnico t JOIN dmc d ON d.id=t.dmc_id WHERE :admin ORDER BY t.nome_completo";
   case "estacoes" -> "SELECT e.*,r.sigla AS rede,d.nome AS dmc,(SELECT string_agg(sensor,', ') FROM estacao_sensor WHERE estacao_id=e.id) AS sensores,(SELECT json_agg(s)::text FROM software_estacao s WHERE s.estacao_id=e.id) AS softwares FROM estacao_meteorologica e JOIN rede_monitoramento r ON r.id=e.rede_id JOIN dmc d ON d.id=r.dmc_id WHERE :admin OR (:colab AND EXISTS(SELECT 1 FROM tecnico t JOIN usuario u ON u.id=t.usuario_id WHERE lower(u.email)=lower(:email) AND t.status='ATIVO' AND t.dmc_id=d.id)) ORDER BY e.nome";
   case "uso" -> "SELECT s.*,e.nome AS estacao FROM solicitacao_uso_estacao s JOIN estacao_meteorologica e ON e.id=s.estacao_id JOIN perfil_usuario_final p ON p.id=s.usuario_solicitante_id JOIN usuario u ON u.id=p.usuario_id WHERE :admin OR lower(u.email)=lower(:email) OR (:colab AND EXISTS(SELECT 1 FROM tecnico t JOIN usuario c ON c.id=t.usuario_id WHERE lower(c.email)=lower(:email) AND t.status='ATIVO' AND t.dmc_id=s.dmc_id)) ORDER BY s.data_solicitacao DESC";
   case "instalacoes" -> "SELECT s.*,e.nome AS estacao FROM solicitacao_instalacao s JOIN estacao_meteorologica e ON e.id=s.estacao_id JOIN rede_monitoramento r ON r.id=e.rede_id JOIN perfil_usuario_final p ON p.id=s.solicitante_id JOIN usuario u ON u.id=p.usuario_id WHERE :admin OR lower(u.email)=lower(:email) OR (:colab AND EXISTS(SELECT 1 FROM tecnico t JOIN usuario c ON c.id=t.usuario_id WHERE lower(c.email)=lower(:email) AND t.status='ATIVO' AND t.dmc_id=r.dmc_id)) ORDER BY s.data_solicitacao DESC";
   default -> throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Relatório inexistente");
  };
  if(!admin && !Set.of("uso","instalacoes","estacoes").contains(tipo) || !admin&&!colaborador&&tipo.equals("estacoes"))
   throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"Relatório não permitido ao perfil");
  return db.queryForList(sql,Map.of("admin",admin,"colab",colaborador,"email",a.getName()));
 }
 @GetMapping("/{tipo}.pdf")
 public ResponseEntity<byte[]> pdf(@PathVariable String tipo, org.springframework.security.core.Authentication a) throws IOException {
  var registros=registros(tipo,a);
  try(PDDocument document=new PDDocument();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
   PDType1Font normal=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
   PDType1Font negrito=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
   PDPage pagina=null;PDPageContentStream stream=null;float y=0;
   List<String> linhas=new ArrayList<>();
   linhas.add("Relatorio de " + tipo + " | Sistema CLIMA");
   linhas.add("Responsavel: " + a.getName());
   linhas.add("Emissao: " + java.time.OffsetDateTime.now());
   linhas.add("Total de registros: " + registros.size());linhas.add(" ");
   for(var registro:registros){
    for(var campo:registro.entrySet()){
     String valor=Objects.toString(campo.getValue(),"").replace('\n',' ').replace('\r',' ');
     String linha=campo.getKey()+": "+valor;
     while(linha.length()>92){linhas.add(linha.substring(0,92));linha="    "+linha.substring(92);}
     linhas.add(linha);
    }
    linhas.add("------------------------------------------------------------");
   }
   for(int i=0;i<linhas.size();i++){
    if(stream==null || y<45){
     if(stream!=null)stream.close();
     pagina=new PDPage(PDRectangle.A4);document.addPage(pagina);
     stream=new PDPageContentStream(document,pagina);y=790;
    }
    String texto=linhas.get(i).replaceAll("[^\\p{InBasic_Latin}\\p{InLatin_1_Supplement}]","?");
    stream.beginText();stream.setFont(i==0?negrito:normal,i==0?14:9);
    stream.newLineAtOffset(38,y);stream.showText(texto);stream.endText();y-=i==0?22:13;
   }
   if(stream!=null)stream.close();
   if(document.getNumberOfPages()==0)document.addPage(new PDPage(PDRectangle.A4));
   document.save(bytes);
   return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=clima-"+tipo+".pdf")
    .contentType(MediaType.APPLICATION_PDF).body(bytes.toByteArray());
  }
 }
 @GetMapping("/{tipo}.csv")
 public ResponseEntity<byte[]> csv(@PathVariable String tipo,org.springframework.security.core.Authentication a) {
  var rows=registros(tipo,a);
  var sb=new StringBuilder("\uFEFF");
  sb.append("Responsavel;").append(celula(a.getName())).append('\n');
  sb.append("Emissao;").append(celula(java.time.OffsetDateTime.now())).append('\n');
  if(!rows.isEmpty()) {
   var keys=new ArrayList<>(rows.get(0).keySet());
   sb.append(String.join(";",keys)).append('\n');
   for(var row:rows) {
    for(int i=0;i<keys.size();i++) {if(i>0)sb.append(';');sb.append(celula(row.get(keys.get(i))));}
    sb.append('\n');
   }
  } else sb.append("Nenhum registro\n");
  return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=clima-"+tipo+".csv")
   .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(sb.toString().getBytes(StandardCharsets.UTF_8));
 }
 private String celula(Object raw) {
  String v=Objects.toString(raw,"");
  if(v.stripLeading().matches("^[=+@-].*"))v="'"+v;
  return "\""+v.replace("\"","\"\"")+"\"";
 }
}
