package br.edu.ucsal.clima.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/estacoes")
public class EstacaoController {
 private final NamedParameterJdbcTemplate db;
 public EstacaoController(NamedParameterJdbcTemplate db){this.db=db;}
 @GetMapping public List<Map<String,Object>> listar(){return db.queryForList("SELECT * FROM estacao_meteorologica ORDER BY nome LIMIT 500",Map.of());}
 @GetMapping("/{id}") public Map<String,Object> obter(@PathVariable UUID id){var r=db.queryForList("SELECT * FROM estacao_meteorologica WHERE id=:id",Map.of("id",id));if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Estação não encontrada");var est=r.get(0);
 est.put("sensores",db.queryForList("SELECT sensor FROM estacao_sensor WHERE estacao_id=:id ORDER BY sensor",Map.of("id",id),String.class));
 est.put("softwares",db.queryForList("SELECT * FROM software_estacao WHERE estacao_id=:id ORDER BY nome",Map.of("id",id)));
 return est;}
 @PostMapping @PreAuthorize("hasAnyRole('ADMINISTRADOR','COLABORADOR')") @Transactional @ResponseStatus(HttpStatus.CREATED)
 public Map<String,Object> criar(Authentication a,@RequestBody Map<String,Object> b){ contextoAuditoria();return salvar(a,null,b);}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMINISTRADOR','COLABORADOR')") @Transactional
 public Map<String,Object> atualizar(Authentication a,@PathVariable UUID id,@RequestBody Map<String,Object> b){ contextoAuditoria();obter(id);return salvar(a,id,b);}
 private Map<String,Object> salvar(Authentication a,UUID id,Map<String,Object> b){
  String sigla=texto(b,"sigla"),nome=texto(b,"nome"),tipo=texto(b,"tipo");
  if(!Set.of("AUTOMATICA","CONVENCIONAL","RADAR_METEOROLOGICO","BOIA_OCEANOGRAFICA").contains(tipo))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tipo inválido");
  int capacidade; BigDecimal lat,lon;UUID rede;
  try{capacidade=Integer.parseInt(texto(b,"capacidade_maxima"));lat=new BigDecimal(texto(b,"latitude"));lon=new BigDecimal(texto(b,"longitude"));rede=UUID.fromString(texto(b,"rede_id"));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Capacidade, coordenadas ou rede inválidas");}
  autorizarRede(a,rede);
  if(id!=null) autorizarEstacao(a,id);
  if(capacidade<=0||lat.compareTo(BigDecimal.valueOf(-90))<0||lat.compareTo(BigDecimal.valueOf(90))>0||lon.compareTo(BigDecimal.valueOf(-180))<0||lon.compareTo(BigDecimal.valueOf(180))>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Capacidade ou coordenadas fora dos limites");
  var p=new HashMap<String,Object>();p.put("id",id==null?UUID.randomUUID():id);p.put("sigla",sigla);p.put("nome",nome);p.put("tipo",tipo);p.put("cap",capacidade);p.put("lat",lat);p.put("lon",lon);p.put("rede",rede);p.put("descricao",b.get("descricao_complementar"));
  if(id==null)db.update("INSERT INTO estacao_meteorologica(id,sigla,nome,tipo,capacidade_maxima,latitude,longitude,rede_id,descricao_complementar) VALUES(:id,:sigla,:nome,:tipo,:cap,:lat,:lon,:rede,:descricao)",p);
  else db.update("UPDATE estacao_meteorologica SET sigla=:sigla,nome=:nome,tipo=:tipo,capacidade_maxima=:cap,latitude=:lat,longitude=:lon,rede_id=:rede,descricao_complementar=:descricao,data_atualizacao=now() WHERE id=:id",p);
  salvarEquipamentos((UUID)p.get("id"),tipo,b); return obter((UUID)p.get("id"));
 }
 @PatchMapping("/{id}/status") @PreAuthorize("hasAnyRole('ADMINISTRADOR','COLABORADOR')") @Transactional
 public Map<String,Object> status(Authentication a,@PathVariable UUID id,@RequestBody Map<String,String> b){ contextoAuditoria();obter(id);autorizarEstacao(a,id);String s=b.getOrDefault("status","");if(!Set.of("DISPONIVEL","INDISPONIVEL","INATIVA","MANUTENCAO").contains(s))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Status inválido");String motivo=b.getOrDefault("motivo","").trim();
 if(Set.of("INATIVA","MANUTENCAO").contains(s)&&motivo.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe o motivo da inativação/manutenção");
 if(s.equals("DISPONIVEL")) {
  Integer reservas=db.queryForObject("SELECT count(*) FROM solicitacao_uso_estacao WHERE estacao_id=:id AND status='APROVADA'",Map.of("id",id),Integer.class);
  if(reservas!=null&&reservas>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"A estação possui reserva aprovada; aguarde a liberação");
 }
 db.update("UPDATE estacao_meteorologica SET justificativa_inativacao=:m WHERE id=:id",Map.of("id",id,"m",motivo));
 db.update("UPDATE estacao_meteorologica SET status=:s,data_atualizacao=now() WHERE id=:id",Map.of("id",id,"s",s));return obter(id);}
 private void salvarEquipamentos(UUID id,String tipo,Map<String,Object> b) {
  boolean especial=Set.of("AUTOMATICA","RADAR_METEOROLOGICO").contains(tipo);
  if(b.containsKey("sensores")) {
   if(!(b.get("sensores") instanceof List<?> sensores))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sensores devem ser uma lista");
   Set<String> permitidos=Set.of("TEMPERATURA","UMIDADE","PRESSAO","VENTO","PLUVIOMETRO");
   for(Object sensor:sensores)if(!permitidos.contains(Objects.toString(sensor,"")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sensor inválido");
   db.update("DELETE FROM estacao_sensor WHERE estacao_id=:id",Map.of("id",id));
   for(Object sensor:new HashSet<>(sensores))db.update("INSERT INTO estacao_sensor(estacao_id,sensor) VALUES(:id,:s)",Map.of("id",id,"s",sensor));
  }
  if(b.containsKey("softwares")) {
   if(!(b.get("softwares") instanceof List<?> softwares))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Softwares devem ser uma lista");
   db.update("DELETE FROM software_estacao WHERE estacao_id=:id",Map.of("id",id));
   for(Object raw:softwares) {
    if(!(raw instanceof Map<?,?> sw))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Software inválido");
    String nome=Objects.toString(sw.get("nome"),"").trim(),url=Objects.toString(sw.get("url_download"),"").trim(),licenca=Objects.toString(sw.get("tipo"),""),objetivo=Objects.toString(sw.get("objetivo_uso"),"").trim();
    if(nome.isBlank()||nome.length()>160||objetivo.isBlank()||!url.matches("https?://[^\\s]+")||!Set.of("PROPRIETARIO","SOFTWARE_LIVRE").contains(licenca))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nome, URL http(s), licença e objetivo são obrigatórios no software");
    if(!(sw.get("variaveis_monitoradas") instanceof List<?> vars)||vars.isEmpty()||vars.stream().anyMatch(v->!(v instanceof String)||v.toString().isBlank()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe as variáveis monitoradas do software");
    var p=new HashMap<String,Object>();p.put("id",id);p.put("nome",nome);p.put("url",url);p.put("tipo",licenca);p.put("obj",objetivo);
    // Named parameters expand the list as ARRAY[:v0,...], keeping all values bound.
    List<String> placeholders=new ArrayList<>();for(int i=0;i<vars.size();i++){p.put("v"+i,vars.get(i));placeholders.add(":v"+i);}
    db.update("INSERT INTO software_estacao(estacao_id,nome,url_download,tipo,objetivo_uso,variaveis_monitoradas) VALUES(:id,:nome,:url,:tipo,:obj,ARRAY["+String.join(",",placeholders)+"]::text[])",p);
   }
  }
  if(especial) {
   Integer n=db.queryForObject("SELECT count(*) FROM estacao_sensor WHERE estacao_id=:id",Map.of("id",id),Integer.class);
   if(n==null||n==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe os sensores da estação automática ou radar");
  }
 }
 private void autorizarRede(Authentication a,UUID rede){
  if(admin(a))return;
  Integer n=db.queryForObject("SELECT count(*) FROM tecnico t JOIN rede_monitoramento r ON r.dmc_id=t.dmc_id WHERE r.id=:rede AND t.usuario_id=(SELECT id FROM usuario WHERE lower(email)=lower(:email)) AND t.status='ATIVO' AND r.status='ATIVO'",Map.of("rede",rede,"email",a.getName()),Integer.class);
  if(n==null||n==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Rede fora do DMC do colaborador");
 }
 private void autorizarEstacao(Authentication a,UUID id){
  if(admin(a))return;
  Integer n=db.queryForObject("SELECT count(*) FROM estacao_meteorologica e JOIN rede_monitoramento r ON r.id=e.rede_id JOIN tecnico t ON t.dmc_id=r.dmc_id WHERE e.id=:id AND t.usuario_id=(SELECT id FROM usuario WHERE lower(email)=lower(:email)) AND t.status='ATIVO'",Map.of("id",id,"email",a.getName()),Integer.class);
  if(n==null||n==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Estação fora do DMC do colaborador");
 }
 private boolean admin(Authentication a){return a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMINISTRADOR"));}
 private String texto(Map<String,Object>b,String k){String s=Objects.toString(b.get(k),"").trim();if(s.isEmpty()||s.length()>180)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Campo inválido: "+k);return s;}
 private void contextoAuditoria() {
  var a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  if(a!=null) db.queryForObject("SELECT set_config('clima.usuario_id',COALESCE((SELECT id::text FROM usuario WHERE lower(email)=lower(:email)),''),true)",Map.of("email",a.getName()),String.class);
 }
}
