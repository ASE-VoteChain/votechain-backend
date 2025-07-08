package com.votechain.backend.auth.dto;

import com.votechain.backend.auth.model.UserRole;
import com.votechain.backend.auth.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String dni;
    private String telefono;
    private String direccion;
    private String ciudad;
    private String codigoPostal;
    private String biografia;
    private UserRole role;
    private UserStatus status;
    private boolean active;
    private boolean twoFactorEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;

    // Campos adicionales para estadísticas (solo para perfil completo)
    private Long totalVotacionesCreadas;
    private Long totalVotosEmitidos;
    private Double participacionPorcentaje;

    public String getFullName() {
        if (fullName != null) {
            return fullName;
        }
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    public static UserDto createPublicView(UserDto user) {
        return UserDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .build();
    }

    public static UserDto createBasicView(UserDto user) {
        return UserDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .dni(user.getDni())
                .role(user.getRole())
                .status(user.getStatus())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
