package com.votechain.backend.auth.controller;

import com.votechain.backend.auth.dto.AuthRequest;
import com.votechain.backend.auth.dto.AuthResponse;
import com.votechain.backend.auth.dto.RegisterRequest;
import com.votechain.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")  // ✅ Sin /api porque ya está en context-path
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Autenticación", description = "API de autenticación y registro de usuarios")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Operation(
        summary = "Iniciar sesión de usuario",
        description = "Permite autenticar un usuario con sus credenciales y devuelve un token JWT para acceder a recursos protegidos",
        tags = { "Autenticación" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Autenticación exitosa",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Credenciales inválidas"),
        @ApiResponse(responseCode = "400", description = "Solicitud incorrecta")
    })
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(
            @Parameter(description = "Datos de autenticación del usuario", required = true)
            @Valid @RequestBody AuthRequest loginRequest) {
        AuthResponse response = authService.authenticate(loginRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Registrar nuevo usuario",
        description = "Permite registrar un nuevo usuario en el sistema. Valida que las contraseñas coincidan y que el DNI/email no estén ya registrados",
        tags = { "Autenticación" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario registrado exitosamente",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o usuario ya existe"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @Parameter(description = "Datos de registro del usuario", required = true)
            @Valid @RequestBody RegisterRequest registerRequest) {
        if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
            return ResponseEntity.badRequest().body("Las contraseñas no coinciden");
        }

        AuthResponse response = authService.register(registerRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Renovar token JWT",
        description = "Permite renovar un token JWT expirado utilizando el refresh token",
        tags = { "Autenticación" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token renovado exitosamente",
            content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Refresh token inválido o expirado"),
        @ApiResponse(responseCode = "400", description = "Solicitud incorrecta")
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @Parameter(description = "Refresh token para renovar la sesión", required = true)
            @RequestBody String refreshToken) {
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }
}
