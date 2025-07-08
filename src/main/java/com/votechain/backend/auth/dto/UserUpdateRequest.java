package com.votechain.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    private String firstName;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
    private String lastName;

    @Email(message = "Formato de email inválido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    @Pattern(regexp = "^[0-9]{8}[A-Z]?$", message = "DNI debe tener 8 dígitos seguidos opcionalmente de una letra")
    private String dni;

    @Pattern(regexp = "^[+]?[0-9]{9,15}$", message = "Teléfono debe tener entre 9 y 15 dígitos")
    private String telefono;

    @Size(max = 100, message = "La dirección no puede exceder 100 caracteres")
    private String direccion;

    @Size(max = 50, message = "La ciudad no puede exceder 50 caracteres")
    private String ciudad;

    @Pattern(regexp = "^[0-9]{5}$", message = "Código postal debe tener 5 dígitos")
    private String codigoPostal;

    @Size(max = 500, message = "La biografía no puede exceder 500 caracteres")
    private String biografia;
}
