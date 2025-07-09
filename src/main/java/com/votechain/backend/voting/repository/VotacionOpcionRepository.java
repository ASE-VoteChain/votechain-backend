package com.votechain.backend.voting.repository;

import com.votechain.backend.voting.model.VotacionOpcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VotacionOpcionRepository extends JpaRepository<VotacionOpcion, Long> {

    List<VotacionOpcion> findByVotacionIdOrderByOrden(Long votacionId);

    // ✅ AGREGAR: Método básico para buscar opciones por ID de votación
    List<VotacionOpcion> findByVotacionId(Long votacionId);

    @Query("SELECT o FROM VotacionOpcion o WHERE o.votacion.id = :votacionId")
    List<VotacionOpcion> getOpcionesByVotacionId(@Param("votacionId") Long votacionId);

    // ✅ AGREGAR: Buscar opción por votación y orden específico
    Optional<VotacionOpcion> findByVotacionIdAndOrden(Long votacionId, Integer orden);

    void deleteByVotacionId(Long votacionId);

    // Método para contar opciones de una votación
    long countByVotacionId(Long votacionId);
}
