package com.lisarios.agendapro.auth;

import com.lisarios.agendapro.common.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UsersController {
    private final Db db;private final Actor actor;private final PasswordEncoder passwords;
    public UsersController(Db db,Actor actor,PasswordEncoder passwords) { this.db=db;this.actor=actor;this.passwords=passwords; }
    @GetMapping public List<Map<String,Object>> users(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset) {
        var who=actor.current();who.admin();if(limit<1||limit>500||offset<0) throw Problem.bad("Paginacao invalida.");
        return db.rows("SELECT id,name,email,role,professional_id,active FROM users WHERE tenant_id=? ORDER BY name,id LIMIT ? OFFSET ?",who.tenantId(),limit,offset);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional public Map<String,Object> create(@Valid @RequestBody Api.UserInput input) {
        var who=actor.current();who.admin();
        if(input.password().getBytes(StandardCharsets.UTF_8).length>72) throw Problem.bad("Senha excede 72 bytes UTF-8.");
        if((input.role()==Api.Role.PROFESSIONAL)!=(input.professionalId()!=null)) throw Problem.bad("Somente usuarios PROFESSIONAL devem ter professionalId.");
        if(input.professionalId()!=null) db.one("SELECT id FROM professionals WHERE id=? AND tenant_id=? AND active=TRUE",input.professionalId(),who.tenantId());
        var id=UUID.randomUUID();db.jdbc.update("INSERT INTO users(id,tenant_id,name,email,password_hash,role,professional_id) VALUES(?,?,?,?,?,?,?)",id,who.tenantId(),input.name().strip(),input.email().strip().toLowerCase(Locale.ROOT),passwords.encode(input.password()),input.role().name(),input.professionalId());
        return db.one("SELECT id,name,email,role,professional_id,active FROM users WHERE id=? AND tenant_id=?",id,who.tenantId());
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional public void deactivate(@PathVariable UUID id) {
        var who=actor.current();who.admin();if(id.equals(who.id())) throw Problem.bad("Nao e possivel desativar o proprio usuario.");
        db.one("SELECT id FROM tenants WHERE id=? FOR UPDATE",who.tenantId());
        db.one("SELECT id FROM users WHERE id=? AND tenant_id=?",id,who.tenantId());
        db.jdbc.update("UPDATE users SET active=FALSE WHERE id=? AND tenant_id=?",id,who.tenantId());
    }
}
