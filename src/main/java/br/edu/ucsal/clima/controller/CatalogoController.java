package br.edu.ucsal.clima.controller;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogos somente leitura para formulários de usuários autenticados. */
@RestController
@RequestMapping("/api/v1/catalogos")
public class CatalogoController {
  private final NamedParameterJdbcTemplate db;
  public CatalogoController(NamedParameterJdbcTemplate db){this.db=db;}
  @GetMapping("/ogms") public List<Map<String,Object>> ogms(){return db.queryForList("SELECT id,sigla,nome,status FROM ogm WHERE status='ATIVO' ORDER BY nome",Map.of());}
  @GetMapping("/dmcs") public List<Map<String,Object>> dmcs(){return db.queryForList("SELECT id,nome,ogm_id,status FROM dmc WHERE status='ATIVO' ORDER BY nome",Map.of());}
  @GetMapping("/redes") public List<Map<String,Object>> redes(){return db.queryForList("SELECT id,sigla,descricao,dmc_id,status FROM rede_monitoramento WHERE status='ATIVO' ORDER BY sigla",Map.of());}
}
