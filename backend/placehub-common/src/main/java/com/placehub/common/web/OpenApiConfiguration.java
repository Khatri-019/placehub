package com.placehub.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata with a bearer-JWT security scheme. Internal controllers are tagged "internal". */
@Configuration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiConfiguration {

    @Bean
    public OpenAPI placeHubOpenApi(@Value("${spring.application.name}") String name) {
        return new OpenAPI()
                .info(new Info().title("PlaceHub Lite - " + name).version("v1"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
