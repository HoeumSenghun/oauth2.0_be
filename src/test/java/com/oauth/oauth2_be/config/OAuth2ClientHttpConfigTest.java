package com.oauth.oauth2_be.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for OAuth2ClientHttpConfig: replacing Spring Security's default OAuth2
 * HTTP clients with timeout-bound ones must not silently drop the message converters/error
 * handling those defaults rely on (a bare RestClient/RestTemplate can't parse a token response
 * at all), and a connection-level failure (e.g. the timeout this class exists to add) must still
 * surface as an OAuth2AuthenticationException rather than an unhandled RestClientException.
 */
class OAuth2ClientHttpConfigTest {

    private static final long CONNECT_TIMEOUT_MS = 300;
    private static final long READ_TIMEOUT_MS = 300;

    private final OAuth2ClientHttpConfig config = new OAuth2ClientHttpConfig(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void tokenResponseClientParsesARealTokenResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            byte[] body = ("{\"access_token\":\"abc123\",\"token_type\":\"Bearer\",\"expires_in\":3600}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String tokenUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";

        OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> client = config.oauth2TokenResponseClient();

        var response = client.getTokenResponse(authorizationCodeGrantRequest(tokenUri));

        assertThat(response.getAccessToken().getTokenValue()).isEqualTo("abc123");
    }

    @Test
    void userInfoServiceConvertsAConnectionFailureIntoAnOAuth2AuthenticationException() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Never responds: read times out well before any bytes are written back.
        server.createContext("/userinfo", exchange -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        String userInfoUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/userinfo";

        OAuth2UserService<OAuth2UserRequest, OAuth2User> userService = config.oauth2UserService();

        assertThatThrownBy(() -> userService.loadUser(userInfoRequest(userInfoUri)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    private static OAuth2AuthorizationCodeGrantRequest authorizationCodeGrantRequest(String tokenUri) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("test")
                .clientId("client-id")
                .clientSecret("client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/test")
                .authorizationUri("http://127.0.0.1/authorize")
                .tokenUri(tokenUri)
                .build();

        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .clientId("client-id")
                .authorizationUri("http://127.0.0.1/authorize")
                .redirectUri("http://localhost/login/oauth2/code/test")
                .state("state")
                .build();
        OAuth2AuthorizationResponse authorizationResponse = OAuth2AuthorizationResponse.success("code")
                .redirectUri("http://localhost/login/oauth2/code/test")
                .state("state")
                .build();

        return new OAuth2AuthorizationCodeGrantRequest(
                registration, new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse)
        );
    }

    private static OAuth2UserRequest userInfoRequest(String userInfoUri) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("test")
                .clientId("client-id")
                .clientSecret("client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/test")
                .authorizationUri("http://127.0.0.1/authorize")
                .tokenUri("http://127.0.0.1/token")
                .userInfoUri(userInfoUri)
                .userNameAttributeName("sub")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "token-value", Instant.now(), Instant.now().plusSeconds(60)
        );
        return new OAuth2UserRequest(registration, accessToken, Map.of());
    }
}
