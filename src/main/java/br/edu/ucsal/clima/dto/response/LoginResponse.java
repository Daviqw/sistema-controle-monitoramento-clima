package br.edu.ucsal.clima.dto.response;
import java.util.List;
public record LoginResponse(String token, String tipo, long expiraEmMs, String email, List<String> perfis) {}
