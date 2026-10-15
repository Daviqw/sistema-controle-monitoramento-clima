package br.edu.ucsal.clima.exception;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflitoHorarioException extends RuntimeException {
    public ConflitoHorarioException(String mensagem) { super(mensagem); }
}
