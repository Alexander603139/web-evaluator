package com.webevaluator.auth.controller;

import com.webevaluator.auth.dto.LoginRequest;
import com.webevaluator.auth.dto.TokenResponse;
import com.webevaluator.auth.security.JwtTokenProvider;
import com.webevaluator.core.domain.RefreshToken;
import com.webevaluator.core.domain.User;
import com.webevaluator.auth.repository.RefreshTokenRepository;
import com.webevaluator.auth.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        // УЛУЧШЕНИЕ 2: Сохраняем хэш refresh-токена в БД для возможности отзыва
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .tokenHash(jwtTokenProvider.hashRefreshToken(refreshToken))
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        refreshTokenRepository.save(rt);

        return ResponseEntity.ok(TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900000)
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String refreshToken = authHeader.substring(7);
        String tokenHash = jwtTokenProvider.hashRefreshToken(refreshToken);

        // УЛУЧШЕНИЕ 2: Проверяем наличие и статус в БД
        RefreshToken rt = refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid or revoked refresh token"));

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(401).build();
        }

        User user = rt.getUser();
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getRole().name());

        return ResponseEntity.ok(TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900000)
                .build());
    }
}