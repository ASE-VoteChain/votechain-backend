package com.votechain.backend.auth.service;

import com.votechain.backend.auth.dto.UserDto;
import com.votechain.backend.auth.dto.UserUpdateRequest;
import com.votechain.backend.auth.model.User;
import com.votechain.backend.auth.repository.UserRepository;
import com.votechain.backend.common.logging.SystemLogService;
import com.votechain.backend.vote.repository.VoteRepository;
import com.votechain.backend.voting.repository.VotacionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VotacionRepository votacionRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SystemLogService systemLogService;

    /**
     * Get complete user profile (for authenticated user)
     */
    public UserDto getUserProfile(Long userId) {
        log.info("👤 Obteniendo perfil completo del usuario {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // Get statistics
        long totalVotacionesCreadas = votacionRepository.countByCreadorId(userId);
        long totalVotosEmitidos = voteRepository.countByUserId(userId);
        long totalVotacionesDisponibles = votacionRepository.count();

        double participacionPorcentaje = totalVotacionesDisponibles > 0
            ? (totalVotosEmitidos * 100.0 / totalVotacionesDisponibles)
            : 0.0;

        UserDto userDto = convertToDto(user);
        userDto.setTotalVotacionesCreadas(totalVotacionesCreadas);
        userDto.setTotalVotosEmitidos(totalVotosEmitidos);
        userDto.setParticipacionPorcentaje(participacionPorcentaje);

        log.info("✅ Perfil obtenido: {} votaciones creadas, {} votos emitidos",
                totalVotacionesCreadas, totalVotosEmitidos);

        return userDto;
    }

    /**
     * Get public user information (limited info)
     */
    public UserDto getUserPublicInfo(Long userId) {
        log.info("🌐 Obteniendo información pública del usuario {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        if (!user.isActive()) {
            throw new EntityNotFoundException("User is not active");
        }

        // Get only public statistics
        long totalVotacionesCreadas = votacionRepository.countByCreadorId(userId);

        UserDto userDto = convertToDto(user);
        userDto.setTotalVotacionesCreadas(totalVotacionesCreadas);

        // Return only public information
        return UserDto.createPublicView(userDto);
    }

    /**
     * Update user profile
     */
    @Transactional
    public UserDto updateUserProfile(Long userId, UserUpdateRequest request) {
        log.info("✏️ Actualizando perfil del usuario {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // Check if email is already taken by another user
        if (!user.getEmail().equals(request.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalStateException("Email ya está en uso por otro usuario");
            }
        }

        // Check if DNI is already taken by another user
        if (request.getDni() != null && !request.getDni().equals(user.getDni())) {
            if (userRepository.existsByDni(request.getDni())) {
                throw new IllegalStateException("DNI ya está en uso por otro usuario");
            }
        }

        // Update user fields
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setDni(request.getDni());
        user.setTelefono(request.getTelefono());
        user.setDireccion(request.getDireccion());
        user.setCiudad(request.getCiudad());
        user.setCodigoPostal(request.getCodigoPostal());
        user.setBiografia(request.getBiografia());

        User savedUser = userRepository.save(user);

        // Log the action
        systemLogService.logUserAction(userId, "Update Profile",
                "Profile updated: " + savedUser.getFullName());

        log.info("✅ Perfil actualizado para usuario {}: {}", userId, savedUser.getFullName());

        return getUserProfile(userId); // Return complete profile with statistics
    }

    /**
     * Get all users with pagination and search (admin only)
     */
    public Page<UserDto> getAllUsers(int page, int size, String search) {
        log.info("👥 Obteniendo lista de usuarios (página {}, tamaño {}, búsqueda: '{}')",
                page, size, search);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<User> users;
        if (search != null && !search.trim().isEmpty()) {
            users = userRepository.searchUsers(search, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(this::convertToDto);
    }

    /**
     * Change user password
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        log.info("🔒 Cambiando contraseña del usuario {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Contraseña actual incorrecta");
        }

        // Validate new password
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Log the action
        systemLogService.logUserAction(userId, "Change Password",
                "Password changed successfully");

        log.info("✅ Contraseña cambiada exitosamente para usuario {}", userId);
    }

    /**
     * Get user statistics
     */
    public Map<String, Object> getUserStatistics(Long userId) {
        log.info("📊 Obteniendo estadísticas del usuario {}", userId);

        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }

        Map<String, Object> statistics = new HashMap<>();

        // Voting statistics
        long totalVotacionesCreadas = votacionRepository.countByCreadorId(userId);
        long totalVotosEmitidos = voteRepository.countByUserId(userId);
        long totalVotacionesDisponibles = votacionRepository.count();

        // Participation percentage
        double participacionPorcentaje = totalVotacionesDisponibles > 0
            ? (totalVotosEmitidos * 100.0 / totalVotacionesDisponibles)
            : 0.0;

        // Recent activity
        long votosUltimoMes = voteRepository.countByUserIdAndCreatedAtAfter(
                userId, LocalDateTime.now().minusMonths(1));

        long votacionesCreadasUltimoMes = votacionRepository.countByCreadorIdAndCreatedAtAfter(
                userId, LocalDateTime.now().minusMonths(1));

        statistics.put("totalVotacionesCreadas", totalVotacionesCreadas);
        statistics.put("totalVotosEmitidos", totalVotosEmitidos);
        statistics.put("participacionPorcentaje", participacionPorcentaje);
        statistics.put("votosUltimoMes", votosUltimoMes);
        statistics.put("votacionesCreadasUltimoMes", votacionesCreadasUltimoMes);
        statistics.put("generadoEn", LocalDateTime.now());

        log.info("📈 Estadísticas generadas: {} votaciones, {} votos, {:.1f}% participación",
                totalVotacionesCreadas, totalVotosEmitidos, participacionPorcentaje);

        return statistics;
    }

    /**
     * Deactivate user account
     */
    @Transactional
    public void deactivateUser(Long userId) {
        log.info("🚫 Desactivando cuenta del usuario {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        user.setActive(false);
        userRepository.save(user);

        // Log the action
        systemLogService.logUserAction(userId, "Deactivate Account",
                "Account deactivated by user");

        log.info("✅ Cuenta desactivada para usuario {}: {}", userId, user.getFullName());
    }

    /**
     * Convert User entity to UserDto
     */
    private UserDto convertToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .dni(user.getDni())
                .telefono(user.getTelefono())
                .direccion(user.getDireccion())
                .ciudad(user.getCiudad())
                .codigoPostal(user.getCodigoPostal())
                .biografia(user.getBiografia())
                .role(user.getRole())
                .status(user.getStatus())
                .active(user.isActive())
                .twoFactorEnabled(user.isTwoFactorEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }
}
