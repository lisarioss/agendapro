package com.lisarios.agendapro.common;

import org.springframework.http.HttpStatus;

public class Problem extends RuntimeException {
    public final HttpStatus status;
    public Problem(HttpStatus status, String message) { super(message); this.status=status; }
    public static Problem bad(String text) { return new Problem(HttpStatus.BAD_REQUEST,text); }
    public static Problem conflict(String text) { return new Problem(HttpStatus.CONFLICT,text); }
    public static Problem forbidden() { return new Problem(HttpStatus.FORBIDDEN,"Operacao nao permitida para este usuario."); }
    public static Problem notFound() { return new Problem(HttpStatus.NOT_FOUND,"Recurso nao encontrado."); }
}
