package com.votechain.backend.voting.controller;

import com.votechain.backend.security.UserDetailsImpl;
import com.votechain.backend.voting.dto.VotacionOpcionDto;
import com.votechain.backend.voting.service.VotacionOpcionService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/votacion-opciones")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Opciones de Votación", description = "Gestión de opciones dentro de las votaciones")
@Slf4j
public class VotacionOpcionController {

    @Autowired
    private VotacionOpcionService opcionService;

    /**
     * Get all options for a specific votacion
     */
    @Operation(
        summary = "Obtener opciones de una votación",
        description = "Devuelve todas las opciones disponibles para una votación específica",
        tags = { "Opciones de Votación" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Opciones obtenidas correctamente"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @GetMapping("/votacion/{votacionId}")
    public ResponseEntity<List<VotacionOpcionDto>> getOpcionesByVotacion(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long votacionId) {

        List<VotacionOpcionDto> opciones = opcionService.getOpcionesByVotacion(votacionId);
        return ResponseEntity.ok(opciones);
    }

    /**
     * Get a specific option by ID
     */
    @Operation(
        summary = "Obtener opción específica",
        description = "Devuelve los detalles de una opción específica por su ID",
        tags = { "Opciones de Votación" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Opción obtenida correctamente"),
        @ApiResponse(responseCode = "404", description = "Opción no encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<?> getOpcionById(
            @Parameter(description = "ID de la opción", required = true) @PathVariable Long id) {
        try {
            VotacionOpcionDto opcion = opcionService.getOpcionById(id);
            return ResponseEntity.ok(opcion);
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error obteniendo opción {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error obteniendo opción: " + e.getMessage()));
        }
    }

    /**
     * Create a new option for a votacion
     */
    @Operation(
        summary = "Crear nueva opción",
        description = "Permite a un administrador agregar una nueva opción a una votación",
        tags = { "Opciones de Votación", "Administración" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Opción creada correctamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o votación no editable"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createOpcion(
            @Parameter(description = "Datos de la nueva opción", required = true)
            @Valid @RequestBody VotacionOpcionDto request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            VotacionOpcionDto nuevaOpcion = opcionService.createOpcion(request, userDetails.getId());
            return new ResponseEntity<>(nuevaOpcion, HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error creando opción", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error creando opción: " + e.getMessage()));
        }
    }

    /**
     * Update an existing option
     */
    @Operation(
        summary = "Actualizar opción existente",
        description = "Permite a un administrador modificar una opción existente (solo si la votación está en estado CREADA o PROXIMA)",
        tags = { "Opciones de Votación", "Administración" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Opción actualizada correctamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o votación no editable"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido"),
        @ApiResponse(responseCode = "404", description = "Opción no encontrada")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateOpcion(
            @Parameter(description = "ID de la opción", required = true) @PathVariable Long id,
            @Parameter(description = "Datos actualizados de la opción", required = true)
            @Valid @RequestBody VotacionOpcionDto request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            VotacionOpcionDto opcionActualizada = opcionService.updateOpcion(id, request, userDetails.getId());
            return ResponseEntity.ok(opcionActualizada);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error actualizando opción {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error actualizando opción: " + e.getMessage()));
        }
    }

    /**
     * Delete an option
     */
    @Operation(
        summary = "Eliminar opción",
        description = "Permite a un administrador eliminar una opción (solo si la votación está en estado CREADA o PROXIMA)",
        tags = { "Opciones de Votación", "Administración" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Opción eliminada correctamente"),
        @ApiResponse(responseCode = "400", description = "Votación no editable o no se puede eliminar"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido"),
        @ApiResponse(responseCode = "404", description = "Opción no encontrada")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteOpcion(
            @Parameter(description = "ID de la opción", required = true) @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            opcionService.deleteOpcion(id, userDetails.getId());
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error eliminando opción {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error eliminando opción: " + e.getMessage()));
        }
    }

    /**
     * Reorder options within a votacion
     */
    @Operation(
        summary = "Reordenar opciones",
        description = "Permite a un administrador cambiar el orden de las opciones en una votación",
        tags = { "Opciones de Votación", "Administración" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Opciones reordenadas correctamente"),
        @ApiResponse(responseCode = "400", description = "Votación no editable o datos inválidos"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    @PostMapping("/votacion/{votacionId}/reordenar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reorderOpciones(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long votacionId,
            @Parameter(description = "Lista de IDs de opciones en el nuevo orden", required = true)
            @RequestBody List<Long> opcionIds,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            List<VotacionOpcionDto> opcionesReordenadas = opcionService.reorderOpciones(votacionId, opcionIds, userDetails.getId());
            return ResponseEntity.ok(opcionesReordenadas);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error reordenando opciones para votación {}", votacionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error reordenando opciones: " + e.getMessage()));
        }
    }
}
