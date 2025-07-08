package com.votechain.backend.common.logging;

import com.votechain.backend.auth.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SystemLogService {

    @Autowired
    private SystemLogRepository systemLogRepository;

    /**
     * Log user authentication
     */
    public void logUserAuthentication(Long userId, String email) {
        logUserActivity(userId, LogType.AUTH, LogLevel.INFO,
                "User login", "User logged in: " + email);
    }

    /**
     * Log user registration
     */
    public void logUserRegistration(User user) {
        logUserActivity(user.getId(), LogType.AUTH, LogLevel.INFO,
                "User registration", "New user registered: " + user.getEmail());
    }

    /**
     * Log token refresh
     */
    public void logTokenRefresh(Long userId) {
        logUserActivity(userId, LogType.AUTH, LogLevel.INFO,
                "Token refresh", "User refreshed authentication token");
    }

    /**
     * Log vote casting
     */
    public void logVoteCast(Long userId, Long votingId, String voteHash) {
        logUserActivity(userId, LogType.VOTE, LogLevel.INFO,
                "Vote cast", "User cast vote in voting #" + votingId +
                        ", hash: " + voteHash);
    }

    /**
     * Log general user actions (método que faltaba para UserService)
     */
    public void logUserAction(Long userId, String action, String details) {
        logUserActivity(userId, LogType.USER_ACTIVITY, LogLevel.INFO, action, details);
    }

    /**
     * Log vote verification
     */
    public void logVoteVerification(Long userId, String voteHash, boolean verified) {
        LogLevel level = verified ? LogLevel.INFO : LogLevel.WARNING;
        logUserActivity(userId, LogType.VOTE, level,
                "Vote verification", "Vote verification " +
                        (verified ? "succeeded" : "failed") + ", hash: " + voteHash);
    }

    /**
     * Log blockchain interaction
     */
    public void logBlockchainInteraction(Long userId, String action, String transactionHash) {
        logUserActivity(userId, LogType.BLOCKCHAIN, LogLevel.INFO,
                action, "Blockchain transaction: " + transactionHash);
    }

    /**
     * Log admin action
     */
    public void logAdminAction(Long adminId, String action, String details) {
        logUserActivity(adminId, LogType.ADMIN, LogLevel.INFO,
                action, details);
    }

    /**
     * Log system error
     */
    public void logError(String action, String errorDetails) {
        SystemLog log = SystemLog.builder()
                .type(LogType.SYSTEM)
                .level(LogLevel.ERROR)
                .action(action)
                .description(errorDetails)
                .timestamp(LocalDateTime.now())
                .ipAddress(getClientIpAddress())
                .userAgent(getUserAgent())
                .build();

        systemLogRepository.save(log);
    }

    /**
     * Log user activity
     */
    private void logUserActivity(Long userId, LogType type, LogLevel level,
                                  String action, String description) {
        User user = new User();
        user.setId(userId);

        SystemLog log = SystemLog.builder()
                .type(type)
                .level(level)
                .action(action)
                .description(description)
                .user(user)
                .timestamp(LocalDateTime.now())
                .ipAddress(getClientIpAddress())
                .userAgent(getUserAgent())
                .build();

        systemLogRepository.save(log);
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .map(this::extractIpAddress)
                .orElse("Unknown");
    }

    /**
     * Extract the client IP address from a request
     */
    private String extractIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }

    /**
     * Get user agent from request
     */
    private String getUserAgent() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .map(request -> request.getHeader("User-Agent"))
                .orElse("Unknown");
    }
}
