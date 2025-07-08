package com.votechain.backend.auth.controller;

import com.votechain.backend.auth.dto.UserDto;
import com.votechain.backend.auth.dto.UserUpdateRequest;
import com.votechain.backend.auth.service.UserService;
import com.votechain.backend.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")  // ✅ Sin /api porque ya está en context-path
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Usuarios", description = "Gestión de perfiles de usuario y consultas relacionadas")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * Get current user profile
     */
    @Operation(
        summary = "Obtener perfil del usuario actual",
        description = "Devuelve la información completa del perfil del usuario autenticado",
        tags = { "Usuarios", "Perfil" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Perfil obtenido correctamente",
            content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @GetMapping("/profile")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> getCurrentUserProfile(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            UserDto userProfile = userService.getUserProfile(userDetails.getId());
            return ResponseEntity.ok(userProfile);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error obteniendo perfil del usuario {}", userDetails.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error obteniendo perfil: " + e.getMessage()));
        }
    }

    /**
     * Update current user profile
     */
    @Operation(
        summary = "Actualizar perfil del usuario actual",
        description = "Permite al usuario actualizar su información personal",
        tags = { "Usuarios", "Perfil" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Perfil actualizado correctamente",
            content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
        @ApiResponse(responseCode = "409", description = "Email ya está en uso")
    })
    @PutMapping("/profile")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> updateProfile(
            @Parameter(description = "Datos actualizados del perfil", required = true)
            @Valid @RequestBody UserUpdateRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            UserDto updatedUser = userService.updateUserProfile(userDetails.getId(), request);
            return ResponseEntity.ok(updatedUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Error actualizando perfil del usuario {}", userDetails.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error actualizando perfil: " + e.getMessage()));
        }
    }

    /**
     * Get user by ID (public information)
     */
    @Operation(
        summary = "Obtener información pública de un usuario",
        description = "Devuelve la información pública de un usuario específico (útil para mostrar creadores de votaciones)",
        tags = { "Usuarios" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario obtenido correctamente",
            content = @Content(schema = @Schema(implementation = UserDto.class))),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(
            @Parameter(description = "ID del usuario", required = true) @PathVariable Long id) {
        try {
            UserDto user = userService.getUserPublicInfo(id);
            return ResponseEntity.ok(user);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error obteniendo usuario {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error obteniendo usuario: " + e.getMessage()));
        }
    }

    /**
     * Get all users (admin only)
     */
    @Operation(
        summary = "Listar todos los usuarios",
        description = "Permite a un administrador ver todos los usuarios del sistema con paginación",
        tags = { "Usuarios", "Administración" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de usuarios obtenida correctamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserDto>> getAllUsers(
            @Parameter(description = "Número de página (empieza en 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Buscar por texto en nombre o email") @RequestParam(required = false) String search) {

        Page<UserDto> users = userService.getAllUsers(page, size, search);
        return ResponseEntity.ok(users);
    }

    /**
     * Change user password
     */
    @Operation(
        summary = "Cambiar contraseña",
        description = "Permite al usuario cambiar su contraseña actual",
        tags = { "Usuarios", "Seguridad" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contraseña cambiada correctamente"),
        @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta"),
        @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    @PostMapping("/change-password")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> changePassword(
            @Parameter(description = "Datos para cambio de contraseña", required = true)
            @RequestBody Map<String, String> passwordRequest,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            String currentPassword = passwordRequest.get("currentPassword");
            String newPassword = passwordRequest.get("newPassword");

            if (currentPassword == null || newPassword == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Se requieren currentPassword y newPassword"));
            }

            userService.changePassword(userDetails.getId(), currentPassword, newPassword);
            return ResponseEntity.ok(Map.of("message", "Contraseña cambiada exitosamente"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Error cambiando contraseña del usuario {}", userDetails.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error cambiando contraseña: " + e.getMessage()));
        }
    }

    /**
     * Get user statistics
     */
    @Operation(
        summary = "Obtener estadísticas del usuario",
        description = "Devuelve estadísticas de participación del usuario autenticado",
        tags = { "Usuarios", "Estadísticas" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas correctamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    @GetMapping("/profile/statistics")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> getUserStatistics(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            Map<String, Object> statistics = userService.getUserStatistics(userDetails.getId());
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("❌ Error obteniendo estadísticas del usuario {}", userDetails.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error obteniendo estadísticas: " + e.getMessage()));
        }
    }

    /**
     * Deactivate user account
     */
    @Operation(
        summary = "Desactivar cuenta de usuario",
        description = "Permite al usuario desactivar su propia cuenta",
        tags = { "Usuarios", "Cuenta" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cuenta desactivada correctamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    @PostMapping("/profile/deactivate")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> deactivateAccount(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            userService.deactivateUser(userDetails.getId());
            return ResponseEntity.ok(Map.of("message", "Cuenta desactivada exitosamente"));
        } catch (Exception e) {
            log.error("❌ Error desactivando cuenta del usuario {}", userDetails.getId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error desactivando cuenta: " + e.getMessage()));
        }
    }
}
