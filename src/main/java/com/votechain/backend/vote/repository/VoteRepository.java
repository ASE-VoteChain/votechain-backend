package com.votechain.backend.vote.repository;

import com.votechain.backend.vote.model.Vote;
import com.votechain.backend.vote.model.VoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

    List<Vote> findByVotacionId(Long votacionId);

    Page<Vote> findByVotacionId(Long votacionId, Pageable pageable);

    List<Vote> findByUserId(Long userId);

    Page<Vote> findByUserId(Long userId, Pageable pageable);

    Optional<Vote> findByVoteHash(String voteHash);

    Optional<Vote> findByBlockchainTransactionHash(String transactionHash);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.votacion.id = :votacionId AND v.opcionSeleccionada.id = :opcionId")
    Long countVotesByVotacionAndOpcion(@Param("votacionId") Long votacionId, @Param("opcionId") Long opcionId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.votacion.id = :votacionId")
    Long countByVotacionId(@Param("votacionId") Long votacionId);

    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN TRUE ELSE FALSE END FROM Vote v WHERE v.votacion.id = :votacionId AND v.user.id = :userId")
    boolean existsByVotacionIdAndUserId(@Param("votacionId") Long votacionId, @Param("userId") Long userId);

    Page<Vote> findByStatus(VoteStatus status, Pageable pageable);

    // Métodos adicionales para estadísticas
    long countByVotacionIdAndOpcionSeleccionadaId(Long votacionId, Long opcionSeleccionadaId);

    List<Vote> findByVotacionIdOrderByCreatedAt(Long votacionId);

    long countByVotacionIdAndBlockchainVerified(Long votacionId, boolean blockchainVerified);

    long countByVotacionIdAndStatus(Long votacionId, VoteStatus status);

    @Query("SELECT v FROM Vote v WHERE v.votacion.id = :votacionId ORDER BY v.createdAt DESC")
    List<Vote> findLatestVotesByVotacion(@Param("votacionId") Long votacionId, Pageable pageable);

    // Métodos adicionales para el dashboard
    long countByUserId(Long userId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.user.id = :userId AND YEAR(v.createdAt) = YEAR(CURRENT_DATE)")
    long countByUserIdThisYear(@Param("userId") Long userId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.createdAt >= :startDate AND v.createdAt <= :endDate")
    long countByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(v) FROM Vote v WHERE YEAR(v.createdAt) = YEAR(CURRENT_DATE) AND MONTH(v.createdAt) = MONTH(CURRENT_DATE) AND DAY(v.createdAt) = DAY(CURRENT_DATE)")
    long countVotesToday();

    // Votos recientes
    @Query("SELECT v FROM Vote v ORDER BY v.createdAt DESC")
    List<Vote> findTop10ByOrderByCreatedAtDesc(Pageable pageable);

    default List<Vote> findTop10ByOrderByCreatedAtDesc() {
        return findTop10ByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 10));
    }

    // Votos por usuario ordenados por fecha
    List<Vote> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Último voto de un usuario
    Optional<Vote> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    // Votos verificados en blockchain
    long countByBlockchainVerifiedTrue();

    // Métodos adicionales que faltan para UserService
    @Query("SELECT COUNT(v) FROM Vote v WHERE v.user.id = :userId AND v.createdAt > :date")
    long countByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("date") java.time.LocalDateTime date);
}
