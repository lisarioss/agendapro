package com.lisarios.agendapro;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles(value="postgres-test",inheritProfiles=false)
@EnabledIfSystemProperty(named="pg.tests",matches="true")
public class PostgresIntegrationTest extends AgendaProIntegrationTest {
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->System.getProperty("pg.url","jdbc:postgresql://localhost:5432/agendapro_test"));
        registry.add("spring.datasource.username",()->System.getProperty("pg.user","agendapro"));
        registry.add("spring.datasource.password",()->System.getProperty("pg.password","agendapro"));
        registry.add("app.jwt-secret",()->"somente-testes-postgresql-chave-nao-utilizar-em-producao-2026");
    }
    @Test void postgresConstraintRejectsOverlapEvenWithoutServiceLayer() throws Exception {
        String id=book("2030-01-07T09:00:00Z");
        assertThatThrownBy(()->jdbc.update("INSERT INTO appointments(id,tenant_id,professional_id,client_id,service_id,starts_at,ends_at,status,price,notes) SELECT ?,tenant_id,professional_id,client_id,service_id,starts_at,ends_at,status,price,notes FROM appointments WHERE id=?",java.util.UUID.randomUUID(),java.util.UUID.fromString(id)))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
