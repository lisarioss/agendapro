package com.lisarios.agendapro.auth;

import com.lisarios.agendapro.common.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final Db db; private final PasswordEncoder passwords; private final JwtEncoder encoder;
    private final Actor actor; private final Clock clock; private final long minutes; private final String dummy;
    public AuthController(Db db,PasswordEncoder passwords,JwtEncoder encoder,Actor actor,Clock clock,@Value("${app.token-minutes}") long minutes) {
        this.db=db;this.passwords=passwords;this.encoder=encoder;this.actor=actor;this.clock=clock;this.minutes=minutes;
        this.dummy=passwords.encode(UUID.randomUUID().toString());
    }
    private String email(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private void passwordLength(String password) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72) throw Problem.bad("Senha excede o limite de 72 bytes UTF-8.");
    }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String,Object> register(@Valid @RequestBody Api.Register input) {
        passwordLength(input.password());
        try { ZoneId.of(input.timezone()); } catch(DateTimeException e) { throw Problem.bad("Fuso horario IANA invalido."); }
        var tenant=UUID.randomUUID();var user=UUID.randomUUID();
        db.jdbc.update("INSERT INTO tenants(id,name,slug,timezone) VALUES(?,?,?,?)",tenant,input.companyName().strip(),input.slug(),input.timezone());
        db.jdbc.update("INSERT INTO users(id,tenant_id,name,email,password_hash,role) VALUES(?,?,?,?,?,?)",
            user,tenant,input.name().strip(),email(input.email()),passwords.encode(input.password()),"ADMIN");
        return token(user,tenant);
    }
    @PostMapping("/login")
    public Map<String,Object> login(@Valid @RequestBody Api.Login input) {
        var rows=db.rows("SELECT u.id,u.tenant_id,u.password_hash FROM users u JOIN tenants t ON t.id=u.tenant_id WHERE t.slug=? AND u.email=? AND u.active=TRUE",input.slug(),email(input.email()));
        String hash=rows.isEmpty()?dummy:rows.getFirst().get("passwordHash").toString();
        if(!passwords.matches(input.password(),hash) || rows.isEmpty()) throw new Problem(HttpStatus.UNAUTHORIZED,"Credenciais invalidas.");
        return token(Db.uuid(rows.getFirst().get("id")),Db.uuid(rows.getFirst().get("tenantId")));
    }
    private Map<String,Object> token(UUID user,UUID tenant) {
        Instant now=clock.instant();
        long version=((Number)db.one("SELECT token_version FROM users WHERE id=? AND tenant_id=?",user,tenant).get("tokenVersion")).longValue();
        var claims=JwtClaimsSet.builder().issuer("agendapro").audience(List.of("agendapro-api")).subject(user.toString())
            .issuedAt(now).expiresAt(now.plusSeconds(minutes*60)).claim("tenant",tenant.toString()).claim("version",version).build();
        String value=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        return Map.of("accessToken",value,"tokenType","Bearer","expiresIn",minutes*60,"tenantId",tenant,"userId",user);
    }
    @GetMapping("/me") public Map<String,Object> me() {
        var who=actor.current();
        return db.one("SELECT u.id,u.name,u.email,u.role,u.professional_id,u.tenant_id,t.name AS company_name,t.slug,t.timezone FROM users u JOIN tenants t ON t.id=u.tenant_id WHERE u.id=? AND u.tenant_id=?",who.id(),who.tenantId());
    }
    @PutMapping("/password") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void password(@Valid @RequestBody Api.PasswordInput input) {
        passwordLength(input.newPassword());var who=actor.current();
        var row=db.one("SELECT password_hash FROM users WHERE id=? AND tenant_id=?",who.id(),who.tenantId());
        if(!passwords.matches(input.currentPassword(),row.get("passwordHash").toString())) throw Problem.bad("Senha atual incorreta.");
        db.jdbc.update("UPDATE users SET password_hash=?,token_version=token_version+1 WHERE id=? AND tenant_id=?",passwords.encode(input.newPassword()),who.id(),who.tenantId());
    }
}
