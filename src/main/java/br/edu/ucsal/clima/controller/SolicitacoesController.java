package br.edu.ucsal.clima.controller;

import org.springframework.http.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.time.*;

@RestController
@RequestMapping("/api/v1/solicitacoes")
public class SolicitacoesController {
 private final NamedParameterJdbcTemplate db;
 public SolicitacoesController(NamedParameterJdbcTemplate db){this.db=db;}
 private UUID usuario(Authentication a){return db.queryForObject("SELECT id FROM usuario WHERE lower(email)=lower(:email)",Map.of("email",a.getName()),UUID.class);}
 private UUID perfil(Authentication a){var r=db.queryForList("SELECT id FROM perfil_usuario_final WHERE usuario_id=:id AND status='ATIVO'",Map.of("id",usuario(a)));if(r.isEmpty())throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Cadastre seu perfil de usuário final antes de solicitar");return (UUID)r.get(0).get("id");}
 @GetMapping("/meu-perfil") public List<Map<String,Object>> meuPerfil(Authentication a){return db.queryForList("SELECT * FROM perfil_usuario_final WHERE usuario_id=:id",Map.of("id",usuario(a)));}
 @PostMapping("/meu-perfil") @Transactional public Map<String,Object> cadastrarPerfil(Authentication a,@RequestBody Map<String,String> b){ contextoAuditoria();
  UUID uid=usuario(a);String documento=b.getOrDefault("documento","").trim(),nome=b.getOrDefault("nome","").trim();
  if(documento.isEmpty()||nome.isEmpty()||documento.length()>30||nome.length()>180)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nome ou documento inválidos");
  UUID dmc;try{dmc=UUID.fromString(b.get("dmc_id"));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"DMC inválido");}
  var p=Map.of("uid",uid,"nome",nome,"doc",documento,"dmc",dmc);
  db.update("INSERT INTO perfil_usuario_final(usuario_id,nome,documento,dmc_id) VALUES(:uid,:nome,:doc,:dmc) ON CONFLICT (usuario_id) DO UPDATE SET nome=:nome,documento=:doc,dmc_id=:dmc,data_atualizacao=now()",p);
  return db.queryForMap("SELECT * FROM perfil_usuario_final WHERE usuario_id=:uid",Map.of("uid",uid));
 }
 @GetMapping("/uso") public List<Map<String,Object>> listarUso(Authentication a){UUID uid=usuario(a);return db.queryForList("SELECT s.* FROM solicitacao_uso_estacao s JOIN perfil_usuario_final p ON p.id=s.usuario_solicitante_id WHERE p.usuario_id=:uid OR EXISTS(SELECT 1 FROM usuario_perfil up JOIN perfil pf ON pf.id=up.perfil_id WHERE up.usuario_id=:uid AND pf.nome='ADMINISTRADOR') OR (:colab AND EXISTS(SELECT 1 FROM tecnico t WHERE t.usuario_id=:uid AND t.status='ATIVO' AND t.dmc_id=s.dmc_id)) ORDER BY s.data_solicitacao DESC LIMIT 500",Map.of("uid",uid,"colab",a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_COLABORADOR"))));}
 @PostMapping("/uso") @Transactional @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> solicitarUso(Authentication a,@RequestBody Map<String,String>b){ contextoAuditoria();return salvarUso(a,null,b);}
 @PutMapping("/uso/{id}") @Transactional public Map<String,Object> editarUso(Authentication a,@PathVariable UUID id,@RequestBody Map<String,String>b){contextoAuditoria();propriaPendente(a,id,false);return salvarUso(a,id,b);}
 private Map<String,Object> salvarUso(Authentication a,UUID id,Map<String,String>b){
  UUID pf=perfil(a),est;LocalDate dia;LocalTime inicio,fim;
  try{est=UUID.fromString(b.get("estacao_id"));dia=LocalDate.parse(b.get("data_uso"));inicio=LocalTime.parse(b.get("hora_inicial"));fim=LocalTime.parse(b.get("hora_final"));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Data, horário ou estação inválidos");}
  if(!fim.isAfter(inicio)||dia.isBefore(LocalDate.now()))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Horário ou data inválidos");
  var estacoes=db.queryForList("SELECT e.status,d.id AS dmc_id FROM estacao_meteorologica e JOIN rede_monitoramento r ON r.id=e.rede_id JOIN dmc d ON d.id=r.dmc_id WHERE e.id=:id",Map.of("id",est));
  if(estacoes.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Estação inexistente");
  if(!"DISPONIVEL".equals(estacoes.get(0).get("status")))throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Estação indisponível");
  // Serializa aprovações concorrentes por estação, evitando reservas simultâneas aprovadas.
  db.queryForObject("SELECT id FROM estacao_meteorologica WHERE id=:id FOR UPDATE",Map.of("id",est),UUID.class);
  var p=new HashMap<String,Object>();p.put("est",est);p.put("dia",dia);p.put("inicio",inicio);p.put("fim",fim);
  Integer conflitos=db.queryForObject("SELECT count(*) FROM solicitacao_uso_estacao WHERE estacao_id=:est AND data_uso=:dia AND status='APROVADA' AND hora_inicial<:fim AND hora_final>:inicio",p,Integer.class);
  if(conflitos!=null&&conflitos>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Horário já reservado");
  p.put("pf",pf);p.put("dmc",estacoes.get(0).get("dmc_id"));p.put("id",id==null?UUID.randomUUID():id);
  String nome=db.queryForObject("SELECT nome FROM perfil_usuario_final WHERE id=:pf",Map.of("pf",pf),String.class);p.put("nome",nome);
  if(id==null) db.update("INSERT INTO solicitacao_uso_estacao(id,estacao_id,usuario_solicitante_id,nome_usuario,dmc_id,data_uso,hora_inicial,hora_final) VALUES(:id,:est,:pf,:nome,:dmc,:dia,:inicio,:fim)",p);
  else db.update("UPDATE solicitacao_uso_estacao SET estacao_id=:est,nome_usuario=:nome,dmc_id=:dmc,data_uso=:dia,hora_inicial=:inicio,hora_final=:fim WHERE id=:id",p);
  return db.queryForMap("SELECT * FROM solicitacao_uso_estacao WHERE id=:id",Map.of("id",p.get("id")));
 }
 @PatchMapping("/uso/{id}/decisao") @PreAuthorize("hasAnyRole('ADMINISTRADOR','COLABORADOR')") @Transactional public Map<String,Object> decidir(Authentication a,@PathVariable UUID id,@RequestBody Map<String,String>b){ contextoAuditoria();
  String status=b.getOrDefault("status","");if(!Set.of("APROVADA","REPROVADA").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Decisão inválida");
  var solicitacoes=db.queryForList("SELECT * FROM solicitacao_uso_estacao WHERE id=:id FOR UPDATE",Map.of("id",id));if(solicitacoes.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Solicitação inexistente");
  var s=solicitacoes.get(0);if(!"PENDENTE".equals(s.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Solicitação já decidida");
  UUID est=(UUID)s.get("estacao_id");autorizarDmc(a,est);db.queryForObject("SELECT id FROM estacao_meteorologica WHERE id=:id FOR UPDATE",Map.of("id",est),UUID.class);
  if(status.equals("APROVADA")){
   String situacao=db.queryForObject("SELECT status FROM estacao_meteorologica WHERE id=:id",Map.of("id",est),String.class);
   if(!"DISPONIVEL".equals(situacao))throw new ResponseStatusException(HttpStatus.CONFLICT,"Estação não está disponível");
   var p=Map.of("id",id,"est",est,"dia",s.get("data_uso"),"inicio",s.get("hora_inicial"),"fim",s.get("hora_final"));
   Integer n=db.queryForObject("SELECT count(*) FROM solicitacao_uso_estacao WHERE id<>:id AND estacao_id=:est AND data_uso=:dia AND status='APROVADA' AND hora_inicial<:fim AND hora_final>:inicio",p,Integer.class);
   if(n!=null&&n>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Conflito de horários com outra reserva aprovada");
  }
  String motivo=b.getOrDefault("justificativa","").trim();if(status.equals("REPROVADA")&&motivo.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Justificativa obrigatória");
  db.update("UPDATE solicitacao_uso_estacao SET status=:status,justificativa_reprovacao=:motivo,data_decisao=now(),tecnico_decisor_id=(SELECT t.id FROM tecnico t JOIN usuario u ON u.id=t.usuario_id WHERE lower(u.email)=lower(:email) AND t.status='ATIVO') WHERE id=:id",Map.of("status",status,"motivo",motivo,"id",id,"email",a.getName()));
  if(status.equals("APROVADA"))db.update("UPDATE estacao_meteorologica SET status='INDISPONIVEL',data_atualizacao=now() WHERE id=:id",Map.of("id",est));
  return db.queryForMap("SELECT * FROM solicitacao_uso_estacao WHERE id=:id",Map.of("id",id));
 }
 @PatchMapping("/uso/{id}/liberar") @Transactional public Map<String,Object> liberar(Authentication a,@PathVariable UUID id){ contextoAuditoria();
  UUID pf=perfil(a);
  var registros=db.queryForList("SELECT * FROM solicitacao_uso_estacao WHERE id=:id FOR UPDATE",Map.of("id",id));
  if(registros.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Reserva não encontrada");
  var registro=registros.get(0);
  if(!pf.equals(registro.get("usuario_solicitante_id")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Reserva de outro usuário");
  if(!"APROVADA".equals(registro.get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Somente reserva aprovada pode ser liberada");
  UUID est=(UUID)registro.get("estacao_id");
  db.queryForObject("SELECT id FROM estacao_meteorologica WHERE id=:id FOR UPDATE",Map.of("id",est),UUID.class);
  db.update("UPDATE solicitacao_uso_estacao SET status='FINALIZADA' WHERE id=:id",Map.of("id",id));
  Integer outras=db.queryForObject("SELECT count(*) FROM solicitacao_uso_estacao WHERE estacao_id=:id AND status='APROVADA'",Map.of("id",est),Integer.class);
  if(outras!=null&&outras==0)db.update("UPDATE estacao_meteorologica SET status='DISPONIVEL',data_atualizacao=now() WHERE id=:id AND status='INDISPONIVEL'",Map.of("id",est));
  return db.queryForMap("SELECT * FROM solicitacao_uso_estacao WHERE id=:id",Map.of("id",id));
 }
 @PatchMapping("/uso/{id}/cancelar") @Transactional public void cancelar(Authentication a,@PathVariable UUID id){ contextoAuditoria();int n=db.update("UPDATE solicitacao_uso_estacao SET status='CANCELADA' WHERE id=:id AND status='PENDENTE' AND usuario_solicitante_id=:pf",Map.of("id",id,"pf",perfil(a)));if(n==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Não é possível cancelar");}
 @GetMapping("/instalacao") public List<Map<String,Object>> instalacoes(Authentication a){UUID uid=usuario(a);return db.queryForList("SELECT s.* FROM solicitacao_instalacao s JOIN perfil_usuario_final p ON p.id=s.solicitante_id WHERE p.usuario_id=:uid OR EXISTS(SELECT 1 FROM usuario_perfil up JOIN perfil pf ON pf.id=up.perfil_id WHERE up.usuario_id=:uid AND pf.nome='ADMINISTRADOR') OR (:colab AND EXISTS(SELECT 1 FROM tecnico t JOIN estacao_meteorologica e ON e.id=s.estacao_id JOIN rede_monitoramento r ON r.id=e.rede_id WHERE t.usuario_id=:uid AND t.status='ATIVO' AND t.dmc_id=r.dmc_id)) ORDER BY s.data_solicitacao DESC LIMIT 500",Map.of("uid",uid,"colab",a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_COLABORADOR"))));}
 @PostMapping("/instalacao") @Transactional @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> solicitarInstalacao(Authentication a,@RequestBody Map<String,String>b){ contextoAuditoria();return salvarInstalacao(a,null,b);}
 @PutMapping("/instalacao/{id}") @Transactional public Map<String,Object> editarInstalacao(Authentication a,@PathVariable UUID id,@RequestBody Map<String,String>b){contextoAuditoria();propriaPendente(a,id,true);return salvarInstalacao(a,id,b);}
 private Map<String,Object> salvarInstalacao(Authentication a,UUID id,Map<String,String>b){
  UUID est;try{est=UUID.fromString(b.get("estacao_id"));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estação inválida");}
  String tipo=b.getOrDefault("item_tipo","");if(!Set.of("SENSOR","SOFTWARE").contains(tipo))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Item inválido");
  var p=new HashMap<String,Object>();p.put("id",id==null?UUID.randomUUID():id);p.put("est",est);p.put("pf",perfil(a));p.put("tipo",tipo);
  p.put("sensor",tipo.equals("SENSOR")?b.get("sensor"):null);p.put("nome",tipo.equals("SOFTWARE")?b.get("nome_software"):null);p.put("url",tipo.equals("SOFTWARE")?b.get("url_download"):null);p.put("software",tipo.equals("SOFTWARE")?b.get("tipo_software"):null);p.put("objetivo",tipo.equals("SOFTWARE")?b.get("objetivo_uso"):null);
  if(tipo.equals("SENSOR")&&!Set.of("TEMPERATURA","UMIDADE","PRESSAO","VENTO","PLUVIOMETRO").contains(Objects.toString(p.get("sensor"),"")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sensor inválido");
  if(tipo.equals("SOFTWARE")&&(p.get("nome")==null||p.get("url")==null||p.get("objetivo")==null||!Set.of("PROPRIETARIO","SOFTWARE_LIVRE").contains(Objects.toString(p.get("software"),""))))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dados do software incompletos");
  if(tipo.equals("SOFTWARE")&&(Objects.toString(p.get("nome"),"").isBlank()||Objects.toString(p.get("objetivo"),"").isBlank()||!Objects.toString(p.get("url"),"").matches("https?://[^\\s]+")||b.getOrDefault("variaveis_monitoradas","").isBlank()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe nome, URL http(s), objetivo e variáveis do software");
  p.put("vars",b.getOrDefault("variaveis_monitoradas",""));
  if(id==null) db.update("INSERT INTO solicitacao_instalacao(id,estacao_id,solicitante_id,item_tipo,sensor,nome_software,url_download,tipo_software,objetivo_uso,variaveis_monitoradas) VALUES(:id,:est,:pf,:tipo,:sensor,:nome,:url,:software,:objetivo,string_to_array(:vars,','))",p);
  else db.update("UPDATE solicitacao_instalacao SET estacao_id=:est,item_tipo=:tipo,sensor=:sensor,nome_software=:nome,url_download=:url,tipo_software=:software,objetivo_uso=:objetivo,variaveis_monitoradas=string_to_array(:vars,',') WHERE id=:id",p);
  return db.queryForMap("SELECT * FROM solicitacao_instalacao WHERE id=:id",Map.of("id",p.get("id")));
 }
 private void propriaPendente(Authentication a,UUID id,boolean instalacao) {
  String tabela=instalacao?"solicitacao_instalacao":"solicitacao_uso_estacao",campo=instalacao?"solicitante_id":"usuario_solicitante_id";
  var r=db.queryForList("SELECT status,"+campo+" AS dono FROM "+tabela+" WHERE id=:id FOR UPDATE",Map.of("id",id));
  if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Solicitação inexistente");
  if(!perfil(a).equals(r.get(0).get("dono")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Solicitação de outro usuário");
  if(!"PENDENTE".equals(r.get(0).get("status")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Somente solicitações pendentes podem ser editadas ou inativadas");
 }
 @PatchMapping("/{tipo}/{id}/inativar") @Transactional
 public void inativar(Authentication a,@PathVariable String tipo,@PathVariable UUID id,@RequestBody Map<String,String>b) {
  contextoAuditoria();
  if(!Set.of("uso","instalacao").contains(tipo))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  boolean instalacao=tipo.equals("instalacao");propriaPendente(a,id,instalacao);
  String motivo=b.getOrDefault("motivo","").trim();if(motivo.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe o motivo");
  db.update("UPDATE "+(instalacao?"solicitacao_instalacao":"solicitacao_uso_estacao")+" SET status='INATIVA',justificativa_inativacao=:m WHERE id=:id",Map.of("id",id,"m",motivo));
 }
 private void autorizarDmc(Authentication a,UUID estacao){
  UUID uid=usuario(a);
  Integer admin=db.queryForObject("SELECT count(*) FROM usuario_perfil up JOIN perfil p ON p.id=up.perfil_id WHERE up.usuario_id=:uid AND p.nome='ADMINISTRADOR'",Map.of("uid",uid),Integer.class);
  if(admin!=null&&admin>0)return;
  Integer n=db.queryForObject("SELECT count(*) FROM tecnico t JOIN rede_monitoramento r ON r.dmc_id=t.dmc_id JOIN estacao_meteorologica e ON e.rede_id=r.id WHERE t.usuario_id=:uid AND t.status='ATIVO' AND e.id=:est",Map.of("uid",uid,"est",estacao),Integer.class);
  if(n==null||n==0)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Estação fora do DMC do colaborador");
 }
 @PatchMapping("/instalacao/{id}/decisao") @PreAuthorize("hasAnyRole('ADMINISTRADOR','COLABORADOR')") @Transactional public Map<String,Object> decidirInstalacao(Authentication a,@PathVariable UUID id,@RequestBody Map<String,String>b){ contextoAuditoria();String status=b.getOrDefault("status","");if(!Set.of("APROVADA","REPROVADA","INSTALADA").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Status inválido");var p=db.queryForList("SELECT * FROM solicitacao_instalacao WHERE id=:id FOR UPDATE",Map.of("id",id));if(p.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);autorizarDmc(a,(UUID)p.get(0).get("estacao_id"));String atual=(String)p.get(0).get("status");if(!(atual.equals("PENDENTE")&&!status.equals("INSTALADA")||atual.equals("APROVADA")&&status.equals("INSTALADA")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Transição inválida");String motivo=b.getOrDefault("justificativa","");if(status.equals("REPROVADA")&&motivo.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Justificativa obrigatória");if(status.equals("INSTALADA")) {
 var item=p.get(0);UUID est=(UUID)item.get("estacao_id");
 db.queryForObject("SELECT id FROM estacao_meteorologica WHERE id=:id FOR UPDATE",Map.of("id",est),UUID.class);
 if("SENSOR".equals(item.get("item_tipo")))db.update("INSERT INTO estacao_sensor(estacao_id,sensor) VALUES(:e,:s) ON CONFLICT DO NOTHING",Map.of("e",est,"s",item.get("sensor")));
 else db.update("INSERT INTO software_estacao(estacao_id,nome,url_download,tipo,objetivo_uso,variaveis_monitoradas) SELECT estacao_id,nome_software,url_download,tipo_software,objetivo_uso,variaveis_monitoradas FROM solicitacao_instalacao WHERE id=:id",Map.of("id",id));
 }
 db.update("UPDATE solicitacao_instalacao SET status=:s,justificativa_reprovacao=:m,data_decisao=now(),tecnico_decisor_id=(SELECT t.id FROM tecnico t JOIN usuario u ON u.id=t.usuario_id WHERE lower(u.email)=lower(:email) AND t.status='ATIVO') WHERE id=:id",Map.of("s",status,"m",motivo,"id",id,"email",a.getName()));return db.queryForMap("SELECT * FROM solicitacao_instalacao WHERE id=:id",Map.of("id",id));}
 private void contextoAuditoria() {
  var a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  if(a!=null) db.queryForObject("SELECT set_config('clima.usuario_id',COALESCE((SELECT id::text FROM usuario WHERE lower(email)=lower(:email)),''),true)",Map.of("email",a.getName()),String.class);
 }
}
