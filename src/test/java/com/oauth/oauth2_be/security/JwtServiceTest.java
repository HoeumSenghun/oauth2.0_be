package com.oauth.oauth2_be.security;

import com.oauth.oauth2_be.entity.Role;
import com.oauth.oauth2_be.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-key-must-be-32-chars";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);

    @Test
    void validTokenContainsUserIdAndRole() {
        User user = user(42L);
        String token = jwtService.generateToken(user);

        assertThat(jwtService.validate(token)).isTrue();
        assertThat(jwtService.getUserId(token)).isEqualTo(42L);
        assertThat(jwtService.parse(token).get("role")).isEqualTo(Role.USER.name());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expiredService = new JwtService(SECRET, -60_000);
        String token = expiredService.generateToken(user(1L));

        assertThat(expiredService.validate(token)).isFalse();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(user(1L));
        String[] parts = token.split("\\.");
        String signature = parts[2];
        int index = signature.length() / 2;
        char replacement = signature.charAt(index) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "."
                + signature.substring(0, index) + replacement + signature.substring(index + 1);

        assertThat(jwtService.validate(tampered)).isFalse();
    }

    private static User user(Long id) {
        return User.builder()
                .id(id)
                .email("ada@example.com")
                .role(Role.USER)
                .build();
    }
}
