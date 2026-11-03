package com.oauth.oauth2_be;

import com.oauth.oauth2_be.entity.AuthProvider;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.repository.RefreshTokenRepository;
import com.oauth.oauth2_be.repository.UserRepository;
import com.oauth.oauth2_be.security.JwtService;
import com.oauth.oauth2_be.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void cleanUsers() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void pingIsPublic() throws Exception {
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("backend is running"));
    }

    @Test
    void meWithoutCookieIsUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void meWithValidCookieReturnsUser() throws Exception {
        User user = userRepository.save(User.builder()
                .email("ada@example.com")
                .name("Ada")
                .avatarUrl("https://example.com/a.png")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-sub")
                .build());
        String token = jwtService.generateToken(user);

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.name").value("Ada"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/a.png"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void meWithMalformedJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", "not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void meWithJwtForDeletedUserIsUnauthorized() throws Exception {
        User user = userRepository.save(User.builder()
                .email("ghost@example.com")
                .name("Ghost")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-ghost")
                .build());
        String token = jwtService.generateToken(user);
        userRepository.deleteById(user.getId());

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void logoutClearsAccessTokenCookie() throws Exception {
        User user = userRepository.save(User.builder()
                .email("logout@example.com")
                .name("Logout")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-logout")
                .build());
        String token = jwtService.generateToken(user);

        String setCookie = mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("access_token", token)))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn()
                .getResponse()
                .getHeader("Set-Cookie");

        assertThat(setCookie).contains("access_token=");
        assertThat(setCookie).containsAnyOf("Max-Age=0", "Max-Age=-");
    }

    @Test
    void refreshWithValidCookieIssuesNewTokensThatAuthenticate() throws Exception {
        User user = userRepository.save(User.builder()
                .email("refresh@example.com")
                .name("Refresh")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-refresh")
                .build());
        String rawRefreshToken = refreshTokenService.issue(user);

        MockHttpServletResponse response = mockMvc.perform(
                        post("/api/auth/refresh").cookie(new Cookie("refresh_token", rawRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andReturn()
                .getResponse();

        String newAccessToken = cookieValue(response, "access_token");
        String newRefreshToken = cookieValue(response, "refresh_token");
        assertThat(newAccessToken).isNotBlank();
        assertThat(newRefreshToken).isNotBlank().isNotEqualTo(rawRefreshToken);

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", newAccessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()));
    }

    @Test
    void refreshWithMissingCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void refreshWithUnknownCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "not-a-real-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void refreshWithAlreadyRotatedCookieIsUnauthorized() throws Exception {
        User user = userRepository.save(User.builder()
                .email("rotate@example.com")
                .name("Rotate")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-rotate")
                .build());
        String rawRefreshToken = refreshTokenService.issue(user);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", rawRefreshToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", rawRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void logoutRevokesRefreshTokenSoSubsequentRefreshFails() throws Exception {
        User user = userRepository.save(User.builder()
                .email("logout-revoke@example.com")
                .name("LogoutRevoke")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-logout-revoke")
                .build());
        String accessToken = jwtService.generateToken(user);
        String rawRefreshToken = refreshTokenService.issue(user);

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("access_token", accessToken), new Cookie("refresh_token", rawRefreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", rawRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void registerCreatesUserAndIssuesWorkingSession() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"newuser@example.com\",\"password\":\"super-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andReturn()
                .getResponse();

        String accessToken = cookieValue(response, "access_token");
        assertThat(accessToken).isNotBlank();

        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newuser@example.com"));
    }

    @Test
    void registerWithExistingEmailIsConflict() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dupe@example.com\",\"password\":\"super-secret\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dupe@example.com\",\"password\":\"another-secret\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("email_in_use"));
    }

    @Test
    void registerWithShortPasswordIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithCorrectPasswordIssuesWorkingSession() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@example.com\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk());

        MockHttpServletResponse response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@example.com\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andReturn()
                .getResponse();

        String accessToken = cookieValue(response, "access_token");
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrongpass@example.com\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrongpass@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void loginWithUnknownEmailIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"doesnotexist@example.com\",\"password\":\"whatever1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    private static String cookieValue(MockHttpServletResponse response, String name) {
        List<String> setCookies = response.getHeaders("Set-Cookie");
        for (String setCookie : setCookies) {
            if (setCookie.startsWith(name + "=")) {
                return setCookie.split(";")[0].substring(name.length() + 1);
            }
        }
        return null;
    }
}
