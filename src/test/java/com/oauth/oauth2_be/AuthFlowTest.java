package com.oauth.oauth2_be;

import com.oauth.oauth2_be.entity.AuthProvider;
import com.oauth.oauth2_be.entity.User;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private JwtService jwtService;

    @BeforeEach
    void cleanUsers() {
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
}
