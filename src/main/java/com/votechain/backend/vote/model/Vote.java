package com.votechain.backend.vote.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.votechain.backend.auth.model.User;
import com.votechain.backend.voting.model.Votacion;
import com.votechain.backend.voting.model.VotacionOpcion;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "votes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @NotNull
    private User user;

    @ManyToOne
    @JoinColumn(name = "votacion_id")
    @NotNull
    private Votacion votacion;

    @ManyToOne
    @JoinColumn(name = "opcion_id")
    @NotNull
    private VotacionOpcion opcionSeleccionada;

    // Unique hash for vote verification
    @Column(unique = true)
    private String voteHash;

    // Blockchain transaction hash
    private String blockchainTransactionHash;

    @Column(columnDefinition = "TEXT")
    private String blockchainMetadata;

    @Enumerated(EnumType.STRING)
    private VoteStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // IP address of voter (encrypted)
    @JsonIgnore
    private String ipAddress;

    // Flag to indicate if vote is verified in blockchain
    private boolean blockchainVerified = false;

    // Timestamp when the vote was verified on the blockchain
    private LocalDateTime blockchainVerifiedAt;
}
