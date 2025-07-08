package com.votechain.backend.vote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteVerificationStatus {

    private Long userId;
    private Long votacionId;
    private boolean hasVotedInDB;
    private Boolean hasVotedInBlockchain; // Nullable in case of blockchain error
    private Long blockchainVotingId;
    private String blockchainError;
    private boolean isConsistent;
    private LocalDateTime verifiedAt;

    // Helper methods
    public String getStatusMessage() {
        if (blockchainError != null) {
            return "Error verificando blockchain: " + blockchainError;
        }

        if (hasVotedInBlockchain == null) {
            return hasVotedInDB ? "Votó en BD (blockchain no verificado)" : "No ha votado";
        }

        if (isConsistent) {
            return hasVotedInDB ? "Ya votó (verificado en BD y blockchain)" : "No ha votado (verificado en BD y blockchain)";
        } else {
            return String.format("INCONSISTENCIA: BD=%s, Blockchain=%s", hasVotedInDB, hasVotedInBlockchain);
        }
    }

    public String getVerificationLevel() {
        if (blockchainError != null) {
            return "DATABASE_ONLY";
        }

        if (hasVotedInBlockchain == null) {
            return "DATABASE_ONLY";
        }

        return isConsistent ? "FULL_VERIFICATION" : "INCONSISTENT";
    }
}
