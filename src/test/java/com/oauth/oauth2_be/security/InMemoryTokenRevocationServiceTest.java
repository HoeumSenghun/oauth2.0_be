package com.oauth.oauth2_be.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTokenRevocationServiceTest {

    private final InMemoryTokenRevocationService service = new InMemoryTokenRevocationService();

    @Test
    void unknownJtiIsNotRevoked() {
        assertThat(service.isRevoked("never-seen")).isFalse();
    }

    @Test
    void revokedJtiIsRevokedUntilItsTtlElapses() {
        service.revoke("jti-1", Duration.ofMillis(50));

        assertThat(service.isRevoked("jti-1")).isTrue();

        await(100);

        assertThat(service.isRevoked("jti-1")).isFalse();
    }

    private static void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
