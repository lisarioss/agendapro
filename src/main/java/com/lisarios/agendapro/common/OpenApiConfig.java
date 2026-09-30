package com.lisarios.agendapro.common;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean OpenAPI apiDocumentation() {
        return new OpenAPI().info(new Info().title("AgendaPro API").version("1.0.0")
            .description("Isolamento por empresa derivado do JWT. Horarios ISO-8601 com offset; intervalos [inicio,fim)."))
            .components(new Components().addSecuritySchemes("bearerAuth",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
