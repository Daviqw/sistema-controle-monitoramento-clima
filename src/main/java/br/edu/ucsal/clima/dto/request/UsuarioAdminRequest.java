package br.edu.ucsal.clima.dto.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
public record UsuarioAdminRequest(@NotBlank @Size(max=160) String nome,
    @NotBlank @Email String email, @Size(max=100) String senha,
    Set<String> perfis) {}
