package com.votechain.backend.voting.model;

import com.votechain.backend.auth.model.User;
import com.votechain.backend.vote.model.Vote;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "votaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Votacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    private VotacionCategoria categoria;

    @Enumerated(EnumType.STRING)
    private VotacionEstado estado;

    @Enumerated(EnumType.STRING)
    private VotacionPrioridad prioridad;

    @NotNull
    private LocalDateTime fechaInicio;

    @NotNull
    private LocalDateTime fechaFin;

    private String ubicacion;
    private String organizador;

    @Column(columnDefinition = "TEXT")
    private String requisitos;

    @OneToMany(mappedBy = "votacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VotacionOpcion> opciones = new ArrayList<>();

    @OneToMany(mappedBy = "votacion", cascade = CascadeType.ALL)
    private List<Vote> votos = new ArrayList<>();

    @NotNull
    @ManyToOne
    @JoinColumn(name = "creador_id")
    private User creador;

    // Blockchain transaction hash for the voting creation
    private String blockchainTransactionHash;

    @Column(name = "blockchain_voting_id")
    private Long blockchainVotingId;

    @Column(name = "blockchain_verified")
    private boolean blockchainVerified = false;

    @Column(name = "blockchain_verified_at")
    private LocalDateTime blockchainVerifiedAt;

    @Column(name = "blockchain_error", columnDefinition = "TEXT")
    private String blockchainError;

    // Campos adicionales para gestión de estados
    @Column(name = "fecha_activacion")
    private LocalDateTime fechaActivacion;

    @Column(name = "fecha_finalizacion")
    private LocalDateTime fechaFinalizacion;

    @Column(name = "resultado_final")
    private String resultadoFinal;

    @Column(name = "votos_ganadora")
    private Long votosGanadora;

    @Column(name = "blockchain_finalize_hash")
    private String blockchainFinalizeHash;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Helper method to add an option
    public void addOpcion(VotacionOpcion opcion) {
        opciones.add(opcion);
        opcion.setVotacion(this);
    }

    // Helper method to remove an option
    public void removeOpcion(VotacionOpcion opcion) {
        opciones.remove(opcion);
        opcion.setVotacion(null);
    }

    // Helper method to check if voting is active
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return estado == VotacionEstado.ABIERTA &&
               now.isAfter(fechaInicio) &&
               now.isBefore(fechaFin);
    }

    // Helper method to get participation percentage
    public double getParticipacionPorcentaje() {
        // Implementation would depend on total eligible voters vs. actual votes
        if (votos.isEmpty()) {
            return 0.0;
        }
        // This is a placeholder - actual implementation would need a way to know total eligible voters
        return 100.0 * votos.size() / 100.0; // Assuming 100 eligible voters for simplicity
    }
}
