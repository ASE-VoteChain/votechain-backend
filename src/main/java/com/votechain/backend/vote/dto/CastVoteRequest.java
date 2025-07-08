package com.votechain.backend.vote.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CastVoteRequest {
    @NotNull(message = "Votacion ID is required")
    private Long votacionId;

    @NotNull(message = "Option ID is required")
    private Long opcionId;

    // Optional fields for additional security measures
    private String verificationToken;
    private String deviceFingerprint;
}
