package br.edu.ucsal.clima.controller;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/auditoria")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AuditoriaController {
    private final NamedParameterJdbcTemplate db;
    public AuditoriaController(NamedParameterJdbcTemplate db) { this.db=db; }
    @GetMapping
    public List<Map<String,Object>> listar(@RequestParam(defaultValue="0") int page) {
        return db.queryForList("SELECT a.*,u.nome AS responsavel FROM audit_log a LEFT JOIN usuario u ON u.id=a.usuario_id ORDER BY a.data_hora DESC,a.id LIMIT 100 OFFSET :offset",Map.of("offset",Math.max(0,page)*100L));
    }
}
