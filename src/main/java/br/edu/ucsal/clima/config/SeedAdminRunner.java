package br.edu.ucsal.clima.config;

import br.edu.ucsal.clima.domain.entity.Perfil;
import br.edu.ucsal.clima.domain.entity.Usuario;
import br.edu.ucsal.clima.repository.PerfilRepository;
import br.edu.ucsal.clima.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Component
@Profile({"dev", "prod"})
public class SeedAdminRunner implements CommandLineRunner {
    private final UsuarioRepository usuarios;
    private final PerfilRepository perfis;
    private final PasswordEncoder encoder;
    private final String email;
    private final String senha;
    public SeedAdminRunner(UsuarioRepository usuarios, PerfilRepository perfis, PasswordEncoder encoder,
            @Value("${app.seed.admin-email}") String email,
            @Value("${app.seed.admin-password}") String senha) {
        this.usuarios = usuarios; this.perfis = perfis; this.encoder = encoder;
        this.email = email; this.senha = senha;
    }
    @Override @Transactional
    public void run(String... args) {
        if (usuarios.findByEmailIgnoreCase(email).isPresent()) return;
        if (senha == null || senha.isBlank() || senha.equals("change-me-in-dev"))
            throw new IllegalStateException("Defina ADMIN_INITIAL_PASSWORD com uma senha forte.");
        Perfil admin = perfis.findByNome("ADMINISTRADOR")
            .orElseThrow(() -> new IllegalStateException("Perfil ADMINISTRADOR não encontrado."));
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNome("Administrador Inicial");
        u.setEmail(email.toLowerCase());
        u.setSenhaHash(encoder.encode(senha));
        u.setAtivo(true);
        u.setDataCadastro(OffsetDateTime.now());
        u.setDataAtualizacao(OffsetDateTime.now());
        u.setPerfis(Set.of(admin));
        usuarios.save(u);
    }
}
