package com.votechain.backend.common.logging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    
    Page<SystemLog> findByType(LogType type, Pageable pageable);
    
    Page<SystemLog> findByLevel(LogLevel level, Pageable pageable);
    
    Page<SystemLog> findByUserId(Long userId, Pageable pageable);
    
    @Query("SELECT l FROM SystemLog l WHERE " +
           "(:type IS NULL OR l.type = :type) AND " +
           "(:level IS NULL OR l.level = :level) AND " +
           "(:userId IS NULL OR l.user.id = :userId) AND " +
           "(:startDate IS NULL OR l.timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR l.timestamp <= :endDate) AND " +
           "(:action IS NULL OR LOWER(l.action) LIKE LOWER(CONCAT('%', :action, '%')))")
    Page<SystemLog> searchLogs(
            @Param("type") LogType type,
            @Param("level") LogLevel level,
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("action") String action,
            Pageable pageable);
}
