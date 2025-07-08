package com.votechain.backend.vote.dto;

import com.votechain.backend.vote.model.VoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteDto {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private Long votacionId;
    private String votacionTitulo;
    private Long opcionId;
    private String opcionTitulo;
    private LocalDateTime createdAt;
    private String voteHash;
    private String blockchainTransactionHash;
    private String blockchainStatus; // PENDING, CONFIRMED, VERIFIED, FAILED
    private LocalDateTime blockchainVerifiedAt;
    private VoteStatus status;
}
