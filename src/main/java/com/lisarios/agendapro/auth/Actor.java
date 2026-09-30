package com.lisarios.agendapro.auth;

import com.lisarios.agendapro.common.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class Actor {
    private final Db db;
    public Actor(Db db) { this.db=db; }
    public record Identity(UUID id, UUID tenantId, Api.Role role, UUID professionalId, String timezone) {
        public void staff() { if (role==Api.Role.PROFESSIONAL) throw Problem.forbidden(); }
        public void admin() { if (role!=Api.Role.ADMIN) throw Problem.forbidden(); }
        public void professional(UUID id) {
            if (role==Api.Role.PROFESSIONAL && !id.equals(professionalId)) throw Problem.forbidden();
        }
    }
    public Identity current() {
        var jwt=(Jwt)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        var row=db.one("SELECT u.id,u.tenant_id,u.role,u.professional_id,t.timezone FROM users u JOIN tenants t ON t.id=u.tenant_id WHERE u.id=? AND u.tenant_id=? AND u.active=TRUE",
            UUID.fromString(jwt.getSubject()),UUID.fromString(jwt.getClaimAsString("tenant")));
        return new Identity(Db.uuid(row.get("id")),Db.uuid(row.get("tenantId")),Api.Role.valueOf(row.get("role").toString()),
            row.get("professionalId")==null?null:Db.uuid(row.get("professionalId")),row.get("timezone").toString());
    }
}
