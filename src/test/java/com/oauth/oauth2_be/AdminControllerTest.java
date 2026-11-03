package com.oauth.oauth2_be;

import com.oauth.oauth2_be.entity.AuthProvider;
import com.oauth.oauth2_be.entity.Role;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.repository.RefreshTokenRepository;
import com.oauth.oauth2_be.repository.UserRepository;
import com.oauth.oauth2_be.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanUsers() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void listUsersUnauthenticatedIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void listUsersAsNonAdminIsForbidden() throws Exception {
        User regularUser = userRepository.save(User.builder()
                .email("user@example.com")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-user")
                .role(Role.USER)
                .build());
        String token = jwtService.generateToken(regularUser);

        mockMvc.perform(get("/api/admin/users").cookie(new Cookie("access_token", token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void listUsersAsAdminReturnsAllUsers() throws Exception {
        User admin = userRepository.save(User.builder()
                .email("admin@example.com")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-admin")
                .role(Role.ADMIN)
                .build());
        userRepository.save(User.builder()
                .email("other@example.com")
                .provider(AuthProvider.GITHUB)
                .providerId("github-other")
                .role(Role.USER)
                .build());
        String token = jwtService.generateToken(admin);

        mockMvc.perform(get("/api/admin/users").cookie(new Cookie("access_token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateRoleAsAdminChangesTargetUserRole() throws Exception {
        User admin = userRepository.save(User.builder()
                .email("admin2@example.com")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-admin-2")
                .role(Role.ADMIN)
                .build());
        User target = userRepository.save(User.builder()
                .email("target@example.com")
                .provider(AuthProvider.GITHUB)
                .providerId("github-target")
                .role(Role.USER)
                .build());
        String token = jwtService.generateToken(admin);

        mockMvc.perform(patch("/api/admin/users/{id}/role", target.getId())
                        .cookie(new Cookie("access_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void updateRoleAsNonAdminIsForbidden() throws Exception {
        User regularUser = userRepository.save(User.builder()
                .email("user2@example.com")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-user-2")
                .role(Role.USER)
                .build());
        String token = jwtService.generateToken(regularUser);

        mockMvc.perform(patch("/api/admin/users/{id}/role", regularUser.getId())
                        .cookie(new Cookie("access_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void updateRoleForUnknownUserIsNotFound() throws Exception {
        User admin = userRepository.save(User.builder()
                .email("admin3@example.com")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-admin-3")
                .role(Role.ADMIN)
                .build());
        String token = jwtService.generateToken(admin);

        mockMvc.perform(patch("/api/admin/users/{id}/role", 999_999L)
                        .cookie(new Cookie("access_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }
}
