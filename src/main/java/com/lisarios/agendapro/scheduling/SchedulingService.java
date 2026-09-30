package com.lisarios.agendapro.scheduling;

import com.lisarios.agendapro.auth.Actor;
import com.lisarios.agendapro.catalog.ProfessionalStore;
import com.lisarios.agendapro.common.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class SchedulingService {
    private final Db db;private final Actor actor;private final ProfessionalStore professionals;private final Clock clock;
    public SchedulingService(Db db,Actor actor,ProfessionalStore professionals,Clock clock) { this.db=db;this.actor=actor;this.professionals=professionals;this.clock=clock; }
    public static OffsetDateTime utc(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    private void active(Map<String,Object> row) { if(!Boolean.TRUE.equals(row.get("active"))) throw Problem.bad("Cadastro inativo."); }
    private Map<String,Object> service(Actor.Identity who,UUID professional,UUID service) {
        var row=db.one("SELECT s.* FROM services s JOIN professional_services ps ON ps.tenant_id=s.tenant_id AND ps.service_id=s.id WHERE s.id=? AND s.tenant_id=? AND ps.professional_id=?",service,who.tenantId(),professional);active(row);return row;
    }
    public List<Map<String,Object>> hours(UUID professional) {
        var who=actor.current();who.professional(professional);db.one("SELECT id FROM professionals WHERE tenant_id=? AND id=?",who.tenantId(),professional);
        return db.rows("SELECT id,day_of_week,start_time,end_time FROM working_hours WHERE tenant_id=? AND professional_id=? ORDER BY day_of_week,start_time",who.tenantId(),professional);
    }
    @Transactional public List<Map<String,Object>> hours(UUID professional,List<Api.HoursInput> input) {
        var who=actor.current();who.admin();professionals.lock(who.tenantId(),professional);
        if(input.size()>28) throw Problem.bad("Informe ate 28 periodos semanais.");
        for(int i=0;i<input.size();i++) {
            var a=input.get(i);
            if(!a.startTime().isBefore(a.endTime()) || a.startTime().getSecond()!=0 || a.endTime().getSecond()!=0 || a.startTime().getNano()!=0 || a.endTime().getNano()!=0) throw Problem.bad("Periodos devem ter inicio anterior ao fim e precisao de minutos.");
            for(int j=0;j<i;j++) { var b=input.get(j);if(a.dayOfWeek()==b.dayOfWeek() && a.startTime().isBefore(b.endTime()) && a.endTime().isAfter(b.startTime())) throw Problem.bad("Periodos de trabalho se sobrepoem."); }
        }
        db.jdbc.update("DELETE FROM working_hours WHERE tenant_id=? AND professional_id=?",who.tenantId(),professional);
        for(var h:input) db.jdbc.update("INSERT INTO working_hours(id,tenant_id,professional_id,day_of_week,start_time,end_time) VALUES(?,?,?,?,?,?)",UUID.randomUUID(),who.tenantId(),professional,h.dayOfWeek(),h.startTime(),h.endTime());
        for(var row:db.rows("SELECT starts_at,ends_at FROM appointments WHERE tenant_id=? AND professional_id=? AND status IN ('PENDING','CONFIRMED') AND ends_at>?",who.tenantId(),professional,utc(clock.instant()))) {
            if(!fitsHours(who,professional,Db.instant(row.get("startsAt")),Db.instant(row.get("endsAt")))) throw Problem.conflict("A nova jornada invalidaria um agendamento ativo.");
        }
        return hours(professional);
    }
    private boolean fitsHours(Actor.Identity who,UUID professional,Instant start,Instant end) {
        var zone=ZoneId.of(who.timezone());var a=start.atZone(zone);var b=end.atZone(zone);
        if(!a.toLocalDate().equals(b.toLocalDate())) return false;
        return db.exists("SELECT EXISTS(SELECT 1 FROM working_hours WHERE tenant_id=? AND professional_id=? AND day_of_week=? AND start_time<=? AND end_time>=?)",who.tenantId(),professional,a.getDayOfWeek().getValue(),a.toLocalTime(),b.toLocalTime());
    }
    private boolean conflicts(Actor.Identity who,UUID professional,Instant start,Instant end,UUID exclude) {
        boolean booking=db.exists("SELECT EXISTS(SELECT 1 FROM appointments WHERE tenant_id=? AND professional_id=? AND status IN ('PENDING','CONFIRMED') AND starts_at<? AND ends_at>? AND id<>?)",who.tenantId(),professional,utc(end),utc(start),exclude==null?new UUID(0,0):exclude);
        return booking || db.exists("SELECT EXISTS(SELECT 1 FROM time_off WHERE tenant_id=? AND professional_id=? AND starts_at<? AND ends_at>?)",who.tenantId(),professional,utc(end),utc(start));
    }
    private void validateSlot(Actor.Identity who,UUID professional,Instant start,Instant end,UUID exclude) {
        if(!start.isAfter(clock.instant())) throw Problem.bad("O agendamento deve iniciar no futuro.");
        if(start.atOffset(ZoneOffset.UTC).getSecond()!=0 || start.getNano()!=0) throw Problem.bad("Use horarios com precisao de minutos.");
        if(start.isAfter(clock.instant().plus(Duration.ofDays(366)))) throw Problem.bad("Agende ate 366 dias a partir de hoje.");
        if(!fitsHours(who,professional,start,end)) throw Problem.conflict("Horario fora da jornada de trabalho.");
        if(conflicts(who,professional,start,end,exclude)) throw Problem.conflict("Horario indisponivel: agendamento ou folga existente.");
    }
    public List<Map<String,Object>> availability(UUID professional,UUID service,LocalDate date,int step) {
        var who=actor.current();who.professional(professional);
        if(step<5 || step>60) throw Problem.bad("stepMinutes deve estar entre 5 e 60.");
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of(who.timezone())));
        if(date.isBefore(today)||date.isAfter(today.plusDays(366))) throw Problem.bad("Data deve estar entre hoje e os proximos 366 dias.");
        active(db.one("SELECT * FROM professionals WHERE id=? AND tenant_id=?",professional,who.tenantId()));
        var svc=service(who,professional,service);int duration=((Number)svc.get("durationMinutes")).intValue();
        var windows=hours(professional);var zone=ZoneId.of(who.timezone());var slots=new ArrayList<Map<String,Object>>();
        for(var window:windows) {
            if(((Number)window.get("dayOfWeek")).intValue()!=date.getDayOfWeek().getValue()) continue;
            LocalTime from=LocalTime.parse(window.get("startTime").toString()),to=LocalTime.parse(window.get("endTime").toString());
            for(var local=date.atTime(from);local.isBefore(date.atTime(to));local=local.plusMinutes(step)) {
                var offsets=zone.getRules().getValidOffsets(local);if(offsets.size()!=1) continue;
                Instant start=local.atOffset(offsets.getFirst()).toInstant(),end=start.plusSeconds(duration*60L);
                if(start.isAfter(clock.instant()) && fitsHours(who,professional,start,end) && !conflicts(who,professional,start,end,null))
                    slots.add(Map.of("startsAt",start.atZone(zone).toOffsetDateTime(),"endsAt",end.atZone(zone).toOffsetDateTime()));
            }
        }
        return slots;
    }
    public List<Map<String,Object>> timeOff(UUID professional) {
        var who=actor.current();who.professional(professional);db.one("SELECT id FROM professionals WHERE tenant_id=? AND id=?",who.tenantId(),professional);
        return db.rows("SELECT * FROM time_off WHERE tenant_id=? AND professional_id=? ORDER BY starts_at DESC LIMIT 500",who.tenantId(),professional);
    }
    @Transactional public Map<String,Object> timeOff(UUID professional,Api.TimeOffInput input) {
        var who=actor.current();who.admin();professionals.lock(who.tenantId(),professional);
        if(!input.endsAt().isAfter(input.startsAt()) || Duration.between(input.startsAt(),input.endsAt()).toDays()>366) throw Problem.bad("Periodo de folga invalido (maximo 366 dias).");
        if(db.exists("SELECT EXISTS(SELECT 1 FROM appointments WHERE tenant_id=? AND professional_id=? AND status IN ('PENDING','CONFIRMED') AND starts_at<? AND ends_at>?)",who.tenantId(),professional,input.endsAt(),input.startsAt())) throw Problem.conflict("A folga conflita com agendamento ativo. Cancele ou reagende primeiro.");
        var id=UUID.randomUUID();db.jdbc.update("INSERT INTO time_off(id,tenant_id,professional_id,starts_at,ends_at,reason) VALUES(?,?,?,?,?,?)",id,who.tenantId(),professional,input.startsAt(),input.endsAt(),input.reason());
        return db.one("SELECT * FROM time_off WHERE tenant_id=? AND id=?",who.tenantId(),id);
    }
    @Transactional public void deleteTimeOff(UUID professional,UUID id) {
        var who=actor.current();who.admin();professionals.lock(who.tenantId(),professional);
        if(db.jdbc.update("DELETE FROM time_off WHERE id=? AND tenant_id=? AND professional_id=?",id,who.tenantId(),professional)==0) throw Problem.notFound();
    }
    public Map<String,Object> appointment(UUID id) {
        var who=actor.current();var row=db.one("SELECT a.*,c.name AS client_name,p.name AS professional_name,s.name AS service_name FROM appointments a JOIN clients c ON c.tenant_id=a.tenant_id AND c.id=a.client_id JOIN professionals p ON p.tenant_id=a.tenant_id AND p.id=a.professional_id JOIN services s ON s.tenant_id=a.tenant_id AND s.id=a.service_id WHERE a.tenant_id=? AND a.id=?",who.tenantId(),id);
        who.professional(Db.uuid(row.get("professionalId")));return row;
    }
    @Transactional public Map<String,Object> create(Api.AppointmentInput input) {
        var who=actor.current();who.staff();active(professionals.lock(who.tenantId(),input.professionalId()));
        active(db.one("SELECT * FROM clients WHERE id=? AND tenant_id=?",input.clientId(),who.tenantId()));
        var svc=service(who,input.professionalId(),input.serviceId());
        Instant start=input.startsAt().toInstant(),end=start.plusSeconds(((Number)svc.get("durationMinutes")).longValue()*60);
        validateSlot(who,input.professionalId(),start,end,null);var id=UUID.randomUUID();
        db.jdbc.update("INSERT INTO appointments(id,tenant_id,professional_id,client_id,service_id,starts_at,ends_at,status,price,notes) VALUES(?,?,?,?,?,?,?,?,?,?)",id,who.tenantId(),input.professionalId(),input.clientId(),input.serviceId(),utc(start),utc(end),"PENDING",svc.get("price"),input.notes()==null?"":input.notes());
        event(who,id,"CREATED");return appointment(id);
    }
    private Map<String,Object> locked(UUID id,Actor.Identity who) {
        var row=appointment(id);professionals.lock(who.tenantId(),Db.uuid(row.get("professionalId")));
        return db.one("SELECT * FROM appointments WHERE tenant_id=? AND id=? FOR UPDATE",who.tenantId(),id);
    }
    @Transactional public Map<String,Object> reschedule(UUID id,Api.RescheduleInput input) {
        var who=actor.current();who.staff();var row=locked(id,who);
        var status=Api.Status.valueOf(row.get("status").toString());
        if(status!=Api.Status.PENDING && status!=Api.Status.CONFIRMED) throw Problem.conflict("Agendamento encerrado nao pode ser reagendado.");
        var professional=Db.uuid(row.get("professionalId"));
        active(db.one("SELECT * FROM professionals WHERE id=? AND tenant_id=?",professional,who.tenantId()));
        active(db.one("SELECT * FROM clients WHERE id=? AND tenant_id=?",Db.uuid(row.get("clientId")),who.tenantId()));
        service(who,professional,Db.uuid(row.get("serviceId")));
        Instant start=input.startsAt().toInstant(),end=start.plus(Duration.between(Db.instant(row.get("startsAt")),Db.instant(row.get("endsAt"))));
        validateSlot(who,professional,start,end,id);
        db.jdbc.update("UPDATE appointments SET starts_at=?,ends_at=? WHERE id=? AND tenant_id=?",utc(start),utc(end),id,who.tenantId());event(who,id,"RESCHEDULED");return appointment(id);
    }
    @Transactional public Map<String,Object> status(UUID id,Api.StatusInput input) {
        var who=actor.current();var row=locked(id,who);var from=Api.Status.valueOf(row.get("status").toString());var to=input.status();
        if(who.role()==Api.Role.PROFESSIONAL && (to!=Api.Status.COMPLETED && to!=Api.Status.NO_SHOW)) throw Problem.forbidden();
        boolean allowed=from==Api.Status.PENDING && (to==Api.Status.CONFIRMED || to==Api.Status.CANCELED)
            || from==Api.Status.CONFIRMED && (to==Api.Status.COMPLETED || to==Api.Status.CANCELED || to==Api.Status.NO_SHOW);
        if(!allowed) throw Problem.conflict("Transicao de estado invalida: "+from+" -> "+to);
        Instant start=Db.instant(row.get("startsAt")),end=Db.instant(row.get("endsAt")),now=clock.instant();
        if(to==Api.Status.CONFIRMED && !start.isAfter(now)) throw Problem.conflict("Nao e possivel confirmar um horario passado.");
        if(to==Api.Status.COMPLETED && now.isBefore(end)) throw Problem.conflict("Conclua somente apos o termino do atendimento.");
        if(to==Api.Status.NO_SHOW && now.isBefore(start)) throw Problem.conflict("Registre falta somente apos o inicio do atendimento.");
        db.jdbc.update("UPDATE appointments SET status=? WHERE id=? AND tenant_id=?",to.name(),id,who.tenantId());event(who,id,from+"_TO_"+to);return appointment(id);
    }
    public List<Map<String,Object>> appointments(OffsetDateTime from,OffsetDateTime to,UUID professional,Api.Status status,int limit,int offset) {
        var who=actor.current();if(!to.isAfter(from)||Duration.between(from,to).toDays()>93) throw Problem.bad("Periodo deve ter entre 1 instante e 93 dias.");
        if(limit<1||limit>500||offset<0) throw Problem.bad("Paginacao invalida.");
        if(who.role()==Api.Role.PROFESSIONAL) { if(professional!=null) who.professional(professional);professional=who.professionalId(); }
        var args=new ArrayList<Object>(List.of(who.tenantId(),to,from));
        String sql="SELECT a.*,c.name AS client_name,p.name AS professional_name,s.name AS service_name FROM appointments a JOIN clients c ON c.tenant_id=a.tenant_id AND c.id=a.client_id JOIN professionals p ON p.tenant_id=a.tenant_id AND p.id=a.professional_id JOIN services s ON s.tenant_id=a.tenant_id AND s.id=a.service_id WHERE a.tenant_id=? AND a.starts_at<? AND a.ends_at>?";
        if(professional!=null) {sql+=" AND a.professional_id=?";args.add(professional);}
        if(status!=null) {sql+=" AND a.status=?";args.add(status.name());}
        args.add(limit);args.add(offset);return db.rows(sql+" ORDER BY a.starts_at,a.id LIMIT ? OFFSET ?",args.toArray());
    }
    private void event(Actor.Identity who,UUID id,String action) {
        db.jdbc.update("INSERT INTO appointment_events(id,tenant_id,appointment_id,actor_id,action) VALUES(?,?,?,?,?)",UUID.randomUUID(),who.tenantId(),id,who.id(),action);
    }
    public List<Map<String,Object>> events(UUID id) {
        var who=actor.current();appointment(id);
        return db.rows("SELECT id,action,actor_id,occurred_at FROM appointment_events WHERE tenant_id=? AND appointment_id=? ORDER BY occurred_at,id",who.tenantId(),id);
    }
}
