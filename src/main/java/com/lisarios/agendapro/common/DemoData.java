package com.lisarios.agendapro.common;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Component @Profile("demo")
public class DemoData implements ApplicationRunner {
    private final Db db;private final PasswordEncoder passwords;private final Clock clock;
    public DemoData(Db db,PasswordEncoder passwords,Clock clock) { this.db=db;this.passwords=passwords;this.clock=clock; }
    @Override @Transactional public void run(ApplicationArguments args) {
        UUID tenant=UUID.randomUUID(),admin=UUID.randomUUID(),ana=UUID.randomUUID(),bia=UUID.randomUUID(),corte=UUID.randomUUID(),tratamento=UUID.randomUUID();
        db.jdbc.update("INSERT INTO tenants(id,name,slug,timezone) VALUES(?,?,?,?)",tenant,"Studio Aurora","studio-demo","America/Sao_Paulo");
        db.jdbc.update("INSERT INTO users(id,tenant_id,name,email,password_hash,role) VALUES(?,?,?,?,?,?)",admin,tenant,"Lisa Rios","lisa@example.com",passwords.encode("Demo-AgendaPro-2026!"),"ADMIN");
        db.jdbc.update("INSERT INTO professionals(id,tenant_id,name) VALUES(?,?,?)",ana,tenant,"Ana Oliveira");
        db.jdbc.update("INSERT INTO professionals(id,tenant_id,name) VALUES(?,?,?)",bia,tenant,"Beatriz Santos");
        db.jdbc.update("INSERT INTO services(id,tenant_id,name,duration_minutes,price) VALUES(?,?,?,?,?)",corte,tenant,"Corte e finalizacao",45,85);
        db.jdbc.update("INSERT INTO services(id,tenant_id,name,duration_minutes,price) VALUES(?,?,?,?,?)",tratamento,tenant,"Tratamento capilar",60,140);
        for(var professional:List.of(ana,bia)) {
            for(var service:List.of(corte,tratamento)) db.jdbc.update("INSERT INTO professional_services(tenant_id,professional_id,service_id) VALUES(?,?,?)",tenant,professional,service);
            for(int day=1;day<=7;day++) for(var window:List.of(List.of("09:00","12:00"),List.of("13:00","18:00")))
                db.jdbc.update("INSERT INTO working_hours(id,tenant_id,professional_id,day_of_week,start_time,end_time) VALUES(?,?,?,?,?,?)",UUID.randomUUID(),tenant,professional,day,LocalTime.parse(window.get(0)),LocalTime.parse(window.get(1)));
        }
        int index=0;var zone=ZoneId.of("America/Sao_Paulo");var date=LocalDate.now(clock.withZone(zone)).plusDays(1);
        for(var name:List.of("Mariana Costa","Juliana Almeida","Camila Ribeiro")) {
            var client=UUID.randomUUID();db.jdbc.update("INSERT INTO clients(id,tenant_id,name,email,phone) VALUES(?,?,?,?,?)",client,tenant,name,"cliente"+(index+1)+"@example.com","(11) 99999-000"+index);
            var id=UUID.randomUUID();var start=date.atTime(index==2?13:9+index,0).atZone(zone).toOffsetDateTime();
            db.jdbc.update("INSERT INTO appointments(id,tenant_id,professional_id,client_id,service_id,starts_at,ends_at,status,price,notes) VALUES(?,?,?,?,?,?,?,?,?,?)",id,tenant,index==2?bia:ana,client,corte,start,start.plusMinutes(45),index==1?"PENDING":"CONFIRMED",85,"Atendimento de demonstracao");
            db.jdbc.update("INSERT INTO appointment_events(id,tenant_id,appointment_id,actor_id,action) VALUES(?,?,?,?,?)",UUID.randomUUID(),tenant,id,admin,"CREATED");
            if(index!=1) db.jdbc.update("INSERT INTO appointment_events(id,tenant_id,appointment_id,actor_id,action) VALUES(?,?,?,?,?)",UUID.randomUUID(),tenant,id,admin,"PENDING_TO_CONFIRMED");
            index++;
        }
    }
}
