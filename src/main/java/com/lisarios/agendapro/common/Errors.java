package com.lisarios.agendapro.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import java.util.*;

@RestControllerAdvice
public class Errors {
    private static final Logger log=LoggerFactory.getLogger(Errors.class);
    private ResponseEntity<Map<String,Object>> reply(HttpStatus status,String message) {
        return ResponseEntity.status(status).body(Map.of("status",status.value(),"message",message));
    }
    @ExceptionHandler(Problem.class) ResponseEntity<?> problem(Problem e) { return reply(e.status,e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("status",400,"message","Dados invalidos.","errors",
            e.getBindingResult().getFieldErrors().stream().map(x -> x.getField()+": "+x.getDefaultMessage()).toList()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,HandlerMethodValidationException.class})
    ResponseEntity<?> malformed(Exception e) { return reply(HttpStatus.BAD_REQUEST,"Formato de dados ou parametros invalido."); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> constraint(Exception e) {
        return reply(HttpStatus.CONFLICT,"Dados duplicados, referencias invalidas ou conflito de horario.");
    }
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<?> missing(Exception e) { return reply(HttpStatus.NOT_FOUND,"Recurso nao encontrado."); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(Exception e) { return reply(HttpStatus.METHOD_NOT_ALLOWED,"Metodo HTTP nao permitido."); }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e) {
        log.error("Falha interna na requisicao",e);
        return reply(HttpStatus.INTERNAL_SERVER_ERROR,"Erro interno. Consulte os logs do servidor.");
    }
}
