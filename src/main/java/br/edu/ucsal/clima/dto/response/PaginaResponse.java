package br.edu.ucsal.clima.dto.response;
import org.springframework.data.domain.Page;
import java.util.List;
public record PaginaResponse<T>(List<T> content, int page, int size, long totalElements) {
    public static <T> PaginaResponse<T> de(Page<T> pagina) {
        return new PaginaResponse<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(), pagina.getTotalElements());
    }
}
