package br.edu.ucsal.clima.dto.response;
import java.time.OffsetDateTime;
import java.util.List;
public record ErroApi(OffsetDateTime timestamp, int status, String codigo, String mensagem,
                      String caminho, List<ErroCampo> erros) {
    public record ErroCampo(String campo, String mensagem) {}
}
