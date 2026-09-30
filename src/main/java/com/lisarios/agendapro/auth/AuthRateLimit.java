package com.lisarios.agendapro.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Limite local por IP. Em varias replicas, aplicar limite compartilhado no gateway. */
@Component
public class AuthRateLimit extends OncePerRequestFilter {
    private final ConcurrentHashMap<String,Window> attempts=new ConcurrentHashMap<>();
    private final boolean enabled;
    private static final long WINDOW=15*60*1000L;
    private static class Window { final long start=System.currentTimeMillis(); int count; }
    public AuthRateLimit(@Value("${app.auth-rate-limit.enabled:true}") boolean enabled) { this.enabled=enabled; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getMethod().equals("POST") || !(request.getRequestURI().equals("/api/auth/login") || request.getRequestURI().equals("/api/auth/register"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        long now=System.currentTimeMillis();attempts.entrySet().removeIf(e->now-e.getValue().start>WINDOW);
        String ip=request.getRemoteAddr();
        if(attempts.size()>10000 && !attempts.containsKey(ip)) { reject(response);return; }
        var window=attempts.computeIfAbsent(ip,key->new Window());
        synchronized(window) { if(++window.count>30) {reject(response);return;} }
        chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);response.setContentType("application/json");response.setHeader("Retry-After","900");
        response.getWriter().write("{\"status\":429,\"message\":\"Muitas tentativas. Aguarde 15 minutos.\"}");
    }
}
