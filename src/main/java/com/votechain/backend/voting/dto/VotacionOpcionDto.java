package com.votechain.backend.voting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VotacionOpcionDto {
    private Long id;
    private Long votacionId;  // Campo que faltaba
    private String titulo;
    private String descripcion;
    private String imagen;
    private Integer orden;
    private Double porcentaje;
    private Integer totalVotos;
}
