# Usar una imagen base con JDK 17 para construir la aplicación
FROM maven:3.9.4-eclipse-temurin-17 as build

# Establecer el directorio de trabajo
WORKDIR /app

# Copiar archivos de configuración de Maven
COPY pom.xml .
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn .mvn

# Descargar dependencias (esto se cachea si el pom.xml no cambia)
RUN mvn dependency:go-offline -B

# Copiar el código fuente
COPY src src

# Construir la aplicación
RUN mvn clean package -DskipTests

# Usar una imagen más ligera para ejecutar la aplicación
FROM eclipse-temurin:17-jre-alpine

# Instalar curl para health checks
RUN apk add --no-cache curl

# Crear un usuario no-root para ejecutar la aplicación
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Establecer el directorio de trabajo
WORKDIR /app

# Copiar el JAR construido desde la etapa anterior
COPY --from=build /app/target/*.jar app.jar

# Cambiar la propiedad del archivo al usuario no-root
RUN chown appuser:appgroup app.jar

# Cambiar al usuario no-root
USER appuser

# Exponer el puerto
EXPOSE 8080

# Variables de entorno para configuración
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "-Djava.security.egd=file:/dev/./urandom", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}", "app.jar"]
