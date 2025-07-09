# Etapa 1: Construcción del JAR
FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /app

# Copiar archivos necesarios para las dependencias
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .

# Descargar dependencias (optimiza la cache de Docker)
RUN ./mvnw dependency:go-offline -B

# Copiar el código fuente
COPY src ./src

# Compilar el proyecto, sin ejecutar tests
RUN ./mvnw clean package -DskipTests -X

# Etapa 2: Imagen ligera para producción
FROM eclipse-temurin:17-jre-alpine

# Instalar curl para el health check
RUN apk add --no-cache curl

# Crear usuario no-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copiar el JAR desde la etapa anterior
COPY --from=builder /app/target/*.jar app.jar

# Asignar permisos al usuario no-root
RUN chown appuser:appgroup app.jar

USER appuser

# Puerto por defecto de Spring Boot
EXPOSE 8080

# Variables de entorno para Spring Boot
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

# Healthcheck para entornos cloud
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Comando para ejecutar la app
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "app.jar"]
