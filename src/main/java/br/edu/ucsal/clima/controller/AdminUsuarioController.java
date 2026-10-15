package br.edu.ucsal.clima.controller;

import br.edu.ucsal.clima.domain.entity.Usuario;
import br.edu.ucsal.clima.dto.request.UsuarioAdminRequest;
import br.edu.ucsal.clima.dto.response.UsuarioResponse;
import br.edu.ucsal.clima.repository.PerfilRepository;
import br.edu.ucsal.clima.repository.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminUsuarioController {
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate db;
    private final UsuarioRepository usuarios;
    private final PerfilRepository perfis;
    private final PasswordEncoder encoder;
    public AdminUsuarioController(UsuarioRepository usuarios, PerfilRepository perfis, PasswordEncoder encoder,org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate db) {
        this.db=db;
        this.usuarios = usuarios; this.perfis = perfis; this.encoder = encoder;
    }
    @GetMapping
    public Map<String,Object> listar(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(required=false) String filtro) {
        Pageable pageable = PageRequest.of(Math.max(0,page), Math.min(Math.max(1,size),100), Sort.by("nome").ascending());
        Page<Usuario> p = filtro==null||filtro.isBlank()?usuarios.findAll(pageable):usuarios.findByNomeContainingIgnoreCaseOrEmailContainingIgnoreCase(filtro,filtro,pageable);
        List<UsuarioResponse> content = p.getContent().stream().filter(u -> filtro == null ||
            u.getNome().toLowerCase().contains(filtro.toLowerCase()) ||
            u.getEmail().toLowerCase().contains(filtro.toLowerCase())).map(this::dto).toList();
        return Map.of("content", content, "page", p.getNumber(), "size", p.getSize(), "totalElements", p.getTotalElements());
    }
    @GetMapping("/{id}")
    public UsuarioResponse buscar(@PathVariable UUID id) { return dto(obter(id)); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UsuarioResponse criar(@Valid @RequestBody UsuarioAdminRequest req) {
        contextoAuditoria();
        if (usuarios.findByEmailIgnoreCase(req.email()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
        if(req.senha()==null||req.senha().length()<10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Senha deve ter pelo menos 10 caracteres");
        Usuario u = new Usuario(); u.setId(UUID.randomUUID()); u.setNome(req.nome());
        u.setEmail(req.email().toLowerCase()); u.setSenhaHash(encoder.encode(req.senha()));
        u.setAtivo(true); u.setDataCadastro(OffsetDateTime.now()); u.setDataAtualizacao(OffsetDateTime.now());
        u.setPerfis(resolverPerfis(req.perfis()));
        return dto(usuarios.save(u));
    }
    @PutMapping("/{id}")
    @Transactional
    public UsuarioResponse atualizar(@PathVariable UUID id, @Valid @RequestBody UsuarioAdminRequest req) {
        contextoAuditoria(); Usuario u = obter(id);
        if(req.senha()!=null&&!req.senha().isBlank()&&req.senha().length()<10)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Senha deve ter pelo menos 10 caracteres");
        u.setDataAtualizacao(OffsetDateTime.now()); u.setNome(req.nome()); u.setEmail(req.email().toLowerCase());
        if (req.senha() != null && !req.senha().isBlank()) u.setSenhaHash(encoder.encode(req.senha()));
        u.setPerfis(resolverPerfis(req.perfis()));
        return dto(usuarios.save(u));
    }
    @PatchMapping("/{id}/inativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void inativar(@PathVariable UUID id) { contextoAuditoria(); Usuario u = obter(id); u.setAtivo(false); usuarios.save(u); }
    private void contextoAuditoria() {
        var a=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(a!=null)db.queryForObject("SELECT set_config('clima.usuario_id',COALESCE((SELECT id::text FROM usuario WHERE lower(email)=lower(:email)),''),true)",Map.of("email",a.getName()),String.class);
    }
    private Usuario obter(UUID id) {
        return usuarios.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }
    private Set<br.edu.ucsal.clima.domain.entity.Perfil> resolverPerfis(Set<String> nomes) {
        if (nomes == null || nomes.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos um perfil.");
        Set<br.edu.ucsal.clima.domain.entity.Perfil> resultado = new HashSet<>();
        for (String nome : nomes) resultado.add(perfis.findByNome(nome.toUpperCase())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Perfil inválido: " + nome)));
        return resultado;
    }
    private UsuarioResponse dto(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.isAtivo(),
            u.getPerfis().stream().map(p -> p.getNome()).sorted().toList());
    }
}
