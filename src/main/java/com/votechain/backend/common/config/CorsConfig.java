package com.votechain.backend.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:3002,http://127.0.0.1:3000}")
    private String[] allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD}")
    private String[] allowedMethods;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Rutas privadas con credenciales
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods(allowedMethods)
                .allowedHeaders(
                        "authorization", "content-type", "x-auth-token",
                        "x-requested-with", "accept", "origin",
                        "access-control-request-method", "access-control-request-headers"
                )
                .exposedHeaders("Authorization", "x-auth-token", "Content-Type", "Content-Length")
                .allowCredentials(false)
                .maxAge(maxAge);

        // Rutas de autenticación con credenciales
        registry.addMapping("/api/auth/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders(
                        "authorization", "content-type", "x-auth-token",
                        "x-requested-with", "accept", "origin",
                        "access-control-request-method", "access-control-request-headers"
                )
                .allowCredentials(false)
                .maxAge(maxAge);

        // Rutas públicas sin credenciales (p. ej. estadísticas públicas)
        registry.addMapping("/api/dashboard/public-stats")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders(
                        "authorization", "content-type", "x-auth-token",
                        "x-requested-with", "accept", "origin",
                        "access-control-request-method", "access-control-request-headers"
                )
                .allowCredentials(false)
                .maxAge(maxAge);

        // Swagger UI (público sin credenciales)
        registry.addMapping("/swagger-ui/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders(
                        "authorization", "content-type", "x-auth-token",
                        "x-requested-with", "accept", "origin",
                        "access-control-request-method", "access-control-request-headers"
                )
                .allowCredentials(false)
                .maxAge(maxAge);

        // OpenAPI docs (público sin credenciales)
        registry.addMapping("/v3/api-docs/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders(
                        "authorization", "content-type", "x-auth-token",
                        "x-requested-with", "accept", "origin",
                        "access-control-request-method", "access-control-request-headers"
                )
                .allowCredentials(false)
                .maxAge(maxAge);
    }
}
