package com.votechain.backend.voting.model;

/**
 * Estados posibles de una votación en el sistema VoteChain
 */
public enum VotacionEstado {
    CREADA,      // Recién creada, configurando opciones
    ABIERTA,     // Activa, aceptando votos
    CERRADA,     // Finalizada, resultados disponibles
    SUSPENDIDA,  // Temporalmente suspendida
    CANCELADA,   // Cancelada permanentemente
    PROXIMA;     // Programada para el futuro

    /**
     * Verifica si la votación está activa para recibir votos
     */
    public boolean isActive() {
        return this == ABIERTA;
    }

    /**
     * Verifica si la votación ha finalizado
     */
    public boolean isFinalized() {
        return this == CERRADA || this == CANCELADA;
    }

    /**
     * Verifica si la votación puede ser modificada
     */
    public boolean isEditable() {
        return this == CREADA || this == PROXIMA;
    }
}
