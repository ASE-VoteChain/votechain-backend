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
public class VoteVerificationDto {
    private boolean verified;
    private Long votacionId;
    private String votacionTitulo;
    private LocalDateTime timestamp;
    private String blockchainTransactionHash;
    private String blockNumber;
    private String blockHash;
    private LocalDateTime blockTimestamp;
    private String message;
}
