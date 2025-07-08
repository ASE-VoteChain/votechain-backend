package com.votechain.backend.voting.repository;

import com.votechain.backend.voting.model.VotacionOpcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VotacionOpcionRepository extends JpaRepository<VotacionOpcion, Long> {

    List<VotacionOpcion> findByVotacionIdOrderByOrden(Long votacionId);

    @Query("SELECT o FROM VotacionOpcion o WHERE o.votacion.id = :votacionId")
    List<VotacionOpcion> getOpcionesByVotacionId(@Param("votacionId") Long votacionId);

    void deleteByVotacionId(Long votacionId);

    // Método para contar opciones de una votación
    long countByVotacionId(Long votacionId);
}
