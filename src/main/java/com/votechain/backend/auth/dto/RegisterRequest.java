package com.votechain.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos necesarios para registrar un nuevo usuario en la plataforma")
public class RegisterRequest {

    @Schema(description = "Correo electrónico del usuario, debe ser único en el sistema", example = "usuario@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email should be valid")
    private String email;

    @Schema(description = "Documento Nacional de Identidad del usuario, debe ser único y válido", example = "12345678A", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "DNI cannot be empty")
    @Size(min = 8, max = 15, message = "DNI must be between 8 and 15 characters")
    private String dni;

    @Schema(description = "Nombre del usuario", example = "Juan", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "First name cannot be empty")
    private String firstName;

    @Schema(description = "Apellido(s) del usuario", example = "Pérez García", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Last name cannot be empty")
    private String lastName;

    @Schema(description = "Contraseña del usuario, debe tener al menos 8 caracteres y cumplir con política de seguridad", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8)
    @NotBlank(message = "Password cannot be empty")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @Schema(description = "Confirmación de la contraseña, debe coincidir exactamente con el campo password", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Confirm password cannot be empty")
    private String confirmPassword;

    @Schema(description = "Código de verificación opcional para registros controlados", example = "ABC123", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    // Optional verification code for controlled registrations
    private String verificationCode;
}
