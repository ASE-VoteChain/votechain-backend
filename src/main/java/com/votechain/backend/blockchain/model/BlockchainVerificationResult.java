package com.votechain.backend.blockchain.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BlockchainVerificationResult {
    private boolean verified;
    private String transactionHash;
    private String blockNumber;
    private String blockHash;
    private String gasUsed;
    private LocalDateTime timestamp;
    private String error;
}
