package br.edu.ucsal.clima.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/cadastros")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminCadastrosController {
    private final NamedParameterJdbcTemplate db;
    public AdminCadastrosController(NamedParameterJdbcTemplate db) { this.db = db; }

    // Only whitelisted table/column names ever enter SQL identifiers.
    private record Tipo(String tabela, List<String> campos, List<String> obrigatorios, List<String> referencias) {}
    private static final Map<String, Tipo> TIPOS = Map.of(
        "ogms", new Tipo("ogm", List.of("sigla","nome","email_central","telefone_central","diretor_geral_nome"), List.of("sigla","nome","diretor_geral_nome"), List.of()),
        "dmcs", new Tipo("dmc", List.of("ogm_id","nome","email_central","telefone_central","meteorologista_chefe_nome"), List.of("ogm_id","nome","meteorologista_chefe_nome"), List.of("ogm_id")),
        "redes", new Tipo("rede_monitoramento", List.of("sigla","descricao","dmc_id","meteorologista_responsavel_id"), List.of("sigla","descricao","dmc_id"), List.of("dmc_id","meteorologista_responsavel_id")),
        "tecnicos", new Tipo("tecnico", List.of("matricula","nome_completo","email_contato","telefone_contato","dmc_id","usuario_id"), List.of("matricula","nome_completo","email_contato","dmc_id","usuario_id"), List.of("dmc_id","usuario_id"))
    );
    private Tipo tipo(String chave) {
        Tipo t = TIPOS.get(chave);
        if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro inexistente");
        return t;
    }
    @GetMapping("/{chave}")
    public List<Map<String,Object>> listar(@PathVariable String chave) {
        Tipo t = tipo(chave);
        var lista=db.queryForList("SELECT * FROM " + t.tabela() + " ORDER BY data_cadastro DESC LIMIT 300", Map.of());
        if(chave.equals("redes"))for(var item:lista)item.put("tipos_monitoramento",db.queryForList("SELECT tipo FROM rede_tipo_monitoramento WHERE rede_id=:id ORDER BY tipo",Map.of("id",item.get("id")),String.class));
        return lista;
    }
    @PostMapping("/{chave}")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String,Object> criar(@PathVariable String chave, @RequestBody Map<String,Object> corpo) { contextoAuditoria();
        Tipo t = tipo(chave); Map<String,Object> valores = validar(t,corpo); validarVinculos(chave,valores);
        UUID id = UUID.randomUUID(); valores.put("id",id);
        List<String> colunas = new ArrayList<>(valores.keySet());
        String sql = "INSERT INTO " + t.tabela() + " (" + String.join(",",colunas) + ") VALUES (" + String.join(",",colunas.stream().map(c -> ":"+c).toList()) + ")";
        db.update(sql,valores); salvarTipos(chave,id,corpo); return obter(t,id);
    }
    @PutMapping("/{chave}/{id}")
    @Transactional
    public Map<String,Object> atualizar(@PathVariable String chave, @PathVariable UUID id, @RequestBody Map<String,Object> corpo) { contextoAuditoria();
        Tipo t=tipo(chave); obter(t,id); Map<String,Object> valores=validar(t,corpo); validarVinculos(chave,valores); valores.put("id",id);
        String sets=String.join(",",t.campos().stream().map(c -> c+" = :"+c).toList());
        db.update("UPDATE "+t.tabela()+" SET "+sets+", data_atualizacao=now() WHERE id=:id",valores); salvarTipos(chave,id,corpo);
        return obter(t,id);
    }
    @PatchMapping("/{chave}/{id}/inativar")
    @Transactional
    public Map<String,Object> inativar(@PathVariable String chave, @PathVariable UUID id, @RequestBody Map<String,String> corpo) { contextoAuditoria();
        Tipo t=tipo(chave); obter(t,id);
        String motivo=Objects.toString(corpo.get("motivo"), "").trim();
        if(motivo.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe o motivo da inativação");
        db.update("UPDATE "+t.tabela()+" SET justificativa_inativacao=:motivo WHERE id=:id",Map.of("id",id,"motivo",motivo));
        db.update("UPDATE "+t.tabela()+" SET status='INATIVO',data_atualizacao=now() WHERE id=:id",Map.of("id",id));
        return obter(t,id);
    }
    @GetMapping("/ogms/{id}/coordenadorias")
    public List<Map<String,Object>> coordenadorias(@PathVariable UUID id){
        return db.queryForList("SELECT * FROM ogm_coordenadoria WHERE ogm_id=:id ORDER BY nome",Map.of("id",id));
    }
    @PostMapping("/ogms/{id}/coordenadorias") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> adicionarCoordenadoria(@PathVariable UUID id,@RequestBody Map<String,String> body){ contextoAuditoria();
        obter(tipo("ogms"),id);
        String nome=Objects.toString(body.get("nome"),"").trim(),nomeado=Objects.toString(body.get("nomeado"),"").trim();
        if(nome.isBlank()||nomeado.isBlank()||nome.length()>160||nomeado.length()>180)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nome e nomeação são obrigatórios");
        UUID cid=UUID.randomUUID();db.update("INSERT INTO ogm_coordenadoria(id,ogm_id,nome,nomeado) VALUES(:cid,:id,:nome,:nomeado)",Map.of("cid",cid,"id",id,"nome",nome,"nomeado",nomeado));
        return db.queryForMap("SELECT * FROM ogm_coordenadoria WHERE id=:id",Map.of("id",cid));
    }
    @PutMapping("/ogms/{id}/coordenadorias/{cid}") @Transactional
    public Map<String,Object> atualizarCoordenadoria(@PathVariable UUID id,@PathVariable UUID cid,@RequestBody Map<String,String> body){ contextoAuditoria();
        String nome=Objects.toString(body.get("nome"),"").trim(),nomeado=Objects.toString(body.get("nomeado"),"").trim();
        if(nome.isBlank()||nomeado.isBlank()||nome.length()>160||nomeado.length()>180)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dados inválidos");
        int n=db.update("UPDATE ogm_coordenadoria SET nome=:nome,nomeado=:nomeado WHERE id=:cid AND ogm_id=:id",Map.of("nome",nome,"nomeado",nomeado,"cid",cid,"id",id));
        if(n==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return db.queryForMap("SELECT * FROM ogm_coordenadoria WHERE id=:cid",Map.of("cid",cid));
    }
    private void salvarTipos(String chave,UUID id,Map<String,Object> corpo){
        if(!"redes".equals(chave)||!corpo.containsKey("tipos_monitoramento"))return;
        Object raw=corpo.get("tipos_monitoramento");
        if(!(raw instanceof List<?> tipos) || tipos.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tipos devem ser uma lista");
        Set<String> permitidos=Set.of("TEMPERATURA","UMIDADE","PRESSAO","VENTO","PRECIPITACAO","RADAR","CONVENCIONAL","OCEANOGRAFICO","ALERTA_DESLIZAMENTO");
        for(Object item:tipos)if(!(item instanceof String valor)||!permitidos.contains(valor))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tipo de monitoramento inválido");
        db.update("DELETE FROM rede_tipo_monitoramento WHERE rede_id=:id",Map.of("id",id));
        for(Object item:new HashSet<>(tipos))db.update("INSERT INTO rede_tipo_monitoramento(rede_id,tipo) VALUES(:id,:tipo)",Map.of("id",id,"tipo",item));
    }
    private void validarVinculos(String chave, Map<String,Object> v) {
        if("tecnicos".equals(chave)) {
            Integer n=db.queryForObject("SELECT count(*) FROM usuario u JOIN usuario_perfil up ON up.usuario_id=u.id JOIN perfil p ON p.id=up.perfil_id WHERE u.id=:id AND u.ativo AND p.nome='COLABORADOR'",Map.of("id",v.get("usuario_id")),Integer.class);
            if(n==null||n==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vincule uma conta ativa com perfil COLABORADOR");
        }
        if("redes".equals(chave)&&v.get("meteorologista_responsavel_id")!=null) {
            Integer n=db.queryForObject("SELECT count(*) FROM tecnico WHERE id=:id AND dmc_id=:dmc AND status='ATIVO'",Map.of("id",v.get("meteorologista_responsavel_id"),"dmc",v.get("dmc_id")),Integer.class);
            if(n==null||n==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Responsável deve ser técnico ativo do DMC da rede");
        }
    }
    private Map<String,Object> validar(Tipo t,Map<String,Object> entrada) {
        Map<String,Object> valores=new LinkedHashMap<>();
        for (String campo:t.campos()) {
            Object raw=entrada.get(campo);
            String str=raw==null ? "" : raw.toString().trim();
            if (t.obrigatorios().contains(campo) && str.isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Campo obrigatório: "+campo);
            if (str.length()>240) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Campo muito longo: "+campo);
            if (t.referencias().contains(campo) && !str.isEmpty()) {
                try { valores.put(campo,UUID.fromString(str)); }
                catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Identificador inválido: "+campo); }
            } else valores.put(campo,str.isEmpty()?null:str);
        }
        return valores;
    }
    private Map<String,Object> obter(Tipo t,UUID id) {
        List<Map<String,Object>> encontrados=db.queryForList("SELECT * FROM "+t.tabela()+" WHERE id=:id",Map.of("id",id));
        if (encontrados.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Registro não encontrado");
        return encontrados.get(0);
    }
 private void contextoAuditoria() {
  var a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
  if(a!=null) db.queryForObject("SELECT set_config('clima.usuario_id',COALESCE((SELECT id::text FROM usuario WHERE lower(email)=lower(:email)),''),true)",Map.of("email",a.getName()),String.class);
 }
}
