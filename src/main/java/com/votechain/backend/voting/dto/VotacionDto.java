package com.votechain.backend.voting.dto;

import com.votechain.backend.voting.model.VotacionCategoria;
import com.votechain.backend.voting.model.VotacionEstado;
import com.votechain.backend.voting.model.VotacionPrioridad;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VotacionDto {
    private Long id;
    private String titulo;
    private String descripcion;
    private VotacionCategoria categoria;
    private VotacionEstado estado;
    private VotacionPrioridad prioridad;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String ubicacion;
    private String organizador;
    private String requisitos;
    private Long creadorId;
    private String creadorNombre;
    private List<VotacionOpcionDto> opciones;
    private int totalVotos;
    private String blockchainTransactionHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // User-specific data
    private boolean hasParticipated;
    private Long selectedOptionId;
    private LocalDateTime participationDate;
    private String voteHash;
}
