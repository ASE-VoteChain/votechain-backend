package com.votechain.backend.voting.dto;

import com.votechain.backend.voting.model.VotacionCategoria;
import com.votechain.backend.voting.model.VotacionEstado;
import com.votechain.backend.voting.model.VotacionPrioridad;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateVotacionRequest {

    @NotBlank(message = "Title cannot be empty")
    private String titulo;

    @NotBlank(message = "Description cannot be empty")
    private String descripcion;

    @NotNull(message = "Category cannot be null")
    private VotacionCategoria categoria;

    private VotacionEstado estado;

    private VotacionPrioridad prioridad;

    @NotNull(message = "Start date cannot be null")
    private LocalDateTime fechaInicio;

    @NotNull(message = "End date cannot be null")
    @Future(message = "End date must be in the future")
    private LocalDateTime fechaFin;

    private String ubicacion;

    private String organizador;

    private String requisitos;

    @NotNull(message = "Voting options cannot be null")
    @Size(min = 2, message = "At least 2 options are required")
    private List<VotacionOpcionDto> opciones;
}
