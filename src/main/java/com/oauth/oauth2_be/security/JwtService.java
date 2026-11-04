package com.oauth.oauth2_be.security;

import com.oauth.oauth2_be.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final String issuer;
    private final String audience;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expirationMs,
            @Value("${app.jwt.issuer:oauth2-be}") String issuer,
            @Value("${app.jwt.audience:oauth2-be-clients}") String audience
    ) {
        String value = secret == null ? "" : secret.trim();
        if (value.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is missing or shorter than 32 characters. "
                            + "Add it in the host environment (Render: Environment → JWT_SECRET) "
                            + "and redeploy. Do not commit this value."
            );
        }
        this.key = Keys.hmacShaKeyFor(value.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!claims.getAudience().contains(audience)) {
            throw new JwtException("Unexpected JWT audience");
        }
        if (claims.getId() == null || claims.getId().isBlank()) {
            throw new JwtException("JWT is missing required jti claim");
        }
        return claims;
    }

    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    public String getJti(String token) {
        return parse(token).getId();
    }

    /** Remaining validity, floored at zero for an already-expired (but still structurally valid) token. */
    public Duration getRemainingValidity(String token) {
        Instant expiration = parse(token).getExpiration().toInstant();
        Duration remaining = Duration.between(Instant.now(), expiration);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
