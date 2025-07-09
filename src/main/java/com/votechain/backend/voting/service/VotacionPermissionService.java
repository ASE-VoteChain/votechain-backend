package com.votechain.backend.voting.service;

import com.votechain.backend.auth.model.User;
import com.votechain.backend.auth.model.UserRole;
import com.votechain.backend.voting.model.Votacion;
import org.springframework.stereotype.Service;

@Service
public class VotacionPermissionService {

    /**
     * Verifica si el usuario puede crear votaciones
     */
    public boolean canCreateVotacion(User user) {
        return user != null && (
            user.getRole() == UserRole.ROLE_USER ||
            user.getRole() == UserRole.ROLE_ADMIN ||
            user.getRole() == UserRole.ROLE_SUPERVISOR
        );
    }

    /**
     * Verifica si el usuario puede editar una votación específica
     */
    public boolean canEditVotacion(User user, Votacion votacion) {
        if (user == null || votacion == null) {
            return false;
        }

        // Admin puede editar cualquier votación
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return true;
        }

        // El creador puede editar su propia votación
        if (votacion.getCreador() != null && votacion.getCreador().getId().equals(user.getId())) {
            return true;
        }

        return false;
    }

    /**
     * Verifica si el usuario puede eliminar una votación específica
     */
    public boolean canDeleteVotacion(User user, Votacion votacion) {
        if (user == null || votacion == null) {
            return false;
        }

        // Admin puede eliminar cualquier votación
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return true;
        }

        // El creador puede eliminar su propia votación
        if (votacion.getCreador() != null && votacion.getCreador().getId().equals(user.getId())) {
            return true;
        }

        return false;
    }

    /**
     * Verifica si el usuario puede cambiar el estado de una votación específica
     */
    public boolean canChangeVotacionStatus(User user, Votacion votacion) {
        if (user == null || votacion == null) {
            return false;
        }

        // Admin puede cambiar el estado de cualquier votación
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return true;
        }

        // El creador puede cambiar el estado de su propia votación
        if (votacion.getCreador() != null && votacion.getCreador().getId().equals(user.getId())) {
            return true;
        }

        return false;
    }

    /**
     * Verifica si el usuario puede finalizar una votación específica
     */
    public boolean canFinalizeVotacion(User user, Votacion votacion) {
        if (user == null || votacion == null) {
            return false;
        }

        // Admin puede finalizar cualquier votación
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return true;
        }

        // El creador puede finalizar su propia votación
        if (votacion.getCreador() != null && votacion.getCreador().getId().equals(user.getId())) {
            return true;
        }

        return false;
    }

    /**
     * Verifica si el usuario puede suspender una votación específica
     */
    public boolean canSuspendVotacion(User user, Votacion votacion) {
        if (user == null || votacion == null) {
            return false;
        }

        // Admin puede suspender cualquier votación
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return true;
        }

        // El creador puede suspender su propia votación
        if (votacion.getCreador() != null && votacion.getCreador().getId().equals(user.getId())) {
            return true;
        }

        return false;
    }

    /**
     * Verifica si el usuario puede reanudar una votación específica
     */
    public boolean canResumeVotacion(User user, Votacion votacion) {
        if (user == null || votacion == null) {
            return false;
        }

        // Admin puede reanudar cualquier votación
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return true;
        }

        // El creador puede reanudar su propia votación
        if (votacion.getCreador() != null && votacion.getCreador().getId().equals(user.getId())) {
            return true;
        }

        return false;
    }

    /**
     * Verifica si el usuario puede cancelar una votación específica
     */
    public boolean canCancelVotacion(User user, Votacion votacion) {
        if (user == null || votacion == null) {
            return false;
        }

        // Admin puede cancelar cualquier votación
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return true;
        }

        // El creador puede cancelar su propia votación
        if (votacion.getCreador() != null && votacion.getCreador().getId().equals(user.getId())) {
            return true;
        }

        return false;
    }

    /**
     * Verifica si el usuario puede ver las estadísticas de una votación
     * Todos los usuarios pueden ver estadísticas
     */
    public boolean canViewStatistics(User user) {
        return user != null; // Todos los usuarios autenticados pueden ver estadísticas
    }

    /**
     * Verifica si el usuario puede ver todas las votaciones (vista admin)
     */
    public boolean canViewAllVotaciones(User user) {
        return user != null && user.getRole() == UserRole.ROLE_ADMIN;
    }

    /**
     * Verifica si el usuario es el creador de la votación
     */
    public boolean isVotacionCreator(User user, Votacion votacion) {
        if (user == null || votacion == null || votacion.getCreador() == null) {
            return false;
        }
        return votacion.getCreador().getId().equals(user.getId());
    }

    /**
     * Verifica si el usuario es admin
     */
    public boolean isAdmin(User user) {
        return user != null && user.getRole() == UserRole.ROLE_ADMIN;
    }
}
