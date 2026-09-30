package com.lisarios.agendapro;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="app.auth-rate-limit.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(AgendaProIntegrationTest.TimeConfig.class)
public class AgendaProIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired MutableClock clock;
    String token,slug,professional,service,client;
    static final String PASSWORD="AgendaPro-Test-2026!";
    @TestConfiguration static class TimeConfig { @Bean @Primary MutableClock testClock() { return new MutableClock(); } }
    static class MutableClock extends Clock {
        volatile Instant value=Instant.parse("2030-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(value,zone); }
        public Instant instant() { return value; }
    }
    JsonNode request(String method,String path,Object body,String bearer,int expected) throws Exception {
        var result=perform(method,path,body,bearer);
        assertThat(result.getResponse().getStatus()).withFailMessage("%s %s: %s",method,path,result.getResponse().getContentAsString()).isEqualTo(expected);
        return result.getResponse().getContentAsString().isBlank()?json.nullNode():json.readTree(result.getResponse().getContentAsString());
    }
    MvcResult perform(String method,String path,Object body,String bearer) throws Exception {
        var req=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),path).contentType("application/json");
        if(bearer!=null) req.header("Authorization","Bearer "+bearer);
        if(body!=null) req.content(json.writeValueAsBytes(body));
        return mvc.perform(req).andReturn();
    }
    String register(String slug) throws Exception {
        return request("POST","/api/auth/register",Map.of("companyName","Empresa "+slug,"slug",slug,"timezone","UTC","name","Lisa","email","lisa@example.com","password",PASSWORD),null,201).get("accessToken").asText();
    }
    @BeforeEach void prepare() throws Exception {
        clock.value=Instant.parse("2030-01-01T00:00:00Z");slug="empresa-"+UUID.randomUUID().toString().substring(0,12);token=register(slug);
        professional=request("POST","/api/professionals",Map.of("name","Ana"),token,201).get("id").asText();
        service=request("POST","/api/services",Map.of("name","Consulta","durationMinutes",30,"price",100.00),token,201).get("id").asText();
        client=request("POST","/api/clients",Map.of("name","Cliente","email","cliente@example.com","phone","11999990000"),token,201).get("id").asText();
        request("PUT","/api/professionals/"+professional+"/services",List.of(service),token,200);
        List<Map<String,Object>> hours=new ArrayList<>();
        for(int day=1;day<=7;day++) {
            hours.add(Map.of("dayOfWeek",day,"startTime","09:00","endTime","12:00"));
            hours.add(Map.of("dayOfWeek",day,"startTime","13:00","endTime","17:00"));
        }
        request("PUT","/api/professionals/"+professional+"/working-hours",hours,token,200);
    }
    Map<String,Object> booking(String start) { return Map.of("professionalId",professional,"serviceId",service,"clientId",client,"startsAt",start,"notes","Teste"); }
    String book(String start) throws Exception { return request("POST","/api/appointments",booking(start),token,201).get("id").asText(); }
    @Test void registrationLoginHealthAndValidation() throws Exception {
        request("GET","/api/health",null,null,200);
        var result=request("POST","/api/auth/login",Map.of("slug",slug,"email","LISA@example.com","password",PASSWORD),null,200);
        request("GET","/api/auth/me",null,result.get("accessToken").asText(),200);
        request("POST","/api/auth/login",Map.of("slug",slug,"email","lisa@example.com","password","wrong"),null,401);
        request("GET","/api/clients",null,null,401);
        request("POST","/api/services",Map.of("name","Invalid","durationMinutes",0,"price",-1),token,400);
        request("GET","/api/clients?limit=501",null,token,400);
        request("POST","/api/auth/register",Map.of("companyName","X","slug",slug,"timezone","UTC","name","Lisa","email","lisa@example.com","password",PASSWORD),null,409);
    }
    @Test void tenantIsolationIncludesReadsWritesAndReferences() throws Exception {
        String other=register("outra-"+UUID.randomUUID().toString().substring(0,12));
        request("GET","/api/clients/"+client,null,other,404);
        request("PUT","/api/clients/"+client,Map.of("name","Invadido"),other,404);
        request("DELETE","/api/clients/"+client,null,other,404);
        assertThat(request("GET","/api/clients",null,other,200)).isEmpty();
        request("POST","/api/appointments",booking("2030-01-07T09:00:00Z"),other,404);
        String otherClient=request("POST","/api/clients",Map.of("name","Outro"),other,201).get("id").asText();
        var input=new HashMap<>(booking("2030-01-07T09:00:00Z"));input.put("clientId",otherClient);
        request("POST","/api/appointments",input,token,404);
        String id=book("2030-01-07T09:00:00Z");
        request("GET","/api/appointments/"+id,null,other,404);
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","CANCELED"),other,404);
        request("GET","/api/appointments/"+id+"/events",null,other,404);
    }
    @Test void availabilityRespectsBreaksBookingsAndTimeOff() throws Exception {
        String id=book("2030-01-07T09:00:00Z");
        request("POST","/api/professionals/"+professional+"/time-off",Map.of("startsAt","2030-01-07T14:00:00Z","endsAt","2030-01-07T15:00:00Z","reason","Folga"),token,201);
        var slots=request("GET","/api/availability?professionalId="+professional+"&serviceId="+service+"&date=2030-01-07",null,token,200);
        var starts=new ArrayList<Instant>();slots.forEach(s->starts.add(OffsetDateTime.parse(s.get("startsAt").asText()).toInstant()));
        assertThat(starts).contains(Instant.parse("2030-01-07T09:30:00Z")).doesNotContain(Instant.parse("2030-01-07T09:00:00Z"),Instant.parse("2030-01-07T11:45:00Z"),Instant.parse("2030-01-07T14:00:00Z"));
        request("POST","/api/appointments",booking("2030-01-07T11:45:00Z"),token,409);
        request("POST","/api/appointments",booking("2030-01-07T14:00:00Z"),token,409);
        request("POST","/api/appointments",booking("2029-12-31T09:00:00Z"),token,400);
        request("PATCH","/api/appointments/"+id+"/reschedule",Map.of("startsAt","2030-01-07T10:00:00Z"),token,200);
        book("2030-01-07T09:00:00Z");
    }
    @Test void concurrentRequestsCannotDoubleBookAndAdjacentSlotsWork() throws Exception {
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task=()->{gate.await();return perform("POST","/api/appointments",booking("2030-01-07T09:00:00Z"),token).getResponse().getStatus();};
            var first=pool.submit(task);var second=pool.submit(task);gate.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } finally {pool.shutdownNow();}
        book("2030-01-07T09:30:00Z");
        request("POST","/api/appointments",booking("2030-01-07T09:15:00Z"),token,409);
    }
    @Test void lifecycleAndAuditRejectInvalidTransitions() throws Exception {
        String id=book("2030-01-07T09:00:00Z");
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","COMPLETED"),token,409);
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","CONFIRMED"),token,200);
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","COMPLETED"),token,409);
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","NO_SHOW"),token,409);
        clock.value=Instant.parse("2030-01-07T09:31:00Z");
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","COMPLETED"),token,200);
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","CONFIRMED"),token,409);
        request("PATCH","/api/appointments/"+id+"/reschedule",Map.of("startsAt","2030-01-08T10:00:00Z"),token,409);
        assertThat(request("GET","/api/appointments/"+id+"/events",null,token,200)).hasSize(3);
    }
    @Test void cancelFreesSlotNoShowAndPriceSnapshot() throws Exception {
        String id=book("2030-01-07T09:00:00Z");
        request("PUT","/api/services/"+service,Map.of("name","Consulta","durationMinutes",60,"price",200),token,200);
        var saved=request("PATCH","/api/appointments/"+id+"/reschedule",Map.of("startsAt","2030-01-07T10:00:00Z"),token,200);
        assertThat(saved.get("price").decimalValue()).isEqualByComparingTo("100");
        assertThat(Duration.between(Instant.parse(saved.get("startsAt").asText()),Instant.parse(saved.get("endsAt").asText()))).isEqualTo(Duration.ofMinutes(30));
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","CANCELED"),token,200);
        String next=book("2030-01-07T10:00:00Z");
        request("PATCH","/api/appointments/"+next+"/status",Map.of("status","CONFIRMED"),token,200);
        clock.value=Instant.parse("2030-01-07T10:00:00Z");
        request("PATCH","/api/appointments/"+next+"/status",Map.of("status","NO_SHOW"),token,200);
    }
    @Test void scheduleChangesCannotInvalidateExistingAppointments() throws Exception {
        book("2030-01-07T09:00:00Z");
        request("PUT","/api/professionals/"+professional+"/working-hours",List.of(),token,409);
        assertThat(request("GET","/api/professionals/"+professional+"/working-hours",null,token,200)).hasSize(14);
        request("POST","/api/professionals/"+professional+"/time-off",Map.of("startsAt","2030-01-07T09:00:00Z","endsAt","2030-01-07T10:00:00Z","reason","Folga"),token,409);
        request("DELETE","/api/professionals/"+professional,null,token,409);
        request("PUT","/api/professionals/"+professional+"/working-hours",List.of(Map.of("dayOfWeek",0,"startTime","09:00","endTime","08:00")),token,400);
    }
    String userToken(String role,String prof) throws Exception {
        String email=UUID.randomUUID()+"@example.com";var input=new HashMap<String,Object>(Map.of("name",role,"email",email,"password",PASSWORD,"role",role));
        if(prof!=null) input.put("professionalId",prof);
        request("POST","/api/users",input,token,201);
        return request("POST","/api/auth/login",Map.of("slug",slug,"email",email,"password",PASSWORD),null,200).get("accessToken").asText();
    }
    @Test void rolesRestrictCatalogAndProfessionalAgenda() throws Exception {
        String id=book("2030-01-07T09:00:00Z");String worker=userToken("PROFESSIONAL",professional),attendant=userToken("ATTENDANT",null);
        request("GET","/api/clients",null,worker,403);
        request("POST","/api/services",Map.of("name","Consulta","durationMinutes",30,"price",100),attendant,403);
        request("POST","/api/appointments",booking("2030-01-07T10:00:00Z"),worker,403);
        request("GET","/api/appointments/"+id,null,worker,200);
        String otherProfessional=request("POST","/api/professionals",Map.of("name","Outro"),token,201).get("id").asText();
        request("GET","/api/professionals/"+otherProfessional+"/working-hours",null,worker,403);
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","CANCELED"),worker,403);
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","CONFIRMED"),attendant,200);
        clock.value=Instant.parse("2030-01-07T09:31:00Z");
        request("PATCH","/api/appointments/"+id+"/status",Map.of("status","COMPLETED"),worker,200);
    }
    @Test void disablingUserInvalidatesExistingTokenAndPasswordCanChange() throws Exception {
        String attendant=userToken("ATTENDANT",null);
        String user=request("GET","/api/auth/me",null,attendant,200).get("id").asText();
        request("DELETE","/api/users/"+user,null,token,204);
        request("GET","/api/auth/me",null,attendant,401);
        request("PUT","/api/auth/password",Map.of("currentPassword",PASSWORD,"newPassword","Novo-Password-2026!"),token,204);
        request("GET","/api/auth/me",null,token,401);
        request("POST","/api/auth/login",Map.of("slug",slug,"email","lisa@example.com","password",PASSWORD),null,401);
        request("POST","/api/auth/login",Map.of("slug",slug,"email","lisa@example.com","password","Novo-Password-2026!"),null,200);
    }
    @Test void malformedJwtAndForeignTenantFieldAreRejected() throws Exception {
        request("GET","/api/auth/me",null,token.substring(0,token.lastIndexOf('.')+1)+"invalid",401);
        var body=new HashMap<>(booking("2030-01-07T09:00:00Z"));body.put("tenantId",UUID.randomUUID().toString());
        request("POST","/api/appointments",body,token,400);
    }
}
