package com.oauth.oauth2_be.security;

import com.oauth.oauth2_be.entity.AuthProvider;
import com.oauth.oauth2_be.entity.Role;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.service.RefreshTokenService;
import com.oauth.oauth2_be.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    private static final String FRONTEND_URL = "http://localhost:3000";

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private final JwtService jwtService = new JwtService("test-jwt-secret-key-must-be-32-chars", 60_000);
    private final AccessTokenCookie accessTokenCookie = new AccessTokenCookie(false);
    private final RefreshTokenCookie refreshTokenCookie = new RefreshTokenCookie(false);

    private OAuth2SuccessHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(refreshTokenService.issue(any(User.class))).thenReturn("raw-refresh-token");
        lenient().when(refreshTokenService.getRefreshExpirationMs()).thenReturn(2_592_000_000L);
        handler = new OAuth2SuccessHandler(
                userService, jwtService, accessTokenCookie, refreshTokenCookie, refreshTokenService, FRONTEND_URL
        );
    }

    @Test
    void successfulGoogleLoginSetsCookieAndRedirectsToCallback() throws Exception {
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(),
                Map.of("sub", "google-1", "email", "ada@example.com", "name", "Ada", "picture", "https://x/a.png"),
                "sub"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(oauth2User, List.of(), "google");

        User user = User.builder().id(1L).email("ada@example.com").role(Role.USER).build();
        when(userService.findOrCreate(AuthProvider.GOOGLE, "google-1", "ada@example.com", "Ada", "https://x/a.png"))
                .thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_URL + "/auth/callback");
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).contains("access_token=").contains("HttpOnly");
    }

    @Test
    void existingGithubUserLogsInAndCookieCarriesJwtSubjectMatchingUserId() throws Exception {
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(),
                Map.of("id", 555, "login", "octocat", "email", "octocat@example.com"),
                "login"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(oauth2User, List.of(), "github");

        User user = User.builder().id(7L).email("octocat@example.com").role(Role.USER).build();
        when(userService.findOrCreate(AuthProvider.GITHUB, "555", "octocat@example.com", "octocat", null))
                .thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        String setCookie = response.getHeader("Set-Cookie");
        String token = setCookie.split("access_token=")[1].split(";")[0];
        assertThat(jwtService.getUserId(token)).isEqualTo(7L);
    }

    @Test
    void missingProviderEmailRedirectsWithErrorAndSkipsUserCreation() throws Exception {
        OAuth2User oauth2User = new DefaultOAuth2User(
                List.of(),
                Map.of("sub", "google-2"),
                "sub"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(oauth2User, List.of(), "google");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_URL + "/auth/callback?error=oauth_failed");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        verifyNoInteractions(userService);
        verifyNoInteractions(refreshTokenService);
    }
}
