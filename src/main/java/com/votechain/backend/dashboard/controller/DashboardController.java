package com.votechain.backend.dashboard.controller;

import com.votechain.backend.auth.service.UserService;
import com.votechain.backend.auth.repository.UserRepository;
import com.votechain.backend.vote.repository.VoteRepository;
import com.votechain.backend.voting.repository.VotacionRepository;
import com.votechain.backend.voting.model.VotacionEstado;
import com.votechain.backend.voting.model.VotacionCategoria;
import com.votechain.backend.voting.model.Votacion;
import com.votechain.backend.vote.model.Vote;
import com.votechain.backend.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dashboard")  // ✅ Sin /api porque ya está en context-path
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Dashboard", description = "Estadísticas y métricas del sistema")
@Slf4j
public class DashboardController {

    @Autowired
    private VotacionRepository votacionRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    /**
     * Estadísticas generales del sistema - Solo Admin
     */
    @Operation(
        summary = "Obtener estadísticas generales del sistema",
        description = "Devuelve métricas completas del sistema de votaciones para administradores"
    )
    @SecurityRequirement(name = "bearer-jwt")
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDashboardStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // 📊 ESTADÍSTICAS BÁSICAS
            Map<String, Object> basicStats = new HashMap<>();
            basicStats.put("totalVotaciones", votacionRepository.count());
            basicStats.put("votacionesActivas", countVotacionesByEstado(VotacionEstado.ABIERTA));
            basicStats.put("votacionesCreadas", countVotacionesByEstado(VotacionEstado.CREADA));
            basicStats.put("votacionesCerradas", countVotacionesByEstado(VotacionEstado.CERRADA));
            basicStats.put("totalVotos", voteRepository.count());
            basicStats.put("totalUsuarios", userRepository.count());

            // 📈 ESTADÍSTICAS POR CATEGORÍA
            Map<String, Long> statsByCategory = new HashMap<>();
            for (VotacionCategoria categoria : VotacionCategoria.values()) {
                statsByCategory.put(categoria.name(), countVotacionesByCategoria(categoria));
            }

            // 📅 VOTACIONES RECIENTES (últimas 5)
            List<Votacion> votacionesRecientes = votacionRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .collect(Collectors.toList());
            List<Map<String, Object>> votacionesRecientesDto = votacionesRecientes.stream()
                .map(this::convertVotacionToSummary)
                .collect(Collectors.toList());

            // 🗳️ VOTOS RECIENTES (últimos 10)
            List<Vote> votosRecientes = voteRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(10)
                .collect(Collectors.toList());
            List<Map<String, Object>> votosRecientesDto = votosRecientes.stream()
                .map(this::convertVoteToSummary)
                .collect(Collectors.toList());

            // 📊 ESTADÍSTICAS DE PARTICIPACIÓN
            Map<String, Object> participationStats = new HashMap<>();
            participationStats.put("promedioPorcentajeParticipacion", calculateAverageParticipation());
            participationStats.put("votacionConMasParticipacion", findHighestParticipationVoting());
            participationStats.put("votosHoy", countVotesToday());
            participationStats.put("votacionesEstaSemanea", countVotacionesThisWeek());

            // 🏆 TOP VOTACIONES MÁS ACTIVAS
            List<Map<String, Object>> topVotaciones = getTopActiveVotaciones();

            // 📈 TENDENCIAS TEMPORALES
            Map<String, Object> trends = new HashMap<>();
            trends.put("votacionesPorMes", getVotacionesByMonth());
            trends.put("votosPorDia", getVotesByDay());
            trends.put("usuariosRegistradosPorMes", getUserRegistrationsByMonth());

            // 🔗 ESTADÍSTICAS BLOCKCHAIN
            Map<String, Object> blockchainStats = new HashMap<>();
            blockchainStats.put("votosVerificadosBlockchain", countBlockchainVerifiedVotes());
            blockchainStats.put("votacionesEnBlockchain", countBlockchainVerifiedVotaciones());
            blockchainStats.put("porcentajeVerificacion", calculateBlockchainVerificationPercentage());

            // 🎯 CONSTRUIR RESPUESTA
            stats.put("estadisticasBasicas", basicStats);
            stats.put("estadisticasPorCategoria", statsByCategory);
            stats.put("votacionesRecientes", votacionesRecientesDto);
            stats.put("votosRecientes", votosRecientesDto);
            stats.put("estadisticasParticipacion", participationStats);
            stats.put("topVotaciones", topVotaciones);
            stats.put("tendencias", trends);
            stats.put("blockchain", blockchainStats);
            stats.put("generadoEn", LocalDateTime.now());

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("❌ Error obteniendo estadísticas del dashboard", e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "Error obteniendo estadísticas: " + e.getMessage()));
        }
    }

    /**
     * Estadísticas del usuario actual
     */
    @Operation(
        summary = "Obtener estadísticas del usuario actual",
        description = "Devuelve métricas personalizadas para el usuario autenticado"
    )
    @SecurityRequirement(name = "bearer-jwt")
    @GetMapping("/mis-stats")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<?> getUserStats(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        try {
            Long userId = userDetails.getId();
            Map<String, Object> stats = new HashMap<>();

            // 📊 ESTADÍSTICAS BÁSICAS DEL USUARIO
            Map<String, Object> basicStats = new HashMap<>();
            basicStats.put("votacionesCreadas", countVotacionesByCreador(userId));
            basicStats.put("votosRealizados", countVotesByUser(userId));
            basicStats.put("votacionesActivasCreadas", countActiveVotacionesByCreador(userId));
            basicStats.put("fechaRegistro", userService.getUserProfile(userId).getCreatedAt());

            // 📋 ÚLTIMAS VOTACIONES CREADAS
            List<Votacion> ultimasVotaciones = getRecentVotacionesByCreator(userId);
            List<Map<String, Object>> ultimasVotacionesDto = ultimasVotaciones.stream()
                .map(this::convertVotacionToDetailedSummary)
                .collect(Collectors.toList());

            // 🗳️ ÚLTIMOS VOTOS REALIZADOS
            List<Vote> ultimosVotos = getRecentVotesByUser(userId);
            List<Map<String, Object>> ultimosVotosDto = ultimosVotos.stream()
                .map(this::convertVoteToUserSummary)
                .collect(Collectors.toList());

            // 📈 ESTADÍSTICAS POR CATEGORÍA (votaciones creadas)
            Map<String, Long> votacionesPorCategoria = new HashMap<>();
            for (VotacionCategoria categoria : VotacionCategoria.values()) {
                votacionesPorCategoria.put(categoria.name(),
                    countVotacionesByCreatorAndCategory(userId, categoria));
            }

            // 🏆 VOTACIÓN MÁS EXITOSA
            Map<String, Object> votacionMasExitosa = getMostSuccessfulVotacion(userId);

            // 📊 ESTADÍSTICAS DE PARTICIPACIÓN EN VOTACIONES CREADAS
            Map<String, Object> participationInMyVotings = getParticipationInUserVotings(userId);

            // 📅 ACTIVIDAD RECIENTE
            Map<String, Object> actividadReciente = new HashMap<>();
            actividadReciente.put("votacionesEsteAño", countVotacionesByCreatorThisYear(userId));
            actividadReciente.put("votosEsteAño", countVotesByUserThisYear(userId));
            actividadReciente.put("ultimaActividad", getLastUserActivity(userId));

            // 🎯 CONSTRUIR RESPUESTA
            stats.put("estadisticasBasicas", basicStats);
            stats.put("ultimasVotaciones", ultimasVotacionesDto);
            stats.put("ultimosVotos", ultimosVotosDto);
            stats.put("votacionesPorCategoria", votacionesPorCategoria);
            stats.put("votacionMasExitosa", votacionMasExitosa);
            stats.put("participacionEnMisVotaciones", participationInMyVotings);
            stats.put("actividadReciente", actividadReciente);
            stats.put("generadoEn", LocalDateTime.now());

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("❌ Error obteniendo estadísticas del usuario {}", userDetails.getId(), e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "Error obteniendo estadísticas del usuario: " + e.getMessage()));
        }
    }

    /**
     * Estadísticas públicas (sin autenticación)
     */
    @Operation(
        summary = "Obtener estadísticas públicas",
        description = "Devuelve métricas básicas públicas del sistema"
    )
    @GetMapping("/public-stats")
    public ResponseEntity<?> getPublicStats() {
        log.info("➡️ Ingreso al endpoint público /api/dashboard/public-stats");

        try {
            Map<String, Object> stats = new HashMap<>();

            stats.put("totalVotaciones", votacionRepository.count());
            stats.put("votacionesActivas", countVotacionesByEstado(VotacionEstado.ABIERTA));
            stats.put("totalVotos", voteRepository.count());
            stats.put("totalUsuarios", userRepository.count());

            List<Map<String, Object>> topVotaciones = getTopActiveVotaciones().stream()
                    .limit(3)
                    .collect(Collectors.toList());

            stats.put("topVotaciones", topVotaciones);
            stats.put("generadoEn", LocalDateTime.now());

            log.info("✅ Estadísticas públicas generadas correctamente");
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("❌ Error obteniendo estadísticas públicas", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error obteniendo estadísticas públicas: " + e.getMessage()));
        }
    }


    // ========== MÉTODOS AUXILIARES ==========

    // Métodos de conteo que implementan la lógica de los repositorios
    private long countVotacionesByEstado(VotacionEstado estado) {
        return votacionRepository.findAll().stream()
            .filter(v -> v.getEstado() == estado)
            .count();
    }

    private long countVotacionesByCategoria(VotacionCategoria categoria) {
        return votacionRepository.findAll().stream()
            .filter(v -> v.getCategoria() == categoria)
            .count();
    }

    private long countVotacionesByCreador(Long creadorId) {
        return votacionRepository.findAll().stream()
            .filter(v -> v.getCreador() != null && v.getCreador().getId().equals(creadorId))
            .count();
    }

    private long countVotesByUser(Long userId) {
        return voteRepository.findAll().stream()
            .filter(v -> v.getUser().getId().equals(userId))
            .count();
    }

    private long countActiveVotacionesByCreador(Long creadorId) {
        return votacionRepository.findAll().stream()
            .filter(v -> v.getCreador() != null &&
                        v.getCreador().getId().equals(creadorId) &&
                        v.getEstado() == VotacionEstado.ABIERTA)
            .count();
    }

    private long countVotesToday() {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        return voteRepository.findAll().stream()
            .filter(v -> v.getCreatedAt().isAfter(startOfDay) && v.getCreatedAt().isBefore(endOfDay))
            .count();
    }

    private long countVotacionesThisWeek() {
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);

        return votacionRepository.findAll().stream()
            .filter(v -> v.getCreatedAt().isAfter(startOfWeek))
            .count();
    }

    private long countBlockchainVerifiedVotes() {
        return voteRepository.findAll().stream()
            .filter(Vote::isBlockchainVerified)
            .count();
    }

    private long countBlockchainVerifiedVotaciones() {
        return votacionRepository.findAll().stream()
            .filter(Votacion::isBlockchainVerified)
            .count();
    }

    private List<Votacion> getRecentVotacionesByCreator(Long creadorId) {
        return votacionRepository.findAll().stream()
            .filter(v -> v.getCreador() != null && v.getCreador().getId().equals(creadorId))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(5)
            .collect(Collectors.toList());
    }

    private List<Vote> getRecentVotesByUser(Long userId) {
        return voteRepository.findAll().stream()
            .filter(v -> v.getUser().getId().equals(userId))
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .limit(5)
            .collect(Collectors.toList());
    }

    private long countVotacionesByCreatorAndCategory(Long creadorId, VotacionCategoria categoria) {
        return votacionRepository.findAll().stream()
            .filter(v -> v.getCreador() != null &&
                        v.getCreador().getId().equals(creadorId) &&
                        v.getCategoria() == categoria)
            .count();
    }

    private long countVotacionesByCreatorThisYear(Long creadorId) {
        int currentYear = LocalDateTime.now().getYear();
        return votacionRepository.findAll().stream()
            .filter(v -> v.getCreador() != null &&
                        v.getCreador().getId().equals(creadorId) &&
                        v.getCreatedAt().getYear() == currentYear)
            .count();
    }

    private long countVotesByUserThisYear(Long userId) {
        int currentYear = LocalDateTime.now().getYear();
        return voteRepository.findAll().stream()
            .filter(v -> v.getUser().getId().equals(userId) &&
                        v.getCreatedAt().getYear() == currentYear)
            .count();
    }

    private Map<String, Object> convertVotacionToSummary(Votacion votacion) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", votacion.getId());
        summary.put("titulo", votacion.getTitulo());
        summary.put("estado", votacion.getEstado());
        summary.put("categoria", votacion.getCategoria());
        summary.put("fechaCreacion", votacion.getCreatedAt());
        summary.put("creador", votacion.getCreador() != null ? votacion.getCreador().getFullName() : "N/A");
        summary.put("totalVotos", voteRepository.countByVotacionId(votacion.getId()));
        return summary;
    }

    private Map<String, Object> convertVotacionToDetailedSummary(Votacion votacion) {
        Map<String, Object> summary = convertVotacionToSummary(votacion);
        summary.put("fechaInicio", votacion.getFechaInicio());
        summary.put("fechaFin", votacion.getFechaFin());
        summary.put("descripcion", votacion.getDescripcion());
        return summary;
    }

    private Map<String, Object> convertVoteToSummary(Vote vote) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", vote.getId());
        summary.put("fechaVoto", vote.getCreatedAt());
        summary.put("votacionTitulo", vote.getVotacion() != null ? vote.getVotacion().getTitulo() : "N/A");
        summary.put("blockchainVerified", vote.isBlockchainVerified());
        // No incluimos información personal del votante por privacidad
        return summary;
    }

    private Map<String, Object> convertVoteToUserSummary(Vote vote) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", vote.getId());
        summary.put("fechaVoto", vote.getCreatedAt());
        summary.put("votacionTitulo", vote.getVotacion() != null ? vote.getVotacion().getTitulo() : "N/A");
        summary.put("votacionId", vote.getVotacion() != null ? vote.getVotacion().getId() : null);
        summary.put("opcionSeleccionada", vote.getOpcionSeleccionada() != null ? vote.getOpcionSeleccionada().getTitulo() : "N/A");
        summary.put("blockchainVerified", vote.isBlockchainVerified());
        return summary;
    }

    private double calculateAverageParticipation() {
        List<Votacion> allVotaciones = votacionRepository.findAll();
        if (allVotaciones.isEmpty()) return 0.0;

        long totalUsers = userRepository.count();
        if (totalUsers == 0) return 0.0;

        double totalParticipation = allVotaciones.stream()
            .mapToDouble(votacion -> {
                long votes = voteRepository.countByVotacionId(votacion.getId());
                return (votes * 100.0) / totalUsers;
            })
            .sum();

        return totalParticipation / allVotaciones.size();
    }

    private Map<String, Object> findHighestParticipationVoting() {
        List<Votacion> allVotaciones = votacionRepository.findAll();
        long totalUsers = userRepository.count();

        return allVotaciones.stream()
            .map(votacion -> {
                long votes = voteRepository.countByVotacionId(votacion.getId());
                double participation = totalUsers > 0 ? (votes * 100.0) / totalUsers : 0.0;

                Map<String, Object> info = new HashMap<>();
                info.put("votacion", convertVotacionToSummary(votacion));
                info.put("participacion", participation);
                info.put("totalVotos", votes);
                return info;
            })
            .max(Comparator.comparing(map -> (Double) map.get("participacion")))
            .orElse(Map.of("mensaje", "No hay votaciones disponibles"));
    }

    private List<Map<String, Object>> getTopActiveVotaciones() {
        List<Votacion> allVotaciones = votacionRepository.findAll();

        return allVotaciones.stream()
            .map(votacion -> {
                Map<String, Object> info = convertVotacionToSummary(votacion);
                info.put("totalVotos", voteRepository.countByVotacionId(votacion.getId()));
                return info;
            })
            .sorted((a, b) -> Long.compare((Long) b.get("totalVotos"), (Long) a.get("totalVotos")))
            .limit(5)
            .collect(Collectors.toList());
    }

    private Map<String, Long> getVotacionesByMonth() {
        // Implementar lógica para obtener votaciones por mes
        // Por simplicidad, retornamos un ejemplo
        Map<String, Long> votacionesPorMes = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 6; i++) {
            LocalDateTime month = now.minusMonths(i);
            String monthKey = month.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            long count = votacionRepository.countByCreatedAtBetween(
                month.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0),
                month.withDayOfMonth(month.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59).withSecond(59)
            );
            votacionesPorMes.put(monthKey, count);
        }

        return votacionesPorMes;
    }

    private Map<String, Long> getVotesByDay() {
        Map<String, Long> votosPorDia = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 7; i++) {
            LocalDateTime day = now.minusDays(i);
            String dayKey = day.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            long count = voteRepository.countByCreatedAtBetween(
                day.withHour(0).withMinute(0).withSecond(0),
                day.withHour(23).withMinute(59).withSecond(59)
            );
            votosPorDia.put(dayKey, count);
        }

        return votosPorDia;
    }

    private Map<String, Long> getUserRegistrationsByMonth() {
        Map<String, Long> registrosPorMes = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < 6; i++) {
            LocalDateTime month = now.minusMonths(i);
            String monthKey = month.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            long count = userRepository.countByCreatedAtBetween(
                month.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0),
                month.withDayOfMonth(month.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59).withSecond(59)
            );
            registrosPorMes.put(monthKey, count);
        }

        return registrosPorMes;
    }

    private double calculateBlockchainVerificationPercentage() {
        long totalVotes = voteRepository.count();
        if (totalVotes == 0) return 0.0;

        long verifiedVotes = voteRepository.countByBlockchainVerifiedTrue();
        return (verifiedVotes * 100.0) / totalVotes;
    }

    private Map<String, Object> getMostSuccessfulVotacion(Long userId) {
        List<Votacion> userVotaciones = votacionRepository.findByCreadorId(userId);

        return userVotaciones.stream()
            .map(votacion -> {
                Map<String, Object> info = convertVotacionToDetailedSummary(votacion);
                info.put("totalVotos", voteRepository.countByVotacionId(votacion.getId()));
                return info;
            })
            .max(Comparator.comparing(map -> (Long) map.get("totalVotos")))
            .orElse(Map.of("mensaje", "No tienes votaciones creadas"));
    }

    private Map<String, Object> getParticipationInUserVotings(Long userId) {
        List<Votacion> userVotaciones = votacionRepository.findByCreadorId(userId);
        long totalUsers = userRepository.count();

        if (userVotaciones.isEmpty()) {
            return Map.of("mensaje", "No tienes votaciones creadas");
        }

        double averageParticipation = userVotaciones.stream()
            .mapToDouble(votacion -> {
                long votes = voteRepository.countByVotacionId(votacion.getId());
                return totalUsers > 0 ? (votes * 100.0) / totalUsers : 0.0;
            })
            .average()
            .orElse(0.0);

        Map<String, Object> participation = new HashMap<>();
        participation.put("promedioParticipacion", averageParticipation);
        participation.put("totalVotacionesCreadas", userVotaciones.size());
        participation.put("totalVotosRecibidos", userVotaciones.stream()
            .mapToLong(votacion -> voteRepository.countByVotacionId(votacion.getId()))
            .sum());

        return participation;
    }

    private Map<String, Object> getLastUserActivity(Long userId) {
        Map<String, Object> activity = new HashMap<>();

        // Última votación creada
        Optional<Votacion> lastVotacion = votacionRepository.findFirstByCreadorIdOrderByCreatedAtDesc(userId);
        if (lastVotacion.isPresent()) {
            activity.put("ultimaVotacionCreada", Map.of(
                "titulo", lastVotacion.get().getTitulo(),
                "fecha", lastVotacion.get().getCreatedAt()
            ));
        }

        // Último voto realizado
        Optional<Vote> lastVote = voteRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        if (lastVote.isPresent()) {
            activity.put("ultimoVotoRealizado", Map.of(
                "votacionTitulo", lastVote.get().getVotacion().getTitulo(),
                "fecha", lastVote.get().getCreatedAt()
            ));
        }

        return activity;
    }
}
