package com.oauth.oauth2_be.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-instance fallback used when no Redis is available (local dev without Redis running,
 * or the test suite). Not safe for a multi-instance deployment: revocations here are invisible
 * to any other process. Production should run with app.revocation.store=redis.
 */
@Service
@ConditionalOnProperty(prefix = "app.revocation", name = "store", havingValue = "in-memory")
public class InMemoryTokenRevocationService implements TokenRevocationService {

    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    @Override
    public void revoke(String jti, Duration ttl) {
        revokedUntil.put(jti, Instant.now().plus(ttl));
    }

    @Override
    public boolean isRevoked(String jti) {
        Instant expiresAt = revokedUntil.get(jti);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            revokedUntil.remove(jti);
            return false;
        }
        return true;
    }
}
