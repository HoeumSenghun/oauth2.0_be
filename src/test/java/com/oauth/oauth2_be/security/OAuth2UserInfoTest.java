package com.oauth.oauth2_be.security;

import com.oauth.oauth2_be.entity.AuthProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2UserInfoTest {

    @Test
    void googleExtractsProfileFromOAuth2UserAttributes() {
        OAuth2User user = new DefaultOAuth2User(
                List.of(),
                Map.of(
                        "sub", "google-sub-1",
                        "email", "ada@example.com",
                        "name", "Ada Lovelace",
                        "picture", "https://example.com/ada.png"
                ),
                "sub"
        );

        OAuth2UserInfo.Profile profile = OAuth2UserInfo.google(user);

        assertThat(profile.provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(profile.providerId()).isEqualTo("google-sub-1");
        assertThat(profile.email()).isEqualTo("ada@example.com");
        assertThat(profile.name()).isEqualTo("Ada Lovelace");
        assertThat(profile.avatarUrl()).isEqualTo("https://example.com/ada.png");
    }

    @Test
    void googleFallsBackToOidcClaimsWhenAttributesAreBlank() {
        OidcIdToken idToken = new OidcIdToken(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of(
                        IdTokenClaimNames.SUB, "oidc-sub-1",
                        IdTokenClaimNames.ISS, "https://accounts.google.com",
                        "email", "grace@example.com",
                        "name", "Grace Hopper",
                        "picture", "https://example.com/grace.png"
                )
        );
        OAuth2User user = new DefaultOidcUser(List.of(), idToken);

        OAuth2UserInfo.Profile profile = OAuth2UserInfo.google(user);

        assertThat(profile.providerId()).isEqualTo("oidc-sub-1");
        assertThat(profile.email()).isEqualTo("grace@example.com");
        assertThat(profile.name()).isEqualTo("Grace Hopper");
        assertThat(profile.avatarUrl()).isEqualTo("https://example.com/grace.png");
    }

    @Test
    void googleReturnsNullProviderIdWhenSubjectMissing() {
        OAuth2User user = new DefaultOAuth2User(
                List.of(),
                Map.of("name", "no-sub-attribute", "email", "x@example.com"),
                "name"
        );

        OAuth2UserInfo.Profile profile = OAuth2UserInfo.google(user);

        assertThat(profile.providerId()).isNull();
    }

    @Test
    void githubExtractsProfileWhenEmailIsPresent() {
        OAuth2User user = new DefaultOAuth2User(
                List.of(),
                Map.of(
                        "id", 123456,
                        "login", "octocat",
                        "email", "octocat@example.com",
                        "name", "The Octocat",
                        "avatar_url", "https://example.com/octocat.png"
                ),
                "login"
        );

        OAuth2UserInfo.Profile profile = OAuth2UserInfo.github(user);

        assertThat(profile.provider()).isEqualTo(AuthProvider.GITHUB);
        assertThat(profile.providerId()).isEqualTo("123456");
        assertThat(profile.email()).isEqualTo("octocat@example.com");
        assertThat(profile.name()).isEqualTo("The Octocat");
        assertThat(profile.avatarUrl()).isEqualTo("https://example.com/octocat.png");
    }

    @Test
    void githubFallsBackToNoreplyEmailWhenEmailIsPrivate() {
        OAuth2User user = new DefaultOAuth2User(
                List.of(),
                Map.of("id", 789, "login", "shy-dev"),
                "login"
        );

        OAuth2UserInfo.Profile profile = OAuth2UserInfo.github(user);

        assertThat(profile.email()).isEqualTo("789+shy-dev@users.noreply.github.com");
        assertThat(profile.name()).isEqualTo("shy-dev");
    }

    @Test
    void githubFallsBackToNoreplyEmailWithoutLoginWhenLoginIsMissing() {
        OAuth2User user = new DefaultOAuth2User(
                List.of(),
                Map.of("id", 42, "name", "No Login"),
                "name"
        );

        OAuth2UserInfo.Profile profile = OAuth2UserInfo.github(user);

        assertThat(profile.email()).isEqualTo("42@users.noreply.github.com");
    }

    @Test
    void githubReturnsNullProviderIdWhenIdAttributeMissing() {
        OAuth2User user = new DefaultOAuth2User(
                List.of(),
                Map.of("login", "no-id", "name", "No Id"),
                "login"
        );

        OAuth2UserInfo.Profile profile = OAuth2UserInfo.github(user);

        assertThat(profile.providerId()).isNull();
    }
}
