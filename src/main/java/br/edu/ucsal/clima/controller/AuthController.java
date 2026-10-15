package br.edu.ucsal.clima.controller;

import br.edu.ucsal.clima.domain.entity.Usuario;
import br.edu.ucsal.clima.dto.request.LoginRequest;
import br.edu.ucsal.clima.dto.response.LoginResponse;
import br.edu.ucsal.clima.repository.UsuarioRepository;
import br.edu.ucsal.clima.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final UsuarioRepository usuarios;
    private final long expiration;
    public AuthController(AuthenticationManager authManager, JwtService jwt, UsuarioRepository usuarios,
            @Value("${app.security.jwt.expiration-ms}") long expiration) {
        this.authManager = authManager; this.jwt = jwt; this.usuarios = usuarios; this.expiration = expiration;
    }
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.senha()));
            UserDetails principal = (UserDetails) authentication.getPrincipal();
            Usuario usuario = usuarios.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));
            return new LoginResponse(jwt.gerar(principal), "Bearer", expiration,
                usuario.getEmail(), usuario.getPerfis().stream().map(p -> p.getNome()).sorted().toList());
        } catch (AuthenticationException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        }
    }
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        Usuario u = usuarios.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Map.of("id", u.getId(), "nome", u.getNome(), "email", u.getEmail(),
            "ativo", u.isAtivo(), "perfis", u.getPerfis().stream().map(p -> p.getNome()).sorted().toList());
    }
}
