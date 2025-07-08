package com.votechain.backend.vote.controller;

import com.votechain.backend.vote.dto.CastVoteRequest;
import com.votechain.backend.vote.dto.VoteDto;
import com.votechain.backend.vote.dto.VoteVerificationDto;
import com.votechain.backend.vote.dto.VoteVerificationStatus;
import com.votechain.backend.security.UserDetailsImpl;
import com.votechain.backend.vote.service.VoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/votes")  // ✅ CORREGIDO: Sin /api porque ya está en context-path
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Votos", description = "Gestión de votos y verificación")
@Slf4j
public class VoteController {

    @Autowired
    private VoteService voteService;

    /**
     * Cast a vote with full blockchain integration
     */
    @Operation(
        summary = "Emitir voto con integración blockchain",
        description = "Permite al usuario emitir un voto que se registra automáticamente en blockchain"
    )
    @SecurityRequirement(name = "bearer-jwt")
    @PostMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> castVote(
            @Parameter(description = "Datos del voto a emitir", required = true)
            @Valid @RequestBody CastVoteRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            log.info("🗳️ Usuario {} intentando votar en votación {} por opción {}",
                userDetails.getId(), request.getVotacionId(), request.getOpcionId());

            VoteDto vote = voteService.castVoteWithBlockchain(userDetails.getId(), request);

            log.info("✅ Voto emitido exitosamente: {}", vote.getId());
            return new ResponseEntity<>(vote, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("❌ Error al emitir voto para usuario {}: {}", userDetails.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cast a vote (legacy endpoint - maintains backward compatibility)
     */
    @Operation(
        summary = "Emitir voto (modo legado)",
        description = "Permite emitir un voto solo en base de datos sin blockchain"
    )
    @SecurityRequirement(name = "bearer-jwt")
    @PostMapping("/cast")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> castVoteLegacy(
            @Parameter(description = "Datos del voto a emitir", required = true)
            @Valid @RequestBody CastVoteRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            log.info("🗳️ Usuario {} votando (modo legado) en votación {} por opción {}",
                userDetails.getId(), request.getVotacionId(), request.getOpcionId());

            VoteDto vote = voteService.castVote(userDetails.getId(), request);

            log.info("✅ Voto legado emitido exitosamente: {}", vote.getId());
            return new ResponseEntity<>(vote, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("❌ Error al emitir voto legado para usuario {}: {}", userDetails.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Verify if a user has already voted in a votacion
     */
    @GetMapping("/check/{votacionId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Boolean> checkVoteStatus(
            @PathVariable Long votacionId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        boolean hasVoted = voteService.hasVoted(userDetails.getId(), votacionId);
        return ResponseEntity.ok(hasVoted);
    }

    /**
     * Verify a vote using its hash (public, no authentication required)
     */
    @GetMapping("/public/verify/{voteHash}")
    public ResponseEntity<VoteVerificationDto> verifyVote(@PathVariable String voteHash) {
        VoteVerificationDto verification = voteService.verifyVote(voteHash);
        return ResponseEntity.ok(verification);
    }

    /**
     * Get vote history for authenticated user
     */
    @GetMapping("/user/history")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Page<VoteDto>> getUserVoteHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        Page<VoteDto> votes = voteService.getUserVoteHistory(userDetails.getId(), page, size);
        return ResponseEntity.ok(votes);
    }

    /**
     * Get vote details by ID
     */
    @GetMapping("/user/{voteId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<VoteDto> getVoteById(
            @PathVariable Long voteId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        VoteDto vote = voteService.getVoteById(voteId, userDetails.getId());
        return ResponseEntity.ok(vote);
    }

    /**
     * Verify if a user has already voted in a votacion (public endpoint for transparency)
     */
    @GetMapping("/usuario/{userId}/votacion/{votacionId}/verificar")
    public ResponseEntity<Boolean> hasUserVoted(
            @PathVariable Long userId,
            @PathVariable Long votacionId) {

        boolean hasVoted = voteService.hasUserVoted(userId, votacionId);
        return ResponseEntity.ok(hasVoted);
    }

    /**
     * Verify if the authenticated user has already voted in a votacion
     */
    @GetMapping("/votacion/{votacionId}/verificar")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Boolean> hasAuthenticatedUserVoted(
            @PathVariable Long votacionId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        boolean hasVoted = voteService.hasUserVoted(userDetails.getId(), votacionId);
        return ResponseEntity.ok(hasVoted);
    }

    /**
     * Get detailed vote verification status (includes blockchain consistency check)
     */
    @GetMapping("/usuario/{userId}/votacion/{votacionId}/verificar-detallado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VoteVerificationStatus> getDetailedVoteVerification(
            @PathVariable Long userId,
            @PathVariable Long votacionId) {

        VoteVerificationStatus status = voteService.getVoteVerificationStatus(userId, votacionId);
        return ResponseEntity.ok(status);
    }

    /**
     * Get detailed vote verification status for authenticated user
     */
    @GetMapping("/votacion/{votacionId}/verificar-detallado")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<VoteVerificationStatus> getAuthenticatedUserDetailedVerification(
            @PathVariable Long votacionId,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {

        VoteVerificationStatus status = voteService.getVoteVerificationStatus(userDetails.getId(), votacionId);
        return ResponseEntity.ok(status);
    }
}
