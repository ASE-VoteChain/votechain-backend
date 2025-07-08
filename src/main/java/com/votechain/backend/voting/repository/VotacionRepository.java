package com.votechain.backend.voting.repository;

import com.votechain.backend.voting.model.Votacion;
import com.votechain.backend.voting.model.VotacionCategoria;
import com.votechain.backend.voting.model.VotacionEstado;
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
public interface VotacionRepository extends JpaRepository<Votacion, Long> {

    Page<Votacion> findByEstado(VotacionEstado estado, Pageable pageable);

    Page<Votacion> findByCategoria(VotacionCategoria categoria, Pageable pageable);

    Page<Votacion> findByEstadoAndCategoria(VotacionEstado estado, VotacionCategoria categoria, Pageable pageable);

    @Query("SELECT v FROM Votacion v WHERE v.fechaInicio <= :now AND v.fechaFin >= :now AND v.estado = 'ABIERTA'")
    Page<Votacion> findActiveVotaciones(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT v FROM Votacion v WHERE v.fechaInicio > :now AND v.estado = 'PROXIMA'")
    Page<Votacion> findUpcomingVotaciones(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT v FROM Votacion v WHERE v.fechaFin < :now OR v.estado = 'CERRADA'")
    Page<Votacion> findClosedVotaciones(@Param("now") LocalDateTime now, Pageable pageable);

    // Método para buscar votaciones por creador
    Page<Votacion> findByCreadorId(Long creadorId, Pageable pageable);

    // Método para búsqueda con texto
    @Query("SELECT v FROM Votacion v WHERE " +
           "(:searchTerm IS NULL OR LOWER(v.titulo) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(v.descripcion) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND (:estado IS NULL OR v.estado = :estado) " +
           "AND (:categoria IS NULL OR v.categoria = :categoria)")
    Page<Votacion> searchVotaciones(@Param("searchTerm") String searchTerm,
                                   @Param("estado") VotacionEstado estado,
                                   @Param("categoria") VotacionCategoria categoria,
                                   Pageable pageable);

    // Método para encontrar votaciones en las que un usuario ha participado
    @Query("SELECT DISTINCT v FROM Votacion v JOIN v.votos vote WHERE vote.user.id = :userId")
    Page<Votacion> findVotacionesByUserId(@Param("userId") Long userId, Pageable pageable);

    // Método para verificar si un usuario ha votado en una votación específica
    @Query("SELECT CASE WHEN COUNT(vote) > 0 THEN TRUE ELSE FALSE END FROM Vote vote WHERE vote.votacion.id = :votacionId AND vote.user.id = :userId")
    boolean hasUserVotedInVotacion(@Param("votacionId") Long votacionId, @Param("userId") Long userId);

    // Métodos para estadísticas del dashboard
    long countByEstado(VotacionEstado estado);

    long countByCategoria(VotacionCategoria categoria);

    long countByCreadorId(Long creadorId);

    long countByCreadorIdAndEstado(Long creadorId, VotacionEstado estado);

    long countByCreadorIdAndCategoria(Long creadorId, VotacionCategoria categoria);

    @Query("SELECT COUNT(v) FROM Votacion v WHERE v.creador.id = :creadorId AND YEAR(v.createdAt) = YEAR(CURRENT_DATE)")
    long countByCreadorIdThisYear(@Param("creadorId") Long creadorId);

    @Query("SELECT COUNT(v) FROM Votacion v WHERE v.createdAt >= :startDate AND v.createdAt <= :endDate")
    long countByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(v) FROM Votacion v WHERE v.createdAt >= :startOfWeek")
    long countVotacionesThisWeek(@Param("startOfWeek") LocalDateTime startOfWeek);

    @Query("SELECT COUNT(v) FROM Votacion v WHERE v.creador.id = :creadorId AND v.createdAt > :date")
    long countByCreadorIdAndCreatedAtAfter(@Param("creadorId") Long creadorId, @Param("date") LocalDateTime date);

    // Votaciones recientes
    @Query("SELECT v FROM Votacion v ORDER BY v.createdAt DESC")
    Page<Votacion> findTop5ByOrderByCreatedAtDesc(Pageable pageable);

    default List<Votacion> findTop5ByOrderByCreatedAtDesc() {
        return findTop5ByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 5)).getContent();
    }

    // Votaciones por creador ordenadas por fecha
    @Query("SELECT v FROM Votacion v WHERE v.creador.id = :creadorId ORDER BY v.createdAt DESC")
    List<Votacion> findByCreadorIdOrderByCreatedAtDesc(@Param("creadorId") Long creadorId, Pageable pageable);

    // Todas las votaciones de un creador
    List<Votacion> findByCreadorId(Long creadorId);

    // Última votación de un usuario
    Optional<Votacion> findFirstByCreadorIdOrderByCreatedAtDesc(Long creadorId);

    // Votaciones verificadas en blockchain
    long countByBlockchainVerifiedTrue();
}
