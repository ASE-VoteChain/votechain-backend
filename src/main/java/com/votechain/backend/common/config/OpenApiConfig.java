package com.votechain.backend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    private static final String SECURITY_SCHEME_NAME = "bearer-jwt";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de VoteChain")
                        .description("API de la plataforma VoteChain para gestión de votaciones seguras utilizando blockchain. " +
                                "Esta API permite la gestión completa del ciclo de vida de votaciones electrónicas " +
                                "desde su creación, configuración, votación y cierre. Todas las operaciones son " +
                                "auditables y se registran en la blockchain para garantizar su integridad.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipo VoteChain")
                                .url("https://votechain.example.com")
                                .email("contact@votechain.example.com"))
                        .license(new License()
                                .name("Licencia MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(Arrays.asList(
                        new Server().url("http://localhost:8080" + contextPath).description("Servidor de desarrollo local"),
                        new Server().url("https://api.votechain-demo.com").description("Servidor de pruebas")
                ))
                .tags(Arrays.asList(
                        new Tag().name("Autenticación").description("Operaciones relacionadas con la autenticación y gestión de usuarios"),
                        new Tag().name("Votaciones").description("Operaciones para la gestión de votaciones"),
                        new Tag().name("Votos").description("Operaciones relacionadas con el proceso de votación"),
                        new Tag().name("Blockchain").description("Operaciones de verificación y registro en blockchain"),
                        new Tag().name("Administración").description("Operaciones administrativas del sistema")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Ingresa el token JWT (sin el prefijo Bearer)")
                        )
                );
    }
}
