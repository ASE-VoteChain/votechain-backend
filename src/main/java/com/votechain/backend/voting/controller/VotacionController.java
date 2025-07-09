package com.votechain.backend.voting.controller;

import com.votechain.backend.auth.repository.UserRepository;
import com.votechain.backend.auth.model.User;
import com.votechain.backend.blockchain.service.BlockchainService; // ✅ AGREGAR: Import BlockchainService
import com.votechain.backend.vote.model.Vote;
import com.votechain.backend.vote.repository.VoteRepository;
import com.votechain.backend.vote.service.VoteService;
import com.votechain.backend.voting.dto.CreateVotacionRequest;
import com.votechain.backend.voting.dto.VotacionDto;
import com.votechain.backend.voting.model.Votacion;
import com.votechain.backend.voting.model.VotacionCategoria;
import com.votechain.backend.voting.model.VotacionEstado;
import com.votechain.backend.voting.repository.VotacionRepository;
import com.votechain.backend.security.UserDetailsImpl;
import com.votechain.backend.voting.service.VotacionService;
import com.votechain.backend.voting.service.VotacionPermissionService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit; // ✅ AGREGAR: Import TimeUnit
import java.util.stream.Collectors;

@RestController
@RequestMapping("/votaciones")  // ✅ CORREGIR: Sin /api porque ya está en context-path
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Votaciones", description = "Gestión de votaciones y consultas relacionadas")
@Slf4j
public class VotacionController {

    @Autowired
    private VotacionService votacionService;

    @Autowired
    private VotacionRepository votacionRepository;

    // ✅ AGREGAR: Inyectar BlockchainService
    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private VoteService voteService;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VotacionPermissionService permissionService;

    @Operation(
        summary = "Obtener votaciones públicas",
        description = "Permite consultar todas las votaciones públicas con paginación y filtrado por estado, categoría y búsqueda de texto",
        tags = { "Votaciones" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de votaciones obtenida correctamente",
            content = @Content(schema = @Schema(implementation = Page.class)))
    })
    @GetMapping("/public/votaciones")
    public ResponseEntity<Page<VotacionDto>> getPublicVotaciones(
            @Parameter(description = "Número de página (empieza en 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Filtrar por estado de la votación") @RequestParam(required = false) VotacionEstado estado,
            @Parameter(description = "Filtrar por categoría") @RequestParam(required = false) VotacionCategoria categoria,
            @Parameter(description = "Buscar por texto en título o descripción") @RequestParam(required = false) String search) {

        Page<VotacionDto> votaciones = votacionService.getPublicVotaciones(
                page, size, estado, categoria, search);

        return ResponseEntity.ok(votaciones);
    }

    @Operation(
        summary = "Obtener votaciones activas",
        description = "Devuelve solo las votaciones que están activas en el momento actual",
        tags = { "Votaciones" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de votaciones activas obtenida correctamente",
            content = @Content(schema = @Schema(implementation = Page.class)))
    })
    @GetMapping("/public/votaciones/active")
    public ResponseEntity<Page<VotacionDto>> getActiveVotaciones(
            @Parameter(description = "Número de página (empieza en 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "10") int size) {

        Page<VotacionDto> votaciones = votacionService.getActiveVotaciones(page, size);
        return ResponseEntity.ok(votaciones);
    }

    @Operation(
        summary = "Obtener detalle de una votación pública",
        description = "Permite ver los detalles completos de una votación específica, incluyendo sus opciones",
        tags = { "Votaciones" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Votación obtenida correctamente",
            content = @Content(schema = @Schema(implementation = VotacionDto.class))),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @GetMapping("/public/votaciones/{id}")
    public ResponseEntity<VotacionDto> getPublicVotacionDetail(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id) {
        VotacionDto votacion = votacionService.getVotacionById(id);
        return ResponseEntity.ok(votacion);
    }

    @Operation(
        summary = "Obtener votaciones para usuario autenticado",
        description = "Devuelve las votaciones con información adicional para el usuario logueado, incluyendo si ha participado",
        tags = { "Votaciones" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de votaciones obtenida correctamente",
            content = @Content(schema = @Schema(implementation = Page.class))),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    @GetMapping("/user/votaciones")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Page<VotacionDto>> getUserVotaciones(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Parameter(description = "Número de página (empieza en 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Filtrar por estado de la votación") @RequestParam(required = false) VotacionEstado estado,
            @Parameter(description = "Filtrar por categoría") @RequestParam(required = false) VotacionCategoria categoria,
            @Parameter(description = "Filtrar por participación del usuario (true=solo las que ha votado)") @RequestParam(required = false) Boolean participated) {

        Page<VotacionDto> votaciones = votacionService.getUserVotaciones(
                userDetails.getId(), page, size, estado, categoria, participated);

        return ResponseEntity.ok(votaciones);
    }

    @Operation(
        summary = "Obtener detalle de votación para usuario autenticado",
        description = "Devuelve los detalles de una votación específica con información de participación del usuario",
        tags = { "Votaciones" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Votación obtenida correctamente",
            content = @Content(schema = @Schema(implementation = VotacionDto.class))),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @GetMapping("/user/votaciones/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<VotacionDto> getUserVotacionDetail(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id) {

        VotacionDto votacion = votacionService.getUserVotacionDetail(userDetails.getId(), id);
        return ResponseEntity.ok(votacion);
    }

    /**
     * Create new votacion with full blockchain integration
     */
    @Operation(
        summary = "Crear nueva votación",
        description = "Permite a un administrador crear una nueva votación integrada automáticamente con blockchain",
        tags = { "Votaciones", "Administración" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Votación creada correctamente",
            content = @Content(schema = @Schema(implementation = VotacionDto.class))),
        @ApiResponse(responseCode = "400", description = "Datos de la votación inválidos"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> createVotacion(
            @Parameter(description = "Datos para crear la votación", required = true)
            @Valid @RequestBody CreateVotacionRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        try {
            log.info("🚀 Iniciando creación de votación con blockchain para usuario {}", userDetails.getId());

            User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

            if (!permissionService.canCreateVotacion(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos para crear votaciones"));
            }

            // 1. ✅ Crear votación en BD local PRIMERO
            log.info("📝 Creando votación en base de datos local...");
            VotacionDto createdVotacion = votacionService.createVotacion(request, userDetails.getId());
            log.info("✅ Votación creada en BD local: ID={}, Título={}",
                createdVotacion.getId(), createdVotacion.getTitulo());

            // 2. 🔗 Crear la votación en blockchain
            log.info("🔗 Creando votación {} en blockchain...", createdVotacion.getId());
            String txHashVotacion = null;
            Long blockchainVotingId = null;

            try {
                BlockchainService.VotingCreationResult result = blockchainService.createVotacionInBlockchain(
                    createdVotacion.getId(),
                    createdVotacion.getTitulo(),
                    createdVotacion.getFechaInicio(),
                    createdVotacion.getFechaFin()
                ).get(30, TimeUnit.SECONDS);

                txHashVotacion = result.getTransactionHash();
                blockchainVotingId = result.getBlockchainVotingId();

                log.info("✅ Votación creada en blockchain con hash: {}", txHashVotacion);
                log.info("🔑 ID real en blockchain: {} (ID local: {})", blockchainVotingId, createdVotacion.getId());

                // 3. 💾 Actualizar votación con datos blockchain
                log.info("💾 Actualizando votación con datos blockchain...");
                Votacion votacionEntity = votacionRepository.findById(createdVotacion.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Votación no encontrada"));

                votacionEntity.setBlockchainTransactionHash(txHashVotacion);
                votacionEntity.setBlockchainVotingId(blockchainVotingId);
                votacionEntity.setBlockchainVerified(true);
                votacionEntity.setBlockchainVerifiedAt(LocalDateTime.now());

                // 4. 📥 Guardar cambios
                Votacion savedVotacion = votacionRepository.save(votacionEntity);
                log.info("✅ Votación actualizada con datos blockchain");

                // 5. 🔍 Verificar que existe en blockchain
                log.info("🔍 Verificando existencia en blockchain...");
                boolean votacionExiste = blockchainService.checkVotacionExistsSafe(blockchainVotingId)
                    .get(30, TimeUnit.SECONDS);

                if (!votacionExiste) {
                    log.warn("⚠️ La votación no se pudo verificar en blockchain con ID {}, pero continuamos", blockchainVotingId);
                } else {
                    log.info("✅ Verificación: La votación existe en blockchain con ID {}", blockchainVotingId);
                }

                // 6. 🎯 Crear respuesta enriquecida con información blockchain
                Map<String, Object> response = new HashMap<>();
                response.put("votacion", votacionService.convertToDto(savedVotacion));
                response.put("blockchain", Map.of(
                    "transactionHash", txHashVotacion,
                    "blockchainVotingId", blockchainVotingId,
                    "verified", true,
                    "verifiedAt", LocalDateTime.now(),
                    "networkId", blockchainService.getNetworkVersion(),
                    "contractAddress", blockchainService.getContractAddress()
                ));
                response.put("success", true);
                response.put("message", "Votación creada exitosamente en base de datos y blockchain");

                log.info("🎉 Proceso completo exitoso para votación {}", createdVotacion.getId());
                return new ResponseEntity<>(response, HttpStatus.CREATED);

            } catch (Exception blockchainError) {
                log.error("❌ Error en blockchain para votación {}: {}", createdVotacion.getId(), blockchainError.getMessage());

                // La votación ya está en BD, informamos del problema pero no fallamos completamente
                Map<String, Object> response = new HashMap<>();
                response.put("votacion", createdVotacion);
                response.put("blockchain", Map.of(
                    "error", "Error integrando con blockchain: " + blockchainError.getMessage(),
                    "verified", false,
                    "localOnly", true
                ));
                response.put("warning", "Votación creada en base de datos pero falló la integración blockchain");

                return new ResponseEntity<>(response, HttpStatus.CREATED);
            }

        } catch (EntityNotFoundException e) {
            log.error("❌ Usuario no encontrado: {}", userDetails.getId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Usuario no encontrado"));
        } catch (Exception e) {
            log.error("❌ Error general creando votación", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error creando votación: " + e.getMessage()));
        }
    }

    /**
     * Create new votacion (legacy endpoint - maintains backward compatibility)
     */
    @Operation(
        summary = "Crear nueva votación (legacy)",
        description = "Permite crear una votación solo en base de datos (sin blockchain)",
        tags = { "Votaciones", "Administración" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Votación creada correctamente",
            content = @Content(schema = @Schema(implementation = VotacionDto.class))),
        @ApiResponse(responseCode = "400", description = "Datos de la votación inválidos"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    @PostMapping("/admin/votaciones")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VotacionDto> createVotacionLegacy(
            @Parameter(description = "Datos para crear la votación", required = true)
            @Valid @RequestBody CreateVotacionRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        VotacionDto createdVotacion = votacionService.createVotacion(request, userDetails.getId());
        return new ResponseEntity<>(createdVotacion, HttpStatus.CREATED);
    }

    /**
     * Update an existing votacion - only creator or admin
     */
    @Operation(
        summary = "Actualizar votación existente",
        description = "Permite al creador de la votación o admin modificar una votación existente",
        tags = { "Votaciones" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Votación actualizada correctamente",
            content = @Content(schema = @Schema(implementation = VotacionDto.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o votación no editable"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido - no eres el creador de esta votación"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @PutMapping("/{id}")  // ✅ CORREGIDO: Sin /votaciones porque ya está en @RequestMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> updateVotacion(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id,
            @Parameter(description = "Datos actualizados de la votación", required = true)
            @Valid @RequestBody CreateVotacionRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

            Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votación no encontrada"));

            if (!permissionService.canEditVotacion(user, votacion)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos para editar esta votación. Solo el creador o un administrador pueden editarla."));
            }

            VotacionDto updatedVotacion = votacionService.updateVotacionComplete(id, request, userDetails.getId());
            return ResponseEntity.ok(updatedVotacion);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error actualizando votación {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error actualizando votación: " + e.getMessage()));
        }
    }

    /**
     * Delete a votacion - only creator or admin
     */
    @Operation(
        summary = "Eliminar votación",
        description = "Permite al creador de la votación o admin eliminar una votación",
        tags = { "Votaciones" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Votación eliminada correctamente"),
        @ApiResponse(responseCode = "400", description = "Votación no puede ser eliminada"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido - no eres el creador de esta votación"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @DeleteMapping("/votaciones/{id}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteVotacion(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

            Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votación no encontrada"));

            if (!permissionService.canDeleteVotacion(user, votacion)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos para eliminar esta votación. Solo el creador o un administrador pueden eliminarla."));
            }

            votacionService.deleteVotacionSafe(id, userDetails.getId());
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error eliminando votación {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error eliminando votación: " + e.getMessage()));
        }
    }

    /**
     * Cambiar estado de una votación - only creator or admin
     */
    @Operation(
        summary = "Cambiar estado de una votación",
        description = "Permite al creador de la votación o admin cambiar el estado de una votación (activar/finalizar)",
        tags = { "Votaciones" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado de votación actualizado correctamente",
            content = @Content(schema = @Schema(implementation = VotacionDto.class))),
        @ApiResponse(responseCode = "400", description = "Estado inválido"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido - no eres el creador de esta votación"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @PutMapping("/votaciones/{id}/status")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> changeVotacionStatus(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id,
            @Parameter(description = "Nuevo estado para la votación", required = true) @RequestParam VotacionEstado status,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        try {
            User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

            Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votación no encontrada"));

            if (!permissionService.canChangeVotacionStatus(user, votacion)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos para cambiar el estado de esta votación. Solo el creador o un administrador pueden hacerlo."));
            }

            VotacionDto updatedVotacion = votacionService.changeVotacionStatus(id, status, userDetails.getId());
            return ResponseEntity.ok(updatedVotacion);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error cambiando estado de votación {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error cambiando estado de votación: " + e.getMessage()));
        }
    }

    /**
     * Get all votaciones with filters and pagination (Admin view)
     */
    @Operation(
        summary = "Obtener todas las votaciones",
        description = "Permite listar todas las votaciones con filtros y paginación (vista administrativa)",
        tags = { "Votaciones", "Administración" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de votaciones obtenida correctamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido")
    })
    @GetMapping("/admin/votaciones")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<VotacionDto>> getAllVotaciones(
            @Parameter(description = "Número de página (empieza en 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Filtrar por estado de la votación") @RequestParam(required = false) VotacionEstado estado,
            @Parameter(description = "Filtrar por categoría") @RequestParam(required = false) VotacionCategoria categoria,
            @Parameter(description = "Buscar por texto en título o descripción") @RequestParam(required = false) String search) {

        // Usar el mismo método que las votaciones públicas pero con acceso administrativo
        Page<VotacionDto> votaciones = votacionService.getPublicVotaciones(page, size, estado, categoria, search);
        return ResponseEntity.ok(votaciones);
    }

    /**
     * Get votaciones created by the current user
     */
    @Operation(
        summary = "Obtener mis votaciones",
        description = "Devuelve las votaciones creadas por el usuario autenticado",
        tags = { "Votaciones" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de votaciones del usuario obtenida correctamente"),
        @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    @GetMapping("/mis-votaciones")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Page<VotacionDto>> getMisVotaciones(
            @Parameter(description = "Número de página (empieza en 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página") @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Page<VotacionDto> misVotaciones = votacionService.getVotacionesByCreator(userDetails.getId(), page, size);
        return ResponseEntity.ok(misVotaciones);
    }

    /**
     * Cambiar estado de una votación (Admin only)
     */
    @Operation(
        summary = "Cambiar estado de una votación (Admin)",
        description = "Permite a un administrador cambiar el estado de una votación (abierta, cerrada, etc.)",
        tags = { "Votaciones", "Administración" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado de votación actualizado correctamente",
            content = @Content(schema = @Schema(implementation = VotacionDto.class))),
        @ApiResponse(responseCode = "400", description = "Estado inválido"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @PutMapping("/admin/votaciones/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VotacionDto> changeVotacionStatusAdmin(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id,
            @Parameter(description = "Nuevo estado para la votación", required = true) @RequestParam VotacionEstado status,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        VotacionDto updatedVotacion = votacionService.changeVotacionStatus(id, status, userDetails.getId());
        return ResponseEntity.ok(updatedVotacion);
    }

    /**
     * Get comprehensive statistics for a specific votacion
     */
    @Operation(
        summary = "Obtener estadísticas completas de una votación",
        description = "Devuelve estadísticas detalladas incluyendo participación, distribución de votos, análisis blockchain y tendencias temporales",
        tags = { "Votaciones", "Estadísticas" }
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas correctamente"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/votaciones/{id}/estadisticas")
    public ResponseEntity<?> getEstadisticas(@PathVariable Long id) {
        try {
            // Verificar que la votación existe
            Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votación no encontrada con ID: " + id));

            // 📊 ESTADÍSTICAS BÁSICAS
            Map<String, Object> basicStats = new HashMap<>();
            long totalVotos = voteService.countByVotacionId(id);
            long totalUsuariosElegibles = userRepository.count(); // O implementar lógica específica

            basicStats.put("totalVotos", totalVotos);
            basicStats.put("totalUsuariosElegibles", totalUsuariosElegibles);
            basicStats.put("participacionPorcentaje",
                totalUsuariosElegibles > 0 ? (totalVotos * 100.0 / totalUsuariosElegibles) : 0.0);
            basicStats.put("votacionActiva", votacion.getEstado() == VotacionEstado.ABIERTA);

            // 🔗 ESTADÍSTICAS BLOCKCHAIN
            Map<String, Object> blockchainStats = voteService.getBlockchainStats(id);

            // 📈 DISTRIBUCIÓN POR OPCIONES
            Map<String, Long> distribucionOpciones = voteService.getVoteDistributionByOption(id);

            // Calcular porcentajes por opción
            Map<String, Object> opcionesConPorcentajes = new HashMap<>();
            distribucionOpciones.forEach((opcion, votos) -> {
                Map<String, Object> opcionData = new HashMap<>();
                opcionData.put("votos", votos);
                opcionData.put("porcentaje", totalVotos > 0 ? (votos * 100.0 / totalVotos) : 0.0);
                opcionesConPorcentajes.put(opcion, opcionData);
            });

            // ⏰ ESTADÍSTICAS TEMPORALES
            Map<LocalDateTime, Long> votosEnTiempo = voteService.getVotesOverTime(id);

            // 🏆 OPCIÓN GANADORA
            String opcionGanadora = distribucionOpciones.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("No hay votos");

            Long votosGanadora = distribucionOpciones.values().stream()
                .max(Long::compareTo)
                .orElse(0L);

            // 📍 INFORMACIÓN DE LA VOTACIÓN
            Map<String, Object> infoVotacion = new HashMap<>();
            infoVotacion.put("id", votacion.getId());
            infoVotacion.put("titulo", votacion.getTitulo());
            infoVotacion.put("descripcion", votacion.getDescripcion());
            infoVotacion.put("estado", votacion.getEstado());
            infoVotacion.put("fechaInicio", votacion.getFechaInicio());
            infoVotacion.put("fechaFin", votacion.getFechaFin());
            infoVotacion.put("categoria", votacion.getCategoria());
            infoVotacion.put("organizador", votacion.getOrganizador());
            infoVotacion.put("blockchainVotingId", votacion.getBlockchainVotingId());
            infoVotacion.put("blockchainTransactionHash", votacion.getBlockchainTransactionHash());

            // 🔍 ESTADÍSTICAS ADICIONALES
            Map<String, Object> estadisticasAdicionales = new HashMap<>();
            estadisticasAdicionales.put("promedioVotosPorHora", calcularPromedioVotosPorHora(votosEnTiempo));
            estadisticasAdicionales.put("horaConMasVotos", encontrarHoraConMasVotos(votosEnTiempo));
            estadisticasAdicionales.put("tiempoRestante", calcularTiempoRestante(votacion));
            estadisticasAdicionales.put("duracionTotal", calcularDuracionTotal(votacion));

            // 🏃‍♂️ ÚLTIMOS VOTOS (para mostrar actividad reciente)
            List<Vote> ultimosVotos = voteRepository.findLatestVotesByVotacion(id, PageRequest.of(0, 5));
            List<Map<String, Object>> actividadReciente = ultimosVotos.stream()
                .map(vote -> {
                    Map<String, Object> voteInfo = new HashMap<>();
                    voteInfo.put("timestamp", vote.getCreatedAt());
                    voteInfo.put("userHash", vote.getVoteHash().substring(0, 8) + "..."); // Privacidad
                    voteInfo.put("blockchainVerified", vote.isBlockchainVerified());
                    return voteInfo;
                })
                .collect(Collectors.toList());

            // 🎯 CONSTRUIR RESPUESTA COMPLETA
            Map<String, Object> response = new HashMap<>();
            response.put("votacion", infoVotacion);
            response.put("estadisticasBasicas", basicStats);
            response.put("blockchain", blockchainStats);
            response.put("distribucionOpciones", opcionesConPorcentajes);
            response.put("tendenciaTemporal", votosEnTiempo);
            response.put("ganador", Map.of(
                "opcion", opcionGanadora,
                "votos", votosGanadora,
                "porcentaje", totalVotos > 0 ? (votosGanadora * 100.0 / totalVotos) : 0.0
            ));
            response.put("estadisticasAdicionales", estadisticasAdicionales);
            response.put("actividadReciente", actividadReciente);
            response.put("generadoEn", LocalDateTime.now());

            log.info("✅ Estadísticas generadas para votación {} con {} votos totales", id, totalVotos);
            return ResponseEntity.ok(response);

        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error generando estadísticas para votación {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error generando estadísticas: " + e.getMessage()));
        }
    }

    // Métodos auxiliares para cálculos adicionales
    private double calcularPromedioVotosPorHora(Map<LocalDateTime, Long> votosEnTiempo) {
        if (votosEnTiempo.isEmpty()) return 0.0;
        return votosEnTiempo.values().stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
    }

    private LocalDateTime encontrarHoraConMasVotos(Map<LocalDateTime, Long> votosEnTiempo) {
        return votosEnTiempo.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private Map<String, Object> calcularTiempoRestante(Votacion votacion) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> tiempo = new HashMap<>();

        if (now.isBefore(votacion.getFechaFin())) {
            Duration restante = Duration.between(now, votacion.getFechaFin());
            tiempo.put("dias", restante.toDays());
            tiempo.put("horas", restante.toHours() % 24);
            tiempo.put("minutos", restante.toMinutes() % 60);
            tiempo.put("activa", true);
        } else {
            tiempo.put("activa", false);
            tiempo.put("finalizada", true);
        }

        return tiempo;
    }

    private Map<String, Object> calcularDuracionTotal(Votacion votacion) {
        Duration duracion = Duration.between(votacion.getFechaInicio(), votacion.getFechaFin());
        Map<String, Object> tiempo = new HashMap<>();
        tiempo.put("dias", duracion.toDays());
        tiempo.put("horas", duracion.toHours() % 24);
        return tiempo;
    }

    /**
     * Activate a votacion (change from CREADA to ABIERTA)
     */
    @Operation(
        summary = "Activar una votación",
        description = "Permite a un administrador activar una votación que está en estado CREADA",
        tags = { "Votaciones", "Administración", "Estados" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Votación activada correctamente"),
        @ApiResponse(responseCode = "400", description = "La votación no puede ser activada"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @PostMapping("/votaciones/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> activarVotacion(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            VotacionDto votacion = votacionService.activarVotacion(id, userDetails.getId());
            return ResponseEntity.ok(votacion);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error activando votación {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error activando votación: " + e.getMessage()));
        }
    }

    /**
     * Finalizar una votación - permite al creador o admin finalizar su votación
     */
    @Operation(
        summary = "Finalizar una votación",
        description = "Permite al creador de la votación o a un administrador finalizar una votación abierta. " +
                     "Calcula automáticamente los resultados finales, determina el ganador, y muestra estadísticas detalladas " +
                     "incluyendo distribución de votos, porcentajes, y datos de blockchain si aplica.",
        tags = { "Votaciones", "Gestión de Estado" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Votación finalizada correctamente con resultados completos"),
        @ApiResponse(responseCode = "400", description = "La votación no puede ser finalizada (no está abierta)"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido - solo el creador o un administrador pueden finalizar la votación"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @PostMapping("/votaciones/{id}/finalizar")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> finalizarVotacion(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            // Verificar permisos: debe ser el creador o un admin
            User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

            Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votación no encontrada"));

            if (!permissionService.canFinalizeVotacion(user, votacion)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos para finalizar esta votación. Solo el creador o un administrador pueden finalizarla."));
            }

            // Finalizar la votación y obtener resultados completos
            Map<String, Object> resultado = votacionService.finalizarVotacion(id, userDetails.getId());

            // Enriquecer la respuesta con estadísticas adicionales
            Map<String, Object> respuestaCompleta = enriquecerResultadosFinalizacion(resultado, id);

            log.info("✅ Votación {} finalizada exitosamente por usuario {}", id, userDetails.getId());
            return ResponseEntity.ok(respuestaCompleta);

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error finalizando votación {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error finalizando votación: " + e.getMessage()));
        }
    }

    /**
     * Suspend a votacion temporarily - permite al creador o admin suspender su votación
     */
    @Operation(
        summary = "Suspender una votación",
        description = "Permite al creador de la votación o a un administrador suspender temporalmente una votación activa",
        tags = { "Votaciones", "Gestión de Estado" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Votación suspendida correctamente"),
        @ApiResponse(responseCode = "400", description = "La votación no puede ser suspendida (no está abierta)"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido - solo el creador o un administrador pueden suspender la votación"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @PostMapping("/votaciones/{id}/suspender")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> suspenderVotacion(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id,
            @Parameter(description = "Motivo de la suspensión") @RequestParam(required = false) String motivo,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            // Verificar permisos: debe ser el creador o un admin
            User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

            Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votación no encontrada"));

            if (!permissionService.canSuspendVotacion(user, votacion)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos para suspender esta votación. Solo el creador o un administrador pueden suspenderla."));
            }

            VotacionDto votacionSuspendida = votacionService.suspenderVotacion(id, userDetails.getId(), motivo);
            return ResponseEntity.ok(votacionSuspendida);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error suspendiendo votación {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error suspendiendo votación: " + e.getMessage()));
        }
    }

    /**
     * Resume a suspended votacion - permite al creador o admin reanudar su votación
     */
    @Operation(
        summary = "Reanudar una votación suspendida",
        description = "Permite al creador de la votación o a un administrador reanudar una votación que fue suspendida",
        tags = { "Votaciones", "Gestión de Estado" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Votación reanudada correctamente"),
        @ApiResponse(responseCode = "400", description = "La votación no puede ser reanudada (no está suspendida o expiró)"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido - solo el creador o un administrador pueden reanudar la votación"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @PostMapping("/votaciones/{id}/reanudar")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> reanudarVotacion(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            // Verificar permisos: debe ser el creador o un admin
            User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

            Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votación no encontrada"));

            if (!permissionService.canResumeVotacion(user, votacion)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos para reanudar esta votación. Solo el creador o un administrador pueden reanudarla."));
            }

            VotacionDto votacionReanudada = votacionService.reanudarVotacion(id, userDetails.getId());
            return ResponseEntity.ok(votacionReanudada);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error reanudando votación {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error reanudando votación: " + e.getMessage()));
        }
    }

    /**
     * Cancel a votacion permanently - permite al creador o admin cancelar su votación
     */
    @Operation(
        summary = "Cancelar una votación",
        description = "Permite al creador de la votación o a un administrador cancelar permanentemente una votación",
        tags = { "Votaciones", "Gestión de Estado" }
    )
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Votación cancelada correctamente"),
        @ApiResponse(responseCode = "400", description = "La votación no puede ser cancelada (ya está finalizada)"),
        @ApiResponse(responseCode = "401", description = "No autorizado"),
        @ApiResponse(responseCode = "403", description = "Acceso prohibido - solo el creador o un administrador pueden cancelar la votación"),
        @ApiResponse(responseCode = "404", description = "Votación no encontrada")
    })
    @PostMapping("/votaciones/{id}/cancelar")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> cancelarVotacion(
            @Parameter(description = "ID de la votación", required = true) @PathVariable Long id,
            @Parameter(description = "Motivo de la cancelación") @RequestParam(required = false) String motivo,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            // Verificar permisos: debe ser el creador o un admin
            User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

            Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votación no encontrada"));

            if (!permissionService.canCancelVotacion(user, votacion)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos para cancelar esta votación. Solo el creador o un administrador pueden cancelarla."));
            }

            VotacionDto votacionCancelada = votacionService.cancelarVotacion(id, userDetails.getId(), motivo);
            return ResponseEntity.ok(votacionCancelada);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("❌ Error cancelando votación {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error cancelando votación: " + e.getMessage()));
        }
    }

    /**
     * Enriquecer los resultados de finalización con estadísticas adicionales
     */
    private Map<String, Object> enriquecerResultadosFinalizacion(Map<String, Object> resultadoBase, Long votacionId) {
        try {
            // Copiar el resultado base
            Map<String, Object> respuestaEnriquecida = new HashMap<>(resultadoBase);

            // Obtener estadísticas adicionales
            long totalVotos = voteService.countByVotacionId(votacionId);
            long totalUsuariosElegibles = userRepository.count();

            // 📊 ESTADÍSTICAS DE PARTICIPACIÓN
            Map<String, Object> estadisticasParticipacion = new HashMap<>();
            estadisticasParticipacion.put("totalVotos", totalVotos);
            estadisticasParticipacion.put("totalUsuariosElegibles", totalUsuariosElegibles);
            estadisticasParticipacion.put("participacionPorcentaje",
                totalUsuariosElegibles > 0 ? (totalVotos * 100.0 / totalUsuariosElegibles) : 0.0);

            // 📈 DISTRIBUCIÓN DETALLADA POR OPCIONES CON PORCENTAJES
            Map<String, Long> distribucionOpciones = voteService.getVoteDistributionByOption(votacionId);
            Map<String, Object> opcionesDetalladas = new HashMap<>();

            distribucionOpciones.forEach((opcion, votos) -> {
                Map<String, Object> detalleOpcion = new HashMap<>();
                detalleOpcion.put("votos", votos);
                detalleOpcion.put("porcentaje", totalVotos > 0 ?
                    Math.round((votos * 100.0 / totalVotos) * 100.0) / 100.0 : 0.0);
                opcionesDetalladas.put(opcion, detalleOpcion);
            });

            // ⏰ ESTADÍSTICAS TEMPORALES
            Map<LocalDateTime, Long> tendenciaTemporal = voteService.getVotesOverTime(votacionId);

            // 🔗 ESTADÍSTICAS BLOCKCHAIN
            Map<String, Object> blockchainStats = voteService.getBlockchainStats(votacionId);

            // 🏃‍♂️ ACTIVIDAD RECIENTE
            List<Vote> ultimosVotos = voteRepository.findLatestVotesByVotacion(votacionId, PageRequest.of(0, 10));
            List<Map<String, Object>> actividadReciente = ultimosVotos.stream()
                .map(vote -> {
                    Map<String, Object> voteInfo = new HashMap<>();
                    voteInfo.put("timestamp", vote.getCreatedAt());
                    voteInfo.put("voteHash", vote.getVoteHash().substring(0, 8) + "...");
                    voteInfo.put("blockchainVerified", vote.isBlockchainVerified());
                    voteInfo.put("status", vote.getStatus());
                    return voteInfo;
                })
                .collect(Collectors.toList());

            // 🎯 ANÁLISIS DEL GANADOR
            @SuppressWarnings("unchecked")
            Map<String, Object> resultados = (Map<String, Object>) resultadoBase.get("resultados");
            String ganador = (String) resultados.get("ganador");
            Long votosGanadora = (Long) resultados.get("votosGanadora");

            Map<String, Object> analisisGanador = new HashMap<>();
            analisisGanador.put("opcion", ganador);
            analisisGanador.put("votos", votosGanadora);
            analisisGanador.put("porcentaje", totalVotos > 0 ?
                Math.round((votosGanadora * 100.0 / totalVotos) * 100.0) / 100.0 : 0.0);
            analisisGanador.put("margenVictoria", calcularMargenVictoria(distribucionOpciones, ganador, votosGanadora));
            analisisGanador.put("esVictoriaDefinitiva", esVictoriaDefinitiva(distribucionOpciones, votosGanadora, totalVotos));

            // 📊 AGREGAR TODA LA INFORMACIÓN ENRIQUECIDA
            respuestaEnriquecida.put("estadisticasParticipacion", estadisticasParticipacion);
            respuestaEnriquecida.put("opcionesDetalladas", opcionesDetalladas);
            respuestaEnriquecida.put("tendenciaTemporal", tendenciaTemporal);
            respuestaEnriquecida.put("blockchain", blockchainStats);
            respuestaEnriquecida.put("actividadReciente", actividadReciente);
            respuestaEnriquecida.put("analisisGanador", analisisGanador);
            respuestaEnriquecida.put("finalizadoEn", LocalDateTime.now());
            respuestaEnriquecida.put("esVotacionFinalizada", true);

            return respuestaEnriquecida;

        } catch (Exception e) {
            log.warn("⚠️ Error enriqueciendo resultados de finalización: {}", e.getMessage());
            // Si hay error, devolver el resultado base sin enriquecimiento
            return resultadoBase;
        }
    }

    /**
     * Calcular el margen de victoria respecto a la segunda opción más votada
     */
    private Map<String, Object> calcularMargenVictoria(Map<String, Long> distribucion, String ganador, Long votosGanadora) {
        Map<String, Object> margen = new HashMap<>();

        // Encontrar la segunda opción más votada
        Long segundoLugar = distribucion.entrySet().stream()
            .filter(entry -> !entry.getKey().equals(ganador))
            .map(Map.Entry::getValue)
            .max(Long::compareTo)
            .orElse(0L);

        long diferencia = votosGanadora - segundoLugar;
        double porcentajeMargen = votosGanadora > 0 ? (diferencia * 100.0 / votosGanadora) : 0.0;

        margen.put("votosSegundoLugar", segundoLugar);
        margen.put("diferencia", diferencia);
        margen.put("porcentajeMargen", Math.round(porcentajeMargen * 100.0) / 100.0);

        return margen;
    }

    /**
     * Determinar si la victoria es definitiva (más del 50% de los votos)
     */
    private boolean esVictoriaDefinitiva(Map<String, Long> distribucion, Long votosGanadora, long totalVotos) {
        if (totalVotos == 0) return false;
        double porcentajeGanadora = (votosGanadora * 100.0) / totalVotos;
        return porcentajeGanadora > 50.0;
    }
}
