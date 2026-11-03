package com.oauth.oauth2_be.service;

import com.oauth.oauth2_be.entity.RefreshToken;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, 60_000L);
    }

    @Test
    void issueStoresHashNotRawTokenAndReturnsRawToken() {
        User user = User.builder().id(1L).build();

        String rawToken = refreshTokenService.issue(user);

        assertThat(rawToken).isNotBlank();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(captor.getValue().isRevoked()).isFalse();
        assertThat(captor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void rotateReturnsNewTokenAndRevokesTheOldOne() {
        User user = User.builder().id(2L).build();
        RefreshToken existing = RefreshToken.builder()
                .user(user)
                .tokenHash(hashOf("old-raw-token"))
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(hashOf("old-raw-token"))).thenReturn(Optional.of(existing));

        RefreshTokenService.Rotated rotated = refreshTokenService.rotate("old-raw-token");

        assertThat(existing.isRevoked()).isTrue();
        assertThat(rotated.user()).isSameAs(user);
        assertThat(rotated.rawToken()).isNotEqualTo("old-raw-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void rotateThrowsForUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("unknown-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateThrowsForAlreadyRevokedToken() {
        RefreshToken revoked = RefreshToken.builder()
                .user(User.builder().id(3L).build())
                .tokenHash(hashOf("reused-token"))
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByTokenHash(hashOf("reused-token"))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate("reused-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rotateThrowsForExpiredToken() {
        RefreshToken expired = RefreshToken.builder()
                .user(User.builder().id(4L).build())
                .tokenHash(hashOf("expired-token"))
                .expiresAt(Instant.now().minusSeconds(1))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(hashOf("expired-token"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeIsNoOpForUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        refreshTokenService.revoke("unknown-token");

        verify(refreshTokenRepository).findByTokenHash(any());
    }

    @Test
    void revokeMarksKnownTokenAsRevoked() {
        RefreshToken token = RefreshToken.builder()
                .user(User.builder().id(5L).build())
                .tokenHash(hashOf("active-token"))
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenHash(hashOf("active-token"))).thenReturn(Optional.of(token));

        refreshTokenService.revoke("active-token");

        assertThat(token.isRevoked()).isTrue();
    }

    private static String hashOf(String rawToken) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
