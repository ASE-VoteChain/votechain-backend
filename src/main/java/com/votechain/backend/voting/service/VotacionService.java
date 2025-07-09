package com.votechain.backend.voting.service;

import com.votechain.backend.auth.model.User;
import com.votechain.backend.blockchain.service.BlockchainService;
import com.votechain.backend.voting.dto.CreateVotacionRequest;
import com.votechain.backend.voting.dto.VotacionDto;
import com.votechain.backend.voting.dto.VotacionOpcionDto;
import com.votechain.backend.auth.repository.UserRepository;
import com.votechain.backend.voting.repository.VotacionOpcionRepository;
import com.votechain.backend.voting.repository.VotacionRepository;
import com.votechain.backend.common.logging.SystemLogService;
import com.votechain.backend.voting.model.*;
import com.votechain.backend.vote.repository.VoteRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VotacionService {

    @Autowired
    private VotacionRepository votacionRepository;

    @Autowired
    private VotacionOpcionRepository opcionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SystemLogService systemLogService;

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private VoteRepository voteRepository;

    /**
     * Get public votaciones with pagination and filtering
     */
    public Page<VotacionDto> getPublicVotaciones(
            int page, int size, VotacionEstado estado, VotacionCategoria categoria, String searchTerm) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("fechaInicio").descending());

        Page<Votacion> votacionesPage;
        if (searchTerm != null && !searchTerm.isEmpty()) {
            votacionesPage = votacionRepository.searchVotaciones(searchTerm, estado, categoria, pageable);
        } else if (estado != null && categoria != null) {
            votacionesPage = votacionRepository.findByEstadoAndCategoria(estado, categoria, pageable);
        } else if (estado != null) {
            votacionesPage = votacionRepository.findByEstado(estado, pageable);
        } else if (categoria != null) {
            votacionesPage = votacionRepository.findByCategoria(categoria, pageable);
        } else {
            votacionesPage = votacionRepository.findAll(pageable);
        }

        return votacionesPage.map(this::convertToDto);
    }

    /**
     * Get active votaciones
     */
    public Page<VotacionDto> getActiveVotaciones(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return votacionRepository.findActiveVotaciones(LocalDateTime.now(), pageable)
                .map(this::convertToDto);
    }

    /**
     * Get votaciones for a specific user, including participation status
     */
    public Page<VotacionDto> getUserVotaciones(
            Long userId, int page, int size, VotacionEstado estado,
            VotacionCategoria categoria, Boolean participated) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("fechaInicio").descending());

        Page<Votacion> votacionesPage;
        if (participated != null && participated) {
            votacionesPage = votacionRepository.findVotacionesByUserId(userId, pageable);
        } else {
            // Use the same filtering logic as public votaciones
            if (estado != null && categoria != null) {
                votacionesPage = votacionRepository.findByEstadoAndCategoria(estado, categoria, pageable);
            } else if (estado != null) {
                votacionesPage = votacionRepository.findByEstado(estado, pageable);
            } else if (categoria != null) {
                votacionesPage = votacionRepository.findByCategoria(categoria, pageable);
            } else {
                votacionesPage = votacionRepository.findAll(pageable);
            }
        }

        return votacionesPage.map(votacion -> {
            VotacionDto dto = convertToDto(votacion);
            // Set user-specific participation status
            dto.setHasParticipated(hasUserVoted(userId, votacion.getId()));
            return dto;
        });
    }

    /**
     * Get votacion by ID
     */
    public VotacionDto getVotacionById(Long id) {
        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        return convertToDto(votacion);
    }

    /**
     * Get detailed votacion for user view
     */
    public VotacionDto getUserVotacionDetail(Long userId, Long votacionId) {
        Votacion votacion = votacionRepository.findById(votacionId)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + votacionId));

        VotacionDto dto = convertToDto(votacion);
        dto.setHasParticipated(hasUserVoted(userId, votacionId));

        return dto;
    }

    /**
     * Create new votacion
     */
    @Transactional
    public VotacionDto createVotacion(CreateVotacionRequest request, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + creatorId));

        Votacion votacion = Votacion.builder()
                .titulo(request.getTitulo())
                .descripcion(request.getDescripcion())
                .categoria(request.getCategoria())
                .estado(request.getEstado() != null ? request.getEstado() : VotacionEstado.PROXIMA)
                .prioridad(request.getPrioridad() != null ? request.getPrioridad() : VotacionPrioridad.MEDIA)
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .ubicacion(request.getUbicacion())
                .organizador(request.getOrganizador())
                .requisitos(request.getRequisitos())
                .creador(creator)
                .build();

        Votacion savedVotacion = votacionRepository.save(votacion);

        // Create options
        if (request.getOpciones() != null) {
            for (int i = 0; i < request.getOpciones().size(); i++) {
                VotacionOpcionDto opcionDto = request.getOpciones().get(i);

                VotacionOpcion opcion = VotacionOpcion.builder()
                        .titulo(opcionDto.getTitulo())
                        .descripcion(opcionDto.getDescripcion())
                        .imagen(opcionDto.getImagen())
                        .orden(i + 1)
                        .votacion(savedVotacion)
                        .build();

                opcionRepository.save(opcion);
            }
        }

        systemLogService.logAdminAction(creatorId, "Create Votacion",
                "Created new votacion: " + savedVotacion.getTitulo());

        return convertToDto(savedVotacion);
    }

    /**
     * Create new votacion with full blockchain integration (synchronous)
     */
    @Transactional
    public VotacionDto createVotacionWithBlockchain(CreateVotacionRequest request, Long creatorId) {
        log.info("🗳️ Iniciando creación de votación con integración blockchain para usuario {}", creatorId);

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + creatorId));

        // 1. Create votacion in database first
        Votacion votacion = Votacion.builder()
                .titulo(request.getTitulo())
                .descripcion(request.getDescripcion())
                .categoria(request.getCategoria())
                .estado(request.getEstado() != null ? request.getEstado() : VotacionEstado.PROXIMA)
                .prioridad(request.getPrioridad() != null ? request.getPrioridad() : VotacionPrioridad.MEDIA)
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .ubicacion(request.getUbicacion())
                .organizador(request.getOrganizador())
                .requisitos(request.getRequisitos())
                .creador(creator)
                .build();

        Votacion savedVotacion = votacionRepository.save(votacion);
        log.info("✅ Votación creada en base de datos: ID={}, Título={}", savedVotacion.getId(), savedVotacion.getTitulo());

        // 2. Create options in database
        if (request.getOpciones() != null) {
            for (int i = 0; i < request.getOpciones().size(); i++) {
                VotacionOpcionDto opcionDto = request.getOpciones().get(i);

                VotacionOpcion opcion = VotacionOpcion.builder()
                        .titulo(opcionDto.getTitulo())
                        .descripcion(opcionDto.getDescripcion())
                        .imagen(opcionDto.getImagen())
                        .orden(i + 1)
                        .votacion(savedVotacion)
                        .build();

                opcionRepository.save(opcion);
                log.info("✅ Opción creada: ID={}, Título={}", opcion.getId(), opcion.getTitulo());
            }
        }

        // 3. Create votacion in blockchain synchronously
        try {
            log.info("🔗 Creando votación en blockchain...");
            BlockchainService.VotingCreationResult result = blockchainService.createVotacionInBlockchain(
                savedVotacion.getId(),
                savedVotacion.getTitulo(),
                savedVotacion.getFechaInicio(),
                savedVotacion.getFechaFin()
            ).get(30, TimeUnit.SECONDS);

            String txHashVotacion = result.getTransactionHash();
            Long blockchainVotingId = result.getBlockchainVotingId();

            log.info("✅ Votación creada en blockchain con hash: {}", txHashVotacion);
            log.info("🔑 ID real en blockchain: {} (ID local: {})", blockchainVotingId, savedVotacion.getId());

            // 4. Update votacion with blockchain information
            savedVotacion.setBlockchainTransactionHash(txHashVotacion);
            savedVotacion.setBlockchainVotingId(blockchainVotingId);
            savedVotacion.setBlockchainVerified(true);
            savedVotacion.setBlockchainVerifiedAt(LocalDateTime.now());

            // Save updated votacion
            savedVotacion = votacionRepository.save(savedVotacion);

            // 5. Verify that the votacion exists in blockchain
            log.info("🔍 Verificando votación en blockchain...");
            boolean votacionExiste = blockchainService.checkVotacionExistsSafe(blockchainVotingId)
                .get(30, TimeUnit.SECONDS);

            if (votacionExiste) {
                log.info("✅ Verificación: La votación existe en blockchain con ID {}", blockchainVotingId);
            } else {
                log.warn("⚠️ La votación no se pudo verificar en blockchain con ID {}", blockchainVotingId);
            }

            // 6. Log blockchain interaction
            systemLogService.logBlockchainInteraction(creatorId, "Voting Creation", txHashVotacion);
            systemLogService.logAdminAction(creatorId, "Create Votacion with Blockchain",
                    "Created new votacion with blockchain: " + savedVotacion.getTitulo());

            log.info("✅ Proceso de creación de votación completado exitosamente");

        } catch (Exception e) {
            log.error("❌ Error al crear votación en blockchain", e);

            // Mark votacion as blockchain failed but keep in database
            savedVotacion.setBlockchainVerified(false);
            savedVotacion.setBlockchainError(e.getMessage());
            votacionRepository.save(savedVotacion);

            systemLogService.logError("Blockchain Voting Creation",
                    "Error creating votacion on blockchain: " + e.getMessage());

            throw new RuntimeException("Error creating votacion on blockchain: " + e.getMessage(), e);
        }

        return convertToDto(savedVotacion);
    }

    /**
     * Update votacion
     */
    @Transactional
    public VotacionDto updateVotacion(Long id, CreateVotacionRequest request, Long userId) {
        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        // Update basic info
        votacion.setTitulo(request.getTitulo());
        votacion.setDescripcion(request.getDescripcion());
        votacion.setCategoria(request.getCategoria());
        votacion.setEstado(request.getEstado());
        votacion.setPrioridad(request.getPrioridad());
        votacion.setFechaInicio(request.getFechaInicio());
        votacion.setFechaFin(request.getFechaFin());
        votacion.setUbicacion(request.getUbicacion());
        votacion.setOrganizador(request.getOrganizador());
        votacion.setRequisitos(request.getRequisitos());

        Votacion savedVotacion = votacionRepository.save(votacion);

        // Update options - first remove existing ones
        opcionRepository.deleteByVotacionId(id);

        // Then add new ones
        if (request.getOpciones() != null) {
            for (int i = 0; i < request.getOpciones().size(); i++) {
                VotacionOpcionDto opcionDto = request.getOpciones().get(i);

                VotacionOpcion opcion = VotacionOpcion.builder()
                        .titulo(opcionDto.getTitulo())
                        .descripcion(opcionDto.getDescripcion())
                        .imagen(opcionDto.getImagen())
                        .orden(i + 1)
                        .votacion(savedVotacion)
                        .build();

                opcionRepository.save(opcion);
            }
        }

        systemLogService.logAdminAction(userId, "Update Votacion",
                "Updated votacion: " + savedVotacion.getTitulo());

        return convertToDto(savedVotacion);
    }

    /**
     * Create votacion with enhanced validation (only if editable)
     */
    @Transactional
    public VotacionDto updateVotacionComplete(Long id, CreateVotacionRequest request, Long userId) {
        log.info("✏️ Actualizando votación completa {} por usuario {}", id, userId);

        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        // Verificar que la votación sea editable
        if (!votacion.getEstado().isEditable()) {
            throw new IllegalStateException("No se puede modificar una votación en estado: " + votacion.getEstado());
        }

        // Update basic info
        votacion.setTitulo(request.getTitulo());
        votacion.setDescripcion(request.getDescripcion());
        votacion.setCategoria(request.getCategoria());
        votacion.setEstado(request.getEstado());
        votacion.setPrioridad(request.getPrioridad());
        votacion.setFechaInicio(request.getFechaInicio());
        votacion.setFechaFin(request.getFechaFin());
        votacion.setUbicacion(request.getUbicacion());
        votacion.setOrganizador(request.getOrganizador());
        votacion.setRequisitos(request.getRequisitos());

        Votacion savedVotacion = votacionRepository.save(votacion);

        // Update options - first remove existing ones
        opcionRepository.deleteByVotacionId(id);

        // Then add new ones
        if (request.getOpciones() != null) {
            for (int i = 0; i < request.getOpciones().size(); i++) {
                VotacionOpcionDto opcionDto = request.getOpciones().get(i);

                VotacionOpcion opcion = VotacionOpcion.builder()
                        .titulo(opcionDto.getTitulo())
                        .descripcion(opcionDto.getDescripcion())
                        .imagen(opcionDto.getImagen())
                        .orden(i + 1)
                        .votacion(savedVotacion)
                        .build();

                opcionRepository.save(opcion);
            }
        }

        systemLogService.logAdminAction(userId, "Update Votacion Complete",
                "Completely updated votacion: " + savedVotacion.getTitulo());

        log.info("✅ Votación actualizada completamente: ID={}", id);
        return convertToDto(savedVotacion);
    }

    /**
     * Delete votacion
     */
    @Transactional
    public void deleteVotacion(Long id, Long userId) {
        if (!votacionRepository.existsById(id)) {
            throw new EntityNotFoundException("Votacion not found with id: " + id);
        }

        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        // Delete options first
        opcionRepository.deleteByVotacionId(id);

        // Delete votacion
        votacionRepository.deleteById(id);

        systemLogService.logAdminAction(userId, "Delete Votacion",
                "Deleted votacion: " + votacion.getTitulo());
    }

    /**
     * Delete votacion with enhanced validation (only if in CREADA state)
     */
    @Transactional
    public void deleteVotacionSafe(Long id, Long userId) {
        log.info("🗑️ Eliminando votación {} por usuario {}", id, userId);

        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        // Solo permitir eliminar votaciones en estado CREADA
        if (votacion.getEstado() != VotacionEstado.CREADA) {
            throw new IllegalStateException("Solo se pueden eliminar votaciones en estado CREADA. Estado actual: " + votacion.getEstado());
        }

        String titulo = votacion.getTitulo();

        // Delete options first
        opcionRepository.deleteByVotacionId(id);

        // Delete votacion
        votacionRepository.deleteById(id);

        systemLogService.logAdminAction(userId, "Delete Votacion Safe",
                "Safely deleted votacion: " + titulo);

        log.info("✅ Votación eliminada de forma segura: {}", titulo);
    }

    /**
     * Get votaciones created by a specific user
     */
    public Page<VotacionDto> getVotacionesByCreator(Long creatorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Votacion> votaciones = votacionRepository.findByCreadorId(creatorId, pageable);
        return votaciones.map(this::convertToDto);
    }

    /**
     * Change votacion status
     */
    public VotacionDto changeVotacionStatus(Long id, VotacionEstado newStatus, Long userId) {
        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        VotacionEstado oldStatus = votacion.getEstado();
        votacion.setEstado(newStatus);

        votacionRepository.save(votacion);

        systemLogService.logAdminAction(userId, "Change Votacion Status",
                "Changed votacion status: " + votacion.getTitulo() +
                " from " + oldStatus + " to " + newStatus);

        return convertToDto(votacion);
    }

    /**
     * Activate a votacion (change from CREADA to ABIERTA)
     */
    @Transactional
    public VotacionDto activarVotacion(Long id, Long userId) {
        log.info("🚀 Activando votación {} por usuario {}", id, userId);

        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        // Validar que puede ser activada
        if (votacion.getEstado() != VotacionEstado.CREADA) {
            throw new IllegalStateException("Solo se pueden activar votaciones en estado CREADA. Estado actual: " + votacion.getEstado());
        }

        // Verificar que tenga opciones suficientes
        long opcionesCount = opcionRepository.countByVotacionId(id);
        if (opcionesCount < 2) {
            throw new IllegalStateException("La votación debe tener al menos 2 opciones para ser activada. Opciones actuales: " + opcionesCount);
        }

        // Verificar que las fechas sean válidas
        LocalDateTime now = LocalDateTime.now();
        if (votacion.getFechaInicio().isAfter(votacion.getFechaFin())) {
            throw new IllegalStateException("La fecha de inicio debe ser anterior a la fecha de fin");
        }

        // Activar votación
        votacion.setEstado(VotacionEstado.ABIERTA);
        votacion.setFechaActivacion(now);

        Votacion saved = votacionRepository.save(votacion);

        systemLogService.logAdminAction(userId, "Activate Votacion",
                "Activated votacion: " + saved.getTitulo());

        log.info("✅ Votación {} activada exitosamente por usuario {}", id, userId);
        return convertToDto(saved);
    }

    /**
     * Finalize a votacion (change from ABIERTA to CERRADA)
     */
    @Transactional
    public Map<String, Object> finalizarVotacion(Long id, Long userId) {
        log.info("🏁 Finalizando votación {} por usuario {}", id, userId);

        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        // Validar que puede ser finalizada
        if (votacion.getEstado() != VotacionEstado.ABIERTA) {
            throw new IllegalStateException("Solo se pueden finalizar votaciones abiertas. Estado actual: " + votacion.getEstado());
        }

        // 📊 CALCULAR RESULTADOS FINALES DETALLADOS
        Map<String, Long> distribucion = getVoteDistributionByOption(id);
        List<VotacionOpcion> opciones = opcionRepository.findByVotacionId(id);

        // Calcular estadísticas detalladas
        long totalVotos = distribucion.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        // Encontrar ganador(es) - puede haber empate
        Long maxVotos = distribucion.values().stream()
                .max(Long::compareTo)
                .orElse(0L);

        List<String> ganadoras = distribucion.entrySet().stream()
                .filter(entry -> entry.getValue().equals(maxVotos))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        String opcionGanadora = ganadoras.size() == 1 ? ganadoras.get(0) :
                "Empate entre: " + String.join(", ", ganadoras);

        // Calcular porcentajes
        Map<String, Double> porcentajes = new HashMap<>();
        distribucion.forEach((opcion, votos) -> {
            double porcentaje = totalVotos > 0 ? (votos * 100.0) / totalVotos : 0.0;
            porcentajes.put(opcion, Math.round(porcentaje * 100.0) / 100.0); // Redondear a 2 decimales
        });

        // Estadísticas adicionales
        double participacionPromedio = totalVotos > 0 ? (double) totalVotos / opciones.size() : 0.0;
        boolean hayEmpate = ganadoras.size() > 1;

        log.info("📊 Resultados calculados: Ganador(es)={} con {} votos de {} totales ({}%)",
                opcionGanadora, maxVotos, totalVotos,
                totalVotos > 0 ? String.format("%.2f", (maxVotos * 100.0) / totalVotos) : "0");

        // 🔗 FINALIZAR EN BLOCKCHAIN
        String finalizeHash = null;
        boolean blockchainSuccess = false;
        try {
            if (votacion.getBlockchainVotingId() != null) {
                log.info("🔗 Finalizando votación en blockchain...");
                finalizeHash = blockchainService.finalizeVoting(votacion.getBlockchainVotingId())
                        .get(30, TimeUnit.SECONDS);

                votacion.setBlockchainFinalizeHash(finalizeHash);
                blockchainSuccess = true;
                log.info("✅ Votación {} finalizada en blockchain con hash: {}", id, finalizeHash);
            } else {
                log.warn("⚠️ Votación {} no tiene ID de blockchain, finalizando solo en BD", id);
            }
        } catch (Exception e) {
            log.warn("⚠️ Error finalizando en blockchain: {}", e.getMessage());
            // Continuar con la finalización en BD aunque falle blockchain
        }

        // Actualizar estado y resultados
        LocalDateTime fechaFinalizacion = LocalDateTime.now();
        votacion.setEstado(VotacionEstado.CERRADA);
        votacion.setFechaFinalizacion(fechaFinalizacion);
        votacion.setResultadoFinal(opcionGanadora);
        votacion.setVotosGanadora(maxVotos);

        Votacion saved = votacionRepository.save(votacion);

        // Log de la acción
        systemLogService.logAdminAction(userId, "Finalize Votacion",
                String.format("Finalized votacion: %s. Winner: %s with %d votes of %d total votes",
                        saved.getTitulo(), opcionGanadora, maxVotos, totalVotos));

        // 📊 CONSTRUIR RESPUESTA COMPLETA CON RESULTADOS DETALLADOS
        Map<String, Object> response = new HashMap<>();
        response.put("votacion", convertToDto(saved));

        // Resultados detallados
        Map<String, Object> resultados = new HashMap<>();
        resultados.put("ganador", opcionGanadora);
        resultados.put("votosGanadora", maxVotos);
        resultados.put("hayEmpate", hayEmpate);
        resultados.put("ganadoras", ganadoras);
        resultados.put("distribucionVotos", distribucion);
        resultados.put("distribucionPorcentajes", porcentajes);
        resultados.put("totalVotos", totalVotos);
        resultados.put("totalOpciones", opciones.size());
        resultados.put("participacionPromedio", Math.round(participacionPromedio * 100.0) / 100.0);
        resultados.put("fechaFinalizacion", fechaFinalizacion);

        // Información blockchain
        Map<String, Object> blockchain = new HashMap<>();
        blockchain.put("finalizeHash", finalizeHash);
        blockchain.put("blockchainSuccess", blockchainSuccess);
        blockchain.put("blockchainVotingId", votacion.getBlockchainVotingId());
        blockchain.put("verified", votacion.isBlockchainVerified());
        resultados.put("blockchain", blockchain);

        // Duración de la votación
        if (votacion.getFechaInicio() != null) {
            long duracionHoras = java.time.Duration.between(votacion.getFechaInicio(), fechaFinalizacion).toHours();
            resultados.put("duracionHoras", duracionHoras);
        }

        response.put("resultados", resultados);
        response.put("success", true);
        response.put("message", hayEmpate ?
                "Votación finalizada con empate entre " + ganadoras.size() + " opciones" :
                "Votación finalizada exitosamente");

        log.info("🏆 Votación {} finalizada exitosamente. Ganador: {} con {} votos de {} totales ({}% participación)",
                id, opcionGanadora, maxVotos, totalVotos,
                totalVotos > 0 && opciones.size() > 0 ?
                String.format("%.1f", (totalVotos * 100.0) / opciones.size()) : "N/A");

        return response;
    }

    /**
     * Suspend a votacion temporarily
     */
    @Transactional
    public VotacionDto suspenderVotacion(Long id, Long userId, String motivo) {
        log.info("⏸️ Suspendiendo votación {} por usuario {}", id, userId);

        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        // Validar que puede ser suspendida
        if (votacion.getEstado() != VotacionEstado.ABIERTA) {
            throw new IllegalStateException("Solo se pueden suspender votaciones abiertas. Estado actual: " + votacion.getEstado());
        }

        votacion.setEstado(VotacionEstado.SUSPENDIDA);
        Votacion saved = votacionRepository.save(votacion);

        String logMessage = "Suspended votacion: " + saved.getTitulo();
        if (motivo != null && !motivo.trim().isEmpty()) {
            logMessage += ". Motivo: " + motivo;
        }

        systemLogService.logAdminAction(userId, "Suspend Votacion", logMessage);

        log.info("⏸️ Votación {} suspendida por usuario {}", id, userId);
        return convertToDto(saved);
    }

    /**
     * Resume a suspended votacion
     */
    @Transactional
    public VotacionDto reanudarVotacion(Long id, Long userId) {
        log.info("▶️ Reanudando votación {} por usuario {}", id, userId);

        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        // Validar que puede ser reanudada
        if (votacion.getEstado() != VotacionEstado.SUSPENDIDA) {
            throw new IllegalStateException("Solo se pueden reanudar votaciones suspendidas. Estado actual: " + votacion.getEstado());
        }

        // Verificar que aún esté dentro del período de votación
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(votacion.getFechaFin())) {
            throw new IllegalStateException("No se puede reanudar una votación cuyo período ha expirado");
        }

        votacion.setEstado(VotacionEstado.ABIERTA);
        Votacion saved = votacionRepository.save(votacion);

        systemLogService.logAdminAction(userId, "Resume Votacion",
                "Resumed votacion: " + saved.getTitulo());

        log.info("▶️ Votación {} reanudada por usuario {}", id, userId);
        return convertToDto(saved);
    }

    /**
     * Cancel a votacion permanently
     */
    @Transactional
    public VotacionDto cancelarVotacion(Long id, Long userId, String motivo) {
        log.info("❌ Cancelando votación {} por usuario {}", id, userId);

        Votacion votacion = votacionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + id));

        // Validar que puede ser cancelada
        if (votacion.getEstado().isFinalized()) {
            throw new IllegalStateException("No se puede cancelar una votación ya finalizada. Estado actual: " + votacion.getEstado());
        }

        votacion.setEstado(VotacionEstado.CANCELADA);
        votacion.setFechaFinalizacion(LocalDateTime.now());

        Votacion saved = votacionRepository.save(votacion);

        String logMessage = "Cancelled votacion: " + saved.getTitulo();
        if (motivo != null && !motivo.trim().isEmpty()) {
            logMessage += ". Motivo: " + motivo;
        }

        systemLogService.logAdminAction(userId, "Cancel Votacion", logMessage);

        log.info("❌ Votación {} cancelada por usuario {}", id, userId);
        return convertToDto(saved);
    }

    /**
     * Check if a user has voted in a specific votacion
     */
    public boolean hasUserVoted(Long userId, Long votacionId) {
        return votacionRepository.hasUserVotedInVotacion(votacionId, userId);
    }

    /**
     * Convert entity to DTO
     */
    public VotacionDto convertToDto(Votacion votacion) {
        List<VotacionOpcion> opciones = opcionRepository.findByVotacionIdOrderByOrden(votacion.getId());

        // Calcular el total de votos de la votación
        long totalVotosVotacion = voteRepository.countByVotacionId(votacion.getId());

        List<VotacionOpcionDto> opcionesDto = opciones.stream()
                .map(opcion -> {
                    // Contar votos por esta opción específica
                    long votosOpcion = voteRepository.countByVotacionIdAndOpcionSeleccionadaId(
                        votacion.getId(), opcion.getId());

                    // Calcular porcentaje
                    Double porcentaje = null;
                    if (totalVotosVotacion > 0) {
                        porcentaje = (double) votosOpcion / totalVotosVotacion * 100.0;
                        // Redondear a 2 decimales
                        porcentaje = Math.round(porcentaje * 100.0) / 100.0;
                    }

                    return VotacionOpcionDto.builder()
                            .id((long) opcion.getOrden()) // ✅ USAR ORDEN como ID público (1, 2, 3...)
                            .votacionId(opcion.getVotacion().getId())
                            .titulo(opcion.getTitulo())
                            .descripcion(opcion.getDescripcion())
                            .imagen(opcion.getImagen())
                            .orden(opcion.getOrden())
                            .totalVotos((int) votosOpcion) // Agregar total de votos
                            .porcentaje(porcentaje) // Agregar porcentaje
                            .build();
                })
                .collect(Collectors.toList());

        return VotacionDto.builder()
                .id(votacion.getId())
                .titulo(votacion.getTitulo())
                .descripcion(votacion.getDescripcion())
                .categoria(votacion.getCategoria())
                .estado(votacion.getEstado())
                .prioridad(votacion.getPrioridad())
                .fechaInicio(votacion.getFechaInicio())
                .fechaFin(votacion.getFechaFin())
                .ubicacion(votacion.getUbicacion())
                .organizador(votacion.getOrganizador())
                .requisitos(votacion.getRequisitos())
                .creadorId(votacion.getCreador().getId())
                .creadorNombre(votacion.getCreador().getFullName())
                .opciones(opcionesDto)
                .totalVotos((int) totalVotosVotacion) // Usar el total calculado correctamente
                .blockchainTransactionHash(votacion.getBlockchainTransactionHash())
                .createdAt(votacion.getCreatedAt())
                .updatedAt(votacion.getUpdatedAt())
                .build();
    }

    /**
     * Get vote distribution by option for a specific votacion
     */
    private Map<String, Long> getVoteDistributionByOption(Long votacionId) {
        List<VotacionOpcion> opciones = opcionRepository.findByVotacionIdOrderByOrden(votacionId);
        Map<String, Long> distribution = new HashMap<>();

        for (VotacionOpcion opcion : opciones) {
            // Usar el VoteRepository para contar los votos reales por opción
            long voteCount = voteRepository.countByVotacionIdAndOpcionSeleccionadaId(votacionId, opcion.getId());
            distribution.put(opcion.getTitulo(), voteCount);
        }

        return distribution;
    }
}
