# Etapa 1: Compilar el proyecto con Maven y JDK 17
FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /app

# Copiar archivos necesarios
COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn

# Asegurar permisos de ejecución para mvnw (problemas comunes en Railway)
RUN chmod +x mvnw

# Descargar dependencias (esto se cachea si no cambia pom.xml)
RUN ./mvnw dependency:go-offline -B

# Copiar el resto del código fuente
COPY src ./src

# Construir el JAR sin ejecutar tests
RUN ./mvnw clean package -DskipTests

# Etapa 2: Imagen ligera para producción
FROM eclipse-temurin:17-jre-alpine

# Instalar curl para health check
RUN apk add --no-cache curl

# Crear usuario no-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copiar el JAR desde la etapa anterior
COPY --from=builder /app/target/*.jar app.jar

# Asignar propiedad del JAR
RUN chown appuser:appgroup app.jar

USER appuser

# Exponer el puerto (Spring por defecto)
EXPOSE 8080

# Variables de entorno básicas (Railway te permite sobrescribirlas)
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

# Healthcheck para plataformas tipo Railway/Render
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Ejecutar la app
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "app.jar"]
