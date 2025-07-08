package com.votechain.backend.voting.service;

import com.votechain.backend.auth.model.User;
import com.votechain.backend.auth.repository.UserRepository;
import com.votechain.backend.common.logging.SystemLogService;
import com.votechain.backend.voting.dto.VotacionOpcionDto;
import com.votechain.backend.voting.model.Votacion;
import com.votechain.backend.voting.model.VotacionEstado;
import com.votechain.backend.voting.model.VotacionOpcion;
import com.votechain.backend.voting.repository.VotacionOpcionRepository;
import com.votechain.backend.voting.repository.VotacionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VotacionOpcionService {

    @Autowired
    private VotacionOpcionRepository opcionRepository;

    @Autowired
    private VotacionRepository votacionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SystemLogService systemLogService;

    /**
     * Get all options for a specific votacion
     */
    public List<VotacionOpcionDto> getOpcionesByVotacion(Long votacionId) {
        // Verify votacion exists
        if (!votacionRepository.existsById(votacionId)) {
            throw new EntityNotFoundException("Votacion not found with id: " + votacionId);
        }

        List<VotacionOpcion> opciones = opcionRepository.findByVotacionIdOrderByOrden(votacionId);
        return opciones.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific option by ID
     */
    public VotacionOpcionDto getOpcionById(Long id) {
        VotacionOpcion opcion = opcionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Option not found with id: " + id));

        return convertToDto(opcion);
    }

    /**
     * Create a new option for a votacion
     */
    @Transactional
    public VotacionOpcionDto createOpcion(VotacionOpcionDto request, Long userId) {
        log.info("➕ Creando nueva opción para votación {} por usuario {}", request.getVotacionId(), userId);

        // Verify votacion exists
        Votacion votacion = votacionRepository.findById(request.getVotacionId())
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + request.getVotacionId()));

        // Verify votacion is editable
        if (!votacion.getEstado().isEditable()) {
            throw new IllegalStateException("No se pueden agregar opciones a una votación en estado: " + votacion.getEstado());
        }

        // Get next order number
        long maxOrden = opcionRepository.findByVotacionIdOrderByOrden(request.getVotacionId())
                .stream()
                .mapToLong(VotacionOpcion::getOrden)
                .max()
                .orElse(0);

        // Create new option
        VotacionOpcion nuevaOpcion = VotacionOpcion.builder()
                .titulo(request.getTitulo())
                .descripcion(request.getDescripcion())
                .imagen(request.getImagen())
                .orden((int) (maxOrden + 1))
                .votacion(votacion)
                .build();

        VotacionOpcion savedOpcion = opcionRepository.save(nuevaOpcion);

        // Log the action
        systemLogService.logAdminAction(userId, "Create Option",
                String.format("Created option '%s' for votacion '%s'", savedOpcion.getTitulo(), votacion.getTitulo()));

        log.info("✅ Opción creada: ID={}, Título={}", savedOpcion.getId(), savedOpcion.getTitulo());
        return convertToDto(savedOpcion);
    }

    /**
     * Update an existing option
     */
    @Transactional
    public VotacionOpcionDto updateOpcion(Long id, VotacionOpcionDto request, Long userId) {
        log.info("✏️ Actualizando opción {} por usuario {}", id, userId);

        VotacionOpcion opcion = opcionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Option not found with id: " + id));

        // Verify votacion is editable
        if (!opcion.getVotacion().getEstado().isEditable()) {
            throw new IllegalStateException("No se pueden modificar opciones de una votación en estado: " +
                    opcion.getVotacion().getEstado());
        }

        // Update fields
        opcion.setTitulo(request.getTitulo());
        opcion.setDescripcion(request.getDescripcion());
        opcion.setImagen(request.getImagen());

        VotacionOpcion updatedOpcion = opcionRepository.save(opcion);

        // Log the action
        systemLogService.logAdminAction(userId, "Update Option",
                String.format("Updated option '%s' for votacion '%s'",
                        updatedOpcion.getTitulo(), opcion.getVotacion().getTitulo()));

        log.info("✅ Opción actualizada: ID={}, Título={}", updatedOpcion.getId(), updatedOpcion.getTitulo());
        return convertToDto(updatedOpcion);
    }

    /**
     * Delete an option
     */
    @Transactional
    public void deleteOpcion(Long id, Long userId) {
        log.info("🗑️ Eliminando opción {} por usuario {}", id, userId);

        VotacionOpcion opcion = opcionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Option not found with id: " + id));

        // Verify votacion is editable
        if (!opcion.getVotacion().getEstado().isEditable()) {
            throw new IllegalStateException("No se pueden eliminar opciones de una votación en estado: " +
                    opcion.getVotacion().getEstado());
        }

        // Verify minimum options requirement
        long totalOpciones = opcionRepository.countByVotacionId(opcion.getVotacion().getId());
        if (totalOpciones <= 2) {
            throw new IllegalStateException("No se puede eliminar la opción. La votación debe tener al menos 2 opciones.");
        }

        String titulo = opcion.getTitulo();
        String votacionTitulo = opcion.getVotacion().getTitulo();

        // Delete the option
        opcionRepository.delete(opcion);

        // Reorder remaining options
        reorderOpcionesAfterDelete(opcion.getVotacion().getId(), opcion.getOrden());

        // Log the action
        systemLogService.logAdminAction(userId, "Delete Option",
                String.format("Deleted option '%s' from votacion '%s'", titulo, votacionTitulo));

        log.info("✅ Opción eliminada: Título={}", titulo);
    }

    /**
     * Reorder options within a votacion
     */
    @Transactional
    public List<VotacionOpcionDto> reorderOpciones(Long votacionId, List<Long> opcionIds, Long userId) {
        log.info("🔄 Reordenando opciones para votación {} por usuario {}", votacionId, userId);

        // Verify votacion exists and is editable
        Votacion votacion = votacionRepository.findById(votacionId)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + votacionId));

        if (!votacion.getEstado().isEditable()) {
            throw new IllegalStateException("No se pueden reordenar opciones de una votación en estado: " + votacion.getEstado());
        }

        // Get all options for this votacion
        List<VotacionOpcion> allOpciones = opcionRepository.findByVotacionIdOrderByOrden(votacionId);

        // Verify all option IDs belong to this votacion
        List<Long> existingIds = allOpciones.stream().map(VotacionOpcion::getId).collect(Collectors.toList());
        if (!existingIds.containsAll(opcionIds) || existingIds.size() != opcionIds.size()) {
            throw new IllegalArgumentException("Lista de IDs de opciones inválida");
        }

        // Update order for each option
        for (int i = 0; i < opcionIds.size(); i++) {
            Long opcionId = opcionIds.get(i);
            VotacionOpcion opcion = allOpciones.stream()
                    .filter(o -> o.getId().equals(opcionId))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException("Option not found with id: " + opcionId));

            opcion.setOrden(i + 1);
            opcionRepository.save(opcion);
        }

        // Log the action
        systemLogService.logAdminAction(userId, "Reorder Options",
                String.format("Reordered options for votacion '%s'", votacion.getTitulo()));

        log.info("✅ Opciones reordenadas para votación {}", votacionId);

        // Return updated options
        return getOpcionesByVotacion(votacionId);
    }

    /**
     * Reorder options after one is deleted
     */
    private void reorderOpcionesAfterDelete(Long votacionId, int deletedOrder) {
        List<VotacionOpcion> opciones = opcionRepository.findByVotacionIdOrderByOrden(votacionId);

        for (VotacionOpcion opcion : opciones) {
            if (opcion.getOrden() > deletedOrder) {
                opcion.setOrden(opcion.getOrden() - 1);
                opcionRepository.save(opcion);
            }
        }
    }

    /**
     * Convert entity to DTO
     */
    private VotacionOpcionDto convertToDto(VotacionOpcion opcion) {
        return VotacionOpcionDto.builder()
                .id(opcion.getId())
                .titulo(opcion.getTitulo())
                .descripcion(opcion.getDescripcion())
                .imagen(opcion.getImagen())
                .orden(opcion.getOrden())
                .votacionId(opcion.getVotacion().getId())
                .build();
    }
}
