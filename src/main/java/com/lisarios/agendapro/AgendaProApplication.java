package com.lisarios.agendapro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
public class AgendaProApplication {
    public static void main(String[] args) { SpringApplication.run(AgendaProApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
