package com.lisarios.agendapro.auth;

import com.lisarios.agendapro.common.Db;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecretKeySpec signingKey(@Value("${app.jwt-secret}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException("JWT_SECRET deve conter pelo menos 32 bytes.");
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKeySpec key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKeySpec key) {
        var decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience=jwt -> jwt.getAudience().contains("agendapro-api")
            ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("agendapro"),audience));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http,Db db) throws Exception {
        http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET,"/","/index.html","/app.js","/style.css","/favicon.ico","/api/health","/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(jwt->{
                try {
                    UUID id=UUID.fromString(jwt.getSubject()),tenant=UUID.fromString(jwt.getClaimAsString("tenant"));
                    var row=db.rows("SELECT role,token_version FROM users WHERE id=? AND tenant_id=? AND active=TRUE",id,tenant);
                    if(row.isEmpty()) throw new IllegalArgumentException();
                    if(!Objects.equals(((Number)row.getFirst().get("tokenVersion")).longValue(),jwt.getClaim("version"))) throw new IllegalArgumentException();
                    return new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_"+row.getFirst().get("role"))),id.toString());
                } catch(IllegalArgumentException e) { throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token")); }
            })))
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->{
                s.setStatus(401);s.setContentType("application/json");s.getWriter().write("{\"status\":401,\"message\":\"Autenticacao necessaria.\"}");
            }).accessDeniedHandler((r,s,x)->{
                s.setStatus(403);s.setContentType("application/json");s.getWriter().write("{\"status\":403,\"message\":\"Acesso negado.\"}");
            }))
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'")));
        return http.build();
    }
}
