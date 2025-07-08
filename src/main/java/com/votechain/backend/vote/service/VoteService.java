package com.votechain.backend.vote.service;

import com.votechain.backend.auth.model.User;
import com.votechain.backend.blockchain.service.BlockchainService;
import com.votechain.backend.blockchain.model.BlockchainVerificationResult;
import com.votechain.backend.common.logging.SystemLogService;
import com.votechain.backend.vote.dto.CastVoteRequest;
import com.votechain.backend.vote.dto.VoteDto;
import com.votechain.backend.vote.dto.VoteVerificationDto;
import com.votechain.backend.auth.repository.UserRepository;
import com.votechain.backend.vote.dto.VoteVerificationStatus;
import com.votechain.backend.vote.model.Vote;
import com.votechain.backend.vote.model.VoteStatus;
import com.votechain.backend.voting.repository.VotacionOpcionRepository;
import com.votechain.backend.voting.repository.VotacionRepository;
import com.votechain.backend.vote.repository.VoteRepository;
import com.votechain.backend.voting.model.Votacion;
import com.votechain.backend.voting.model.VotacionEstado;
import com.votechain.backend.voting.model.VotacionOpcion;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.Duration;

@Service
@Slf4j
public class VoteService {

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private VotacionRepository votacionRepository;

    @Autowired
    private VotacionOpcionRepository opcionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private SystemLogService systemLogService;

    /**
     * Cast a vote
     */
    @Transactional
    public VoteDto castVote(Long userId, CastVoteRequest request) {
        // Validate votacion exists and is active
        Votacion votacion = votacionRepository.findById(request.getVotacionId())
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + request.getVotacionId()));

        // Check if votacion is active
        if (votacion.getEstado() != VotacionEstado.ABIERTA) {
            throw new IllegalStateException("Voting is not open: current state is " + votacion.getEstado());
        }

        LocalDateTime now = LocalDateTime.now();
        log.info("🕐 Validando fechas de votación:");
        log.info("   Fecha actual del sistema: {}", now);
        log.info("   Fecha inicio de votación: {}", votacion.getFechaInicio());
        log.info("   Fecha fin de votación: {}", votacion.getFechaFin());
        log.info("   ¿Antes del inicio? {}", now.isBefore(votacion.getFechaInicio()));
        log.info("   ¿Después del fin? {}", now.isAfter(votacion.getFechaFin()));

        if (now.isBefore(votacion.getFechaInicio()) || now.isAfter(votacion.getFechaFin())) {
            String errorMsg = String.format("Voting is not currently active. Current time: %s, Voting period: %s to %s",
                now, votacion.getFechaInicio(), votacion.getFechaFin());
            log.error("❌ {}", errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // Validate option exists
        VotacionOpcion opcion = opcionRepository.findById(request.getOpcionId())
                .orElseThrow(() -> new EntityNotFoundException("Option not found with id: " + request.getOpcionId()));

        if (!opcion.getVotacion().getId().equals(votacion.getId())) {
            throw new IllegalArgumentException("Option does not belong to this voting");
        }

        // Check if user already voted
        if (voteRepository.existsByVotacionIdAndUserId(votacion.getId(), userId)) {
            throw new IllegalStateException("User has already voted in this voting");
        }

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // Create vote
        Vote vote = Vote.builder()
                .votacion(votacion)
                .user(user)
                .opcionSeleccionada(opcion)
                .status(VoteStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        // Generate hash for the vote
        String voteHash = generateVoteHash(vote);
        vote.setVoteHash(voteHash);

        // Save vote to database first
        Vote savedVote = voteRepository.save(vote);

        // Log vote cast
        systemLogService.logVoteCast(userId, votacion.getId(), voteHash);

        // Submit to blockchain asynchronously
        CompletableFuture<String> blockchainFuture = blockchainService.registerVote(savedVote);

        // Process blockchain result
        blockchainFuture.thenAccept(transactionHash -> {
            // Update vote with blockchain transaction hash
            savedVote.setBlockchainTransactionHash(transactionHash);
            savedVote.setStatus(VoteStatus.CONFIRMED);
            savedVote.setBlockchainVerified(true);
            savedVote.setBlockchainVerifiedAt(LocalDateTime.now());

            voteRepository.save(savedVote);

            systemLogService.logBlockchainInteraction(userId, "Vote Registration", transactionHash);
            log.info("Vote registered on blockchain with hash: {}", transactionHash);
        }).exceptionally(ex -> {
            log.error("Error registering vote on blockchain", ex);
            systemLogService.logError("Blockchain Vote Registration",
                    "Error registering vote on blockchain: " + ex.getMessage());

            // Still keep the vote in database with error status
            savedVote.setStatus(VoteStatus.REJECTED);
            voteRepository.save(savedVote);

            return null;
        });

        // Return vote DTO immediately without waiting for blockchain
        return convertToDto(savedVote);
    }

    /**
     * Cast a vote with full blockchain integration (synchronous)
     */
    @Transactional
    public VoteDto castVoteWithBlockchain(Long userId, CastVoteRequest request) {
        log.info("🗳️ Iniciando voto con integración blockchain para usuario {} en votación {}", userId, request.getVotacionId());

        // Validate votacion exists and is active
        Votacion votacion = votacionRepository.findById(request.getVotacionId())
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + request.getVotacionId()));

        // Check if votacion is active
        if (votacion.getEstado() != VotacionEstado.ABIERTA) {
            throw new IllegalStateException("Voting is not open: current state is " + votacion.getEstado());
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(votacion.getFechaInicio()) || now.isAfter(votacion.getFechaFin())) {
            throw new IllegalStateException("Voting is not currently active");
        }

        // Validate option exists
        VotacionOpcion opcion = opcionRepository.findById(request.getOpcionId())
                .orElseThrow(() -> new EntityNotFoundException("Option not found with id: " + request.getOpcionId()));

        if (!opcion.getVotacion().getId().equals(votacion.getId())) {
            throw new IllegalArgumentException("Option does not belong to this voting");
        }

        // 🔒 VERIFICACIÓN ROBUSTA DE DUPLICADOS
        log.info("🔍 Realizando verificación robusta de votos duplicados...");
        VoteVerificationStatus verificationStatus = getVoteVerificationStatus(userId, votacion.getId());

        if (verificationStatus.isHasVotedInDB()) {
            log.warn("❌ Usuario {} ya tiene voto registrado en base de datos para votación {}", userId, votacion.getId());
            throw new IllegalStateException("User has already voted in this voting (verified in database)");
        }

        if (verificationStatus.getHasVotedInBlockchain() != null && verificationStatus.getHasVotedInBlockchain()) {
            log.warn("❌ Usuario {} ya tiene voto registrado en blockchain para votación {} (ID blockchain: {})",
                userId, votacion.getId(), verificationStatus.getBlockchainVotingId());
            throw new IllegalStateException("User has already voted according to blockchain verification");
        }

        if (!verificationStatus.isConsistent()) {
            log.error("❌ INCONSISTENCIA DETECTADA en verificación previa al voto: {}", verificationStatus.getStatusMessage());
            systemLogService.logError("Pre-Vote Verification Inconsistency", verificationStatus.getStatusMessage());
            throw new IllegalStateException("Vote verification inconsistency detected. Please contact support.");
        }

        log.info("✅ Verificación de duplicados completada: Usuario {} puede votar en votación {}", userId, votacion.getId());

        // Get user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // Create vote
        Vote vote = Vote.builder()
                .votacion(votacion)
                .user(user)
                .opcionSeleccionada(opcion)
                .status(VoteStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        // Generate hash for the vote
        String voteHash = generateVoteHash(vote);
        vote.setVoteHash(voteHash);

        // Save vote to database first
        Vote savedVote = voteRepository.save(vote);
        log.info("✅ Voto guardado en base de datos: ID={}, Hash={}", savedVote.getId(), voteHash);

        // Log vote cast
        systemLogService.logVoteCast(userId, votacion.getId(), voteHash);

        // Register in blockchain synchronously
        try {
            log.info("🔗 Registrando voto en blockchain...");
            CompletableFuture<String> future = blockchainService.registerVote(savedVote);
            String transactionHash = future.get(30, TimeUnit.SECONDS);

            log.info("✅ Voto registrado en blockchain con hash: {}", transactionHash);

            // Update vote with blockchain information
            savedVote.setBlockchainTransactionHash(transactionHash);
            savedVote.setStatus(VoteStatus.CONFIRMED);
            savedVote.setBlockchainVerified(true);
            savedVote.setBlockchainVerifiedAt(LocalDateTime.now());

            // Save updated vote
            savedVote = voteRepository.save(savedVote);

            // Verify the vote on blockchain
            log.info("🔍 Verificando voto en blockchain...");
            BlockchainVerificationResult verificationResult = blockchainService.verifyVote(transactionHash);
            log.info("✅ Verificación blockchain: {}", verificationResult.isVerified() ? "Exitosa" : "Fallida");

            // 🔍 VERIFICACIÓN FINAL POST-VOTO
            log.info("🔍 Realizando verificación final post-voto...");
            VoteVerificationStatus postVoteStatus = getVoteVerificationStatus(userId, votacion.getId());
            log.info("📊 Estado post-voto: {}", postVoteStatus.getStatusMessage());

            if (!postVoteStatus.isConsistent()) {
                log.warn("⚠️ Inconsistencia detectada después del voto: {}", postVoteStatus.getStatusMessage());
                systemLogService.logError("Post-Vote Verification Inconsistency", postVoteStatus.getStatusMessage());
            }

            // Log blockchain interaction
            systemLogService.logBlockchainInteraction(userId, "Vote Registration", transactionHash);

            log.info("✅ Proceso de voto completado exitosamente");

        } catch (Exception e) {
            log.error("❌ Error al registrar voto en blockchain", e);

            // Update vote status to rejected due to blockchain error
            savedVote.setStatus(VoteStatus.REJECTED);
            voteRepository.save(savedVote);

            systemLogService.logError("Blockchain Vote Registration",
                    "Error registering vote on blockchain: " + e.getMessage());

            throw new RuntimeException("Error registering vote on blockchain: " + e.getMessage(), e);
        }

        // Return vote DTO with complete information
        return convertToDto(savedVote);
    }

    /**
     * Verify a vote using its hash
     */
    public VoteVerificationDto verifyVote(String voteHash) {
        Vote vote = voteRepository.findByVoteHash(voteHash)
                .orElse(null);

        if (vote == null) {
            return VoteVerificationDto.builder()
                    .verified(false)
                    .message("Vote not found")
                    .build();
        }

        // If the vote has a blockchain transaction hash, verify it on the blockchain
        if (vote.getBlockchainTransactionHash() != null) {
            BlockchainVerificationResult result = blockchainService.verifyVote(
                    vote.getBlockchainTransactionHash());

            boolean isVerified = result.isVerified();

            systemLogService.logVoteVerification(vote.getUser().getId(), voteHash, isVerified);

            if (isVerified) {
                return VoteVerificationDto.builder()
                        .verified(true)
                        .votacionId(vote.getVotacion().getId())
                        .votacionTitulo(vote.getVotacion().getTitulo())
                        .timestamp(vote.getCreatedAt())
                        .blockchainTransactionHash(vote.getBlockchainTransactionHash())
                        .blockNumber(result.getBlockNumber())
                        .blockHash(result.getBlockHash())
                        .blockTimestamp(result.getTimestamp())
                        .message("Vote verified successfully")
                        .build();
            } else {
                return VoteVerificationDto.builder()
                        .verified(false)
                        .votacionId(vote.getVotacion().getId())
                        .votacionTitulo(vote.getVotacion().getTitulo())
                        .timestamp(vote.getCreatedAt())
                        .blockchainTransactionHash(vote.getBlockchainTransactionHash())
                        .message("Vote found in database but could not be verified on blockchain: " +
                                result.getError())
                        .build();
            }
        } else {
            // Vote exists in database but has not been confirmed on blockchain yet
            return VoteVerificationDto.builder()
                    .verified(false)
                    .votacionId(vote.getVotacion().getId())
                    .votacionTitulo(vote.getVotacion().getTitulo())
                    .timestamp(vote.getCreatedAt())
                    .message("Vote is pending blockchain confirmation")
                    .build();
        }
    }

    /**
     * Check if a user has already voted in a specific votacion
     */
    public boolean hasVoted(Long userId, Long votacionId) {
        return voteRepository.existsByVotacionIdAndUserId(votacionId, userId);
    }

    /**
     * Check if a user has already voted in a specific votacion with blockchain verification
     */
    public boolean hasUserVoted(Long userId, Long votacionId) {
        log.info("🔍 Verificando si usuario {} ya votó en votación {}", userId, votacionId);

        // 1. Check in database first (fastest check)
        boolean hasVotedInDB = voteRepository.existsByVotacionIdAndUserId(votacionId, userId);
        log.info("📊 Base de datos: Usuario {} votado = {}", userId, hasVotedInDB);

        // 2. If user has voted in DB, verify blockchain consistency
        if (hasVotedInDB) {
            try {
                // Get the votacion to find its blockchain ID
                Votacion votacion = votacionRepository.findById(votacionId).orElse(null);
                if (votacion != null && votacion.getBlockchainVotingId() != null) {
                    // Verify in blockchain using the real blockchain voting ID
                    boolean hasVotedInBlockchain = blockchainService.hasUserVoted(
                        votacion.getBlockchainVotingId(), userId);

                    log.info("🔗 Blockchain: Usuario {} votado = {} (votación blockchain ID: {})",
                        userId, hasVotedInBlockchain, votacion.getBlockchainVotingId());

                    // If there's inconsistency, log it but trust DB for now
                    if (hasVotedInDB != hasVotedInBlockchain) {
                        log.warn("⚠️ INCONSISTENCIA DETECTADA: DB={}, Blockchain={} para usuario {} en votación {}",
                            hasVotedInDB, hasVotedInBlockchain, userId, votacionId);

                        systemLogService.logError("Vote Verification Inconsistency",
                            String.format("User %d vote status inconsistent: DB=%s, Blockchain=%s for voting %d",
                                userId, hasVotedInDB, hasVotedInBlockchain, votacionId));
                    } else {
                        log.info("✅ Verificación consistente: Usuario {} {} ha votado",
                            userId, hasVotedInDB ? "SÍ" : "NO");
                    }
                } else {
                    log.warn("⚠️ Votación {} no tiene ID de blockchain, solo verificando BD", votacionId);
                }
            } catch (Exception e) {
                log.warn("⚠️ Error verificando blockchain para usuario {} en votación {}: {}",
                    userId, votacionId, e.getMessage());
                // Continue with DB result if blockchain check fails
            }
        } else {
            log.info("✅ Usuario {} NO ha votado en votación {}", userId, votacionId);
        }

        return hasVotedInDB;
    }

    /**
     * Enhanced verification that returns detailed information about vote status
     */
    public VoteVerificationStatus getVoteVerificationStatus(Long userId, Long votacionId) {
        log.info("🔍 Verificación detallada para usuario {} en votación {}", userId, votacionId);

        boolean hasVotedInDB = voteRepository.existsByVotacionIdAndUserId(votacionId, userId);
        Boolean hasVotedInBlockchain = null;
        String blockchainError = null;
        Long blockchainVotingId = null;

        try {
            Votacion votacion = votacionRepository.findById(votacionId).orElse(null);
            if (votacion != null && votacion.getBlockchainVotingId() != null) {
                blockchainVotingId = votacion.getBlockchainVotingId();
                hasVotedInBlockchain = blockchainService.hasUserVoted(blockchainVotingId, userId);
            }
        } catch (Exception e) {
            blockchainError = e.getMessage();
            log.warn("Error verificando blockchain: {}", e.getMessage());
        }

        return VoteVerificationStatus.builder()
            .userId(userId)
            .votacionId(votacionId)
            .hasVotedInDB(hasVotedInDB)
            .hasVotedInBlockchain(hasVotedInBlockchain)
            .blockchainVotingId(blockchainVotingId)
            .blockchainError(blockchainError)
            .isConsistent(hasVotedInBlockchain == null || hasVotedInDB == hasVotedInBlockchain)
            .verifiedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Get vote history for a specific user
     */
    public Page<VoteDto> getUserVoteHistory(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Vote> votes = voteRepository.findByUserId(userId, pageable);

        return votes.map(this::convertToDto);
    }

    /**
     * Get vote by ID
     */
    public VoteDto getVoteById(Long voteId, Long userId) {
        Vote vote = voteRepository.findById(voteId)
                .orElseThrow(() -> new EntityNotFoundException("Vote not found with id: " + voteId));

        // Security check - only the user who cast the vote or admins can see it
        // (Actual role-based check would be done at controller level)
        if (!vote.getUser().getId().equals(userId)) {
            throw new SecurityException("You don't have permission to access this vote");
        }

        return convertToDto(vote);
    }

    /**
     * Crea un voto de prueba para las pruebas del blockchain sin validaciones adicionales
     * Este método es solo para fines de prueba
     */
    @Transactional
    public Vote createVote(Long votacionId, Long userId, Long opcionId) {
        // Obtener entidades necesarias
        Votacion votacion = votacionRepository.findById(votacionId)
                .orElseThrow(() -> new EntityNotFoundException("Votacion not found with id: " + votacionId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        VotacionOpcion opcion = opcionRepository.findById(opcionId)
                .orElseThrow(() -> new EntityNotFoundException("Option not found with id: " + opcionId));

        // Crear voto (sin validaciones para propósitos de prueba)
        Vote vote = Vote.builder()
                .votacion(votacion)
                .user(user)
                .opcionSeleccionada(opcion)
                .status(VoteStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        // Generar hash
        String voteHash = generateVoteHash(vote);
        vote.setVoteHash(voteHash);

        // Guardar en base de datos
        return voteRepository.save(vote);
    }

    /**
     * Generate a unique hash for a vote
     */
    private String generateVoteHash(Vote vote) {
        try {
            String data = String.format("%d-%d-%d-%d-%s",
                    vote.getVotacion().getId(),
                    vote.getUser().getId(),
                    vote.getOpcionSeleccionada().getId(),
                    System.currentTimeMillis(),
                    vote.getUser().getDni());

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());

            // Convert byte array to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating vote hash", e);
        }
    }

    /**
     * Convert entity to DTO
     */
    private VoteDto convertToDto(Vote vote) {
        String blockchainStatus = "PENDING";
        if (vote.isBlockchainVerified()) {
            blockchainStatus = "VERIFIED";
        } else if (vote.getStatus() == VoteStatus.REJECTED) {
            blockchainStatus = "FAILED";
        } else if (vote.getBlockchainTransactionHash() != null) {
            blockchainStatus = "CONFIRMED";
        }

        return VoteDto.builder()
                .id(vote.getId())
                .userId(vote.getUser().getId())
                .userEmail(vote.getUser().getEmail())
                .userName(vote.getUser().getFullName())
                .votacionId(vote.getVotacion().getId())
                .votacionTitulo(vote.getVotacion().getTitulo())
                .opcionId(vote.getOpcionSeleccionada().getId())
                .opcionTitulo(vote.getOpcionSeleccionada().getTitulo())
                .createdAt(vote.getCreatedAt())
                .voteHash(vote.getVoteHash())
                .blockchainTransactionHash(vote.getBlockchainTransactionHash())
                .blockchainStatus(blockchainStatus)
                .blockchainVerifiedAt(vote.getBlockchainVerifiedAt())
                .status(vote.getStatus())
                .build();
    }

    /**
     * Count total votes for a specific votacion
     */
    public long countByVotacionId(Long votacionId) {
        return voteRepository.countByVotacionId(votacionId);
    }

    /**
     * Get vote distribution by option for a specific votacion
     */
    public Map<String, Long> getVoteDistributionByOption(Long votacionId) {
        List<VotacionOpcion> opciones = opcionRepository.findByVotacionIdOrderByOrden(votacionId);
        Map<String, Long> distribution = new HashMap<>();

        for (VotacionOpcion opcion : opciones) {
            long voteCount = voteRepository.countByVotacionIdAndOpcionSeleccionadaId(votacionId, opcion.getId());
            distribution.put(opcion.getTitulo(), voteCount);
        }

        return distribution;
    }

    /**
     * Get votes over time for temporal analysis
     */
    public Map<LocalDateTime, Long> getVotesOverTime(Long votacionId) {
        List<Vote> votes = voteRepository.findByVotacionIdOrderByCreatedAt(votacionId);
        Map<LocalDateTime, Long> votesOverTime = new HashMap<>();

        // Group votes by hour
        for (Vote vote : votes) {
            LocalDateTime hourKey = vote.getCreatedAt().withMinute(0).withSecond(0).withNano(0);
            votesOverTime.put(hourKey, votesOverTime.getOrDefault(hourKey, 0L) + 1);
        }

        return votesOverTime;
    }

    /**
     * Get blockchain statistics for a votacion
     */
    public Map<String, Object> getBlockchainStats(Long votacionId) {
        Map<String, Object> stats = new HashMap<>();

        try {
            // Get votacion to access blockchain data
            Votacion votacion = votacionRepository.findById(votacionId).orElse(null);
            if (votacion == null) {
                stats.put("error", "Votación no encontrada");
                return stats;
            }

            // Basic blockchain info
            stats.put("blockchainVotingId", votacion.getBlockchainVotingId());
            stats.put("transactionHash", votacion.getBlockchainTransactionHash());
            stats.put("blockchainVerified", votacion.isBlockchainVerified());
            stats.put("verifiedAt", votacion.getBlockchainVerifiedAt());

            // Vote verification statistics
            long totalVotes = voteRepository.countByVotacionId(votacionId);
            long verifiedVotes = voteRepository.countByVotacionIdAndBlockchainVerified(votacionId, true);
            long pendingVotes = voteRepository.countByVotacionIdAndStatus(votacionId, VoteStatus.PENDING);
            long rejectedVotes = voteRepository.countByVotacionIdAndStatus(votacionId, VoteStatus.REJECTED);

            stats.put("totalVotes", totalVotes);
            stats.put("verifiedVotes", verifiedVotes);
            stats.put("pendingVotes", pendingVotes);
            stats.put("rejectedVotes", rejectedVotes);
            stats.put("verificationPercentage", totalVotes > 0 ? (verifiedVotes * 100.0 / totalVotes) : 0.0);

            // Blockchain connectivity status
            if (votacion.getBlockchainVotingId() != null) {
                try {
                    boolean votacionExists = blockchainService.checkVotacionExistsSafe(votacion.getBlockchainVotingId())
                        .get(10, TimeUnit.SECONDS);
                    stats.put("blockchainStatus", votacionExists ? "CONNECTED" : "NOT_FOUND");

                    if (votacionExists) {
                        // Get blockchain vote count for verification
                        long blockchainVoteCount = blockchainService.getVoteCount(votacion.getBlockchainVotingId());
                        stats.put("blockchainVoteCount", blockchainVoteCount);
                        stats.put("consistencyCheck", blockchainVoteCount == verifiedVotes ? "CONSISTENT" : "INCONSISTENT");
                    }
                } catch (Exception e) {
                    stats.put("blockchainStatus", "ERROR");
                    stats.put("blockchainError", e.getMessage());
                }
            } else {
                stats.put("blockchainStatus", "NOT_DEPLOYED");
            }

        } catch (Exception e) {
            log.error("Error getting blockchain stats for votacion {}", votacionId, e);
            stats.put("error", "Error obteniendo estadísticas blockchain: " + e.getMessage());
        }

        return stats;
    }
}
