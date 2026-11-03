package com.oauth.oauth2_be.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenCookieTest {

    @Test
    void createBuildsHttpOnlySecureLaxCookieWithToken() {
        AccessTokenCookie cookie = new AccessTokenCookie(true);

        ResponseCookie result = cookie.create("jwt-value", Duration.ofHours(24));

        assertThat(result.getName()).isEqualTo(AccessTokenCookie.NAME).isEqualTo("access_token");
        assertThat(result.getValue()).isEqualTo("jwt-value");
        assertThat(result.isHttpOnly()).isTrue();
        assertThat(result.isSecure()).isTrue();
        assertThat(result.getSameSite()).isEqualTo("Lax");
        assertThat(result.getPath()).isEqualTo("/");
        assertThat(result.getMaxAge()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void createHonorsSecureFalseConfiguration() {
        AccessTokenCookie cookie = new AccessTokenCookie(false);

        ResponseCookie result = cookie.create("jwt-value", Duration.ofHours(1));

        assertThat(result.isSecure()).isFalse();
    }

    @Test
    void clearProducesEmptyValueWithZeroMaxAge() {
        AccessTokenCookie cookie = new AccessTokenCookie(true);

        ResponseCookie result = cookie.clear();

        assertThat(result.getValue()).isEmpty();
        assertThat(result.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(result.getName()).isEqualTo("access_token");
    }
}
