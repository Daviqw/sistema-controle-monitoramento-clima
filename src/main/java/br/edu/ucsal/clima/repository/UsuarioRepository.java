package br.edu.ucsal.clima.repository;
import br.edu.ucsal.clima.domain.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    org.springframework.data.domain.Page<Usuario> findByNomeContainingIgnoreCaseOrEmailContainingIgnoreCase(String nome,String email,org.springframework.data.domain.Pageable pageable);
    Optional<Usuario> findByEmailIgnoreCase(String email);
}
