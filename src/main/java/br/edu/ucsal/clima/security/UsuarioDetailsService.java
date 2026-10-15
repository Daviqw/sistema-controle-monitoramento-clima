package br.edu.ucsal.clima.security;

import br.edu.ucsal.clima.domain.entity.Usuario;
import br.edu.ucsal.clima.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {
    private final UsuarioRepository usuarios;
    public UsuarioDetailsService(UsuarioRepository usuarios) { this.usuarios = usuarios; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario u = usuarios.findByEmailIgnoreCase(username)
            .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
        var authorities = u.getPerfis().stream()
            .map(p -> new SimpleGrantedAuthority("ROLE_" + p.getNome())).toList();
        return User.withUsername(u.getEmail())
            .password(u.getSenhaHash())
            .authorities(authorities)
            .disabled(!u.isAtivo())
            .build();
    }
}
