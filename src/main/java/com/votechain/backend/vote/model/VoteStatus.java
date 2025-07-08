package com.votechain.backend.vote.model;

public enum VoteStatus {
    PENDING,         // Vote is registered but not yet confirmed in blockchain
    CONFIRMED,       // Vote is confirmed in blockchain
    VERIFICADO,      // Vote is verified by independent verification
    REJECTED,        // Vote was rejected (e.g., due to rules violation)
    PROCESSING       // Vote is being processed by the blockchain
}
