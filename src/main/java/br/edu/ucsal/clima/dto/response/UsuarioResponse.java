package br.edu.ucsal.clima.dto.response;
import java.util.List;
import java.util.UUID;
public record UsuarioResponse(UUID id, String nome, String email, boolean ativo, List<String> perfis) {}
