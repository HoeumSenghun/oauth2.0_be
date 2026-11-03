package com.oauth.oauth2_be.service;

import com.oauth.oauth2_be.entity.RefreshToken;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-expiration}") long refreshExpirationMs
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public record Rotated(User user, String rawToken) {
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build());

        return rawToken;
    }

    @Transactional
    public Rotated rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        if (existing.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token has already been used");
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        existing.setRevoked(true);
        User user = existing.getUser();
        return new Rotated(user, issue(user));
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.setRevoked(true));
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
