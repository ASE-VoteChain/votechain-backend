package com.votechain.backend.auth.service;

import com.votechain.backend.auth.dto.AuthRequest;
import com.votechain.backend.auth.dto.AuthResponse;
import com.votechain.backend.auth.dto.RegisterRequest;
import com.votechain.backend.auth.model.User;
import com.votechain.backend.auth.model.UserRole;
import com.votechain.backend.auth.model.UserStatus;
import com.votechain.backend.auth.repository.UserRepository;
import com.votechain.backend.security.JwtTokenProvider;
import com.votechain.backend.security.UserDetailsImpl;
import com.votechain.backend.common.logging.SystemLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private SystemLogService systemLogService;

    /**
     * Authenticate a user and generate JWT tokens
     */
    public AuthResponse authenticate(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String accessToken = tokenProvider.generateToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        systemLogService.logUserAuthentication(userDetails.getId(), request.getEmail());

        return AuthResponse.builder()
                .userId(userDetails.getId())
                .email(userDetails.getUsername())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
    }

    /**
     * Register a new user
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already taken");
        }

        // Check if DNI already exists
        if (userRepository.existsByDni(request.getDni())) {
            throw new RuntimeException("DNI is already registered");
        }

        // Create new user
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .dni(request.getDni())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.ROLE_USER) // Default role
                .status(UserStatus.ACTIVE) // Auto-activate for demo
                .build();

        userRepository.save(user);

        systemLogService.logUserRegistration(user);

        // Authenticate user after registration
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String accessToken = tokenProvider.generateToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
    }

    /**
     * Refresh the JWT token
     */
    public AuthResponse refreshToken(String refreshToken) {
        // Validate refresh token
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid refresh token");
        }

        // Get user ID from refresh token
        Long userId = tokenProvider.getUserIdFromJWT(refreshToken);

        // Create a new authentication token
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Generate new tokens
        String newAccessToken = tokenProvider.generateToken(authentication);
        String newRefreshToken = tokenProvider.generateRefreshToken(authentication);

        systemLogService.logTokenRefresh(userId);

        return AuthResponse.builder()
                .userId(userId)
                .email(tokenProvider.getUsernameFromJWT(refreshToken))
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .build();
    }
}
