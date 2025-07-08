package com.votechain.backend.voting.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "votacion_opciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VotacionOpcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    private String imagen;

    @NotNull
    private Integer orden;

    @ManyToOne
    @JoinColumn(name = "votacion_id")
    @JsonIgnore
    private Votacion votacion;

    // Helper method to calculate the percentage of votes for this option
    public double getPorcentajeVotos() {
        if (votacion == null || votacion.getVotos().isEmpty()) {
            return 0.0;
        }

        long votosParaEstaOpcion = votacion.getVotos().stream()
                .filter(voto -> voto.getOpcionSeleccionada() != null &&
                       voto.getOpcionSeleccionada().getId().equals(this.id))
                .count();

        return 100.0 * votosParaEstaOpcion / votacion.getVotos().size();
    }
}
