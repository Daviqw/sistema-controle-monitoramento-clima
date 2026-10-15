package br.edu.ucsal.clima.exception;

import br.edu.ucsal.clima.dto.response.ErroApi;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroApi> validacao(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErroApi.ErroCampo> campos = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ErroApi.ErroCampo(e.getField(), e.getDefaultMessage())).toList();
        return resposta(HttpStatus.BAD_REQUEST, "VALIDACAO", "Dados inválidos.", req, campos);
    }
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ResponseEntity<ErroApi> naoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest req) {
        return resposta(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", ex.getMessage(), req, List.of());
    }
    @ExceptionHandler(ConflitoHorarioException.class)
    ResponseEntity<ErroApi> conflito(ConflitoHorarioException ex, HttpServletRequest req) {
        return resposta(HttpStatus.CONFLICT, "CONFLITO", ex.getMessage(), req, List.of());
    }
    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<ErroApi> regra(RegraNegocioException ex, HttpServletRequest req) {
        return resposta(HttpStatus.UNPROCESSABLE_ENTITY, "REGRA_NEGOCIO", ex.getMessage(), req, List.of());
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErroApi> status(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String msg = status == HttpStatus.UNAUTHORIZED ? "Credenciais inválidas ou autenticação necessária." : (ex.getReason()!=null ? ex.getReason() : status.getReasonPhrase());
        return resposta(status, "HTTP_" + status.value(), msg, req, List.of());
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ErroApi> proibido(Exception ex,HttpServletRequest req) {
        return resposta(HttpStatus.FORBIDDEN,"ACESSO_NEGADO","Você não tem permissão para esta operação.",req,List.of());
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ErroApi> integridade(Exception ex,HttpServletRequest req) {
        return resposta(HttpStatus.CONFLICT,"INTEGRIDADE","Registro duplicado, vínculo inexistente ou dados fora dos limites permitidos.",req,List.of());
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErroApi> formato(Exception ex,HttpServletRequest req) {
        return resposta(HttpStatus.BAD_REQUEST,"FORMATO","Verifique o formato dos dados enviados.",req,List.of());
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroApi> geral(Exception ex, HttpServletRequest req) {
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Ocorreu um erro interno.", req, List.of());
    }
    private ResponseEntity<ErroApi> resposta(HttpStatus status, String codigo, String msg,
            HttpServletRequest req, List<ErroApi.ErroCampo> erros) {
        return ResponseEntity.status(status).body(new ErroApi(OffsetDateTime.now(), status.value(),
            codigo, msg, req.getRequestURI(), erros));
    }
}
