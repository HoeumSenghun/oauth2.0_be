package com.oauth.oauth2_be.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Revoked JWTs are tracked by their jti, not their raw value, so a Redis dump never contains
 * a usable bearer credential. Entries expire automatically once the token would have expired
 * anyway, so the revocation list never outgrows the set of currently-valid tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.revocation", name = "store", havingValue = "redis", matchIfMissing = true)
public class RedisTokenRevocationService implements TokenRevocationService {

    private static final String KEY_PREFIX = "revoked:";
    private static final Duration MIN_TTL = Duration.ofSeconds(1);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void revoke(String jti, Duration ttl) {
        Duration effectiveTtl = ttl.compareTo(MIN_TTL) < 0 ? MIN_TTL : ttl;
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", effectiveTtl);
        } catch (DataAccessException ex) {
            // Logout still clears the client's cookie even if the revocation entry can't be
            // written; this is a known, documented limitation when Redis is unreachable.
            log.error("Failed to record JWT revocation in Redis; token will remain valid until it expires naturally", ex);
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (DataAccessException ex) {
            // Fail closed: if revocation status can't be verified, don't trust the token.
            log.error("Failed to check JWT revocation status in Redis; rejecting the token", ex);
            return true;
        }
    }
}
