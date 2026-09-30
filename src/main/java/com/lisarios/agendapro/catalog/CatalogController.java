package com.lisarios.agendapro.catalog;

import com.lisarios.agendapro.auth.Actor;
import com.lisarios.agendapro.common.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final Db db; private final Actor actor; private final Clock clock; private final ProfessionalStore professionals;
    public CatalogController(Db db,Actor actor,Clock clock,ProfessionalStore professionals) { this.db=db;this.actor=actor;this.clock=clock;this.professionals=professionals; }
    private List<Map<String,Object>> list(String table,int limit,int offset) {
        if(limit<1 || limit>500 || offset<0) throw Problem.bad("limit entre 1 e 500; offset >= 0.");
        var who=actor.current();
        return db.rows("SELECT * FROM "+table+" WHERE tenant_id=? ORDER BY name,id LIMIT ? OFFSET ?",who.tenantId(),limit,offset);
    }
    private Map<String,Object> find(String table,UUID id) { return db.one("SELECT * FROM "+table+" WHERE id=? AND tenant_id=?",id,actor.current().tenantId()); }
    @GetMapping("/clients") public List<Map<String,Object>> clients(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset) {
        actor.current().staff();return list("clients",limit,offset);
    }
    @GetMapping("/clients/{id}") public Map<String,Object> client(@PathVariable UUID id) { actor.current().staff();return find("clients",id); }
    @PostMapping("/clients") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> client(@Valid @RequestBody Api.ClientInput input) {
        var who=actor.current();who.staff();var id=UUID.randomUUID();
        db.jdbc.update("INSERT INTO clients(id,tenant_id,name,email,phone) VALUES(?,?,?,?,?)",id,who.tenantId(),input.name().strip(),input.email(),input.phone());
        return find("clients",id);
    }
    @PutMapping("/clients/{id}") public Map<String,Object> client(@PathVariable UUID id,@Valid @RequestBody Api.ClientInput input) {
        var who=actor.current();who.staff();find("clients",id);
        db.jdbc.update("UPDATE clients SET name=?,email=?,phone=? WHERE id=? AND tenant_id=?",input.name().strip(),input.email(),input.phone(),id,who.tenantId());return find("clients",id);
    }
    @DeleteMapping("/clients/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateClient(@PathVariable UUID id) {
        var who=actor.current();who.staff();find("clients",id);db.jdbc.update("UPDATE clients SET active=FALSE WHERE id=? AND tenant_id=?",id,who.tenantId());
    }
    @GetMapping("/services") public List<Map<String,Object>> services(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset) { return list("services",limit,offset); }
    @GetMapping("/services/{id}") public Map<String,Object> service(@PathVariable UUID id) { return find("services",id); }
    @PostMapping("/services") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> service(@Valid @RequestBody Api.ServiceInput input) {
        var who=actor.current();who.admin();var id=UUID.randomUUID();
        db.jdbc.update("INSERT INTO services(id,tenant_id,name,duration_minutes,price) VALUES(?,?,?,?,?)",id,who.tenantId(),input.name().strip(),input.durationMinutes(),input.price());return find("services",id);
    }
    @PutMapping("/services/{id}") public Map<String,Object> service(@PathVariable UUID id,@Valid @RequestBody Api.ServiceInput input) {
        var who=actor.current();who.admin();find("services",id);
        db.jdbc.update("UPDATE services SET name=?,duration_minutes=?,price=? WHERE id=? AND tenant_id=?",input.name().strip(),input.durationMinutes(),input.price(),id,who.tenantId());return find("services",id);
    }
    @DeleteMapping("/services/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateService(@PathVariable UUID id) {
        var who=actor.current();who.admin();find("services",id);db.jdbc.update("UPDATE services SET active=FALSE WHERE id=? AND tenant_id=?",id,who.tenantId());
    }
    @GetMapping("/professionals") public List<Map<String,Object>> professionals(@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset) { return list("professionals",limit,offset); }
    @GetMapping("/professionals/{id}") public Map<String,Object> professional(@PathVariable UUID id) { return find("professionals",id); }
    @PostMapping("/professionals") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> professional(@Valid @RequestBody Api.ProfessionalInput input) {
        var who=actor.current();who.admin();var id=UUID.randomUUID();db.jdbc.update("INSERT INTO professionals(id,tenant_id,name) VALUES(?,?,?)",id,who.tenantId(),input.name().strip());return find("professionals",id);
    }
    @PutMapping("/professionals/{id}") public Map<String,Object> professional(@PathVariable UUID id,@Valid @RequestBody Api.ProfessionalInput input) {
        var who=actor.current();who.admin();find("professionals",id);db.jdbc.update("UPDATE professionals SET name=? WHERE id=? AND tenant_id=?",input.name().strip(),id,who.tenantId());return find("professionals",id);
    }
    @DeleteMapping("/professionals/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void deactivateProfessional(@PathVariable UUID id) {
        var who=actor.current();who.admin();professionals.lock(who.tenantId(),id);
        if(db.exists("SELECT EXISTS(SELECT 1 FROM appointments WHERE tenant_id=? AND professional_id=? AND status IN ('PENDING','CONFIRMED') AND ends_at>?)",who.tenantId(),id,OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC)))
            throw Problem.conflict("Cancele ou conclua os agendamentos ativos antes de desativar o profissional.");
        db.jdbc.update("UPDATE professionals SET active=FALSE WHERE id=? AND tenant_id=?",id,who.tenantId());
    }
    @GetMapping("/professionals/{id}/services") public List<Map<String,Object>> offered(@PathVariable UUID id) {
        var who=actor.current();find("professionals",id);
        return db.rows("SELECT s.* FROM services s JOIN professional_services ps ON ps.tenant_id=s.tenant_id AND ps.service_id=s.id WHERE ps.tenant_id=? AND ps.professional_id=? ORDER BY s.name,s.id",who.tenantId(),id);
    }
    @PutMapping("/professionals/{id}/services") @Transactional public List<Map<String,Object>> offered(@PathVariable UUID id,@RequestBody @Size(max=500) List<@NotNull UUID> services) {
        var who=actor.current();who.admin();if(services==null || services.size()>500) throw Problem.bad("Informe ate 500 servicos.");professionals.lock(who.tenantId(),id);
        for(var service:new HashSet<>(services)) find("services",service);
        db.jdbc.update("DELETE FROM professional_services WHERE tenant_id=? AND professional_id=?",who.tenantId(),id);
        for(var service:new HashSet<>(services)) db.jdbc.update("INSERT INTO professional_services(tenant_id,professional_id,service_id) VALUES(?,?,?)",who.tenantId(),id,service);
        return offered(id);
    }
}
