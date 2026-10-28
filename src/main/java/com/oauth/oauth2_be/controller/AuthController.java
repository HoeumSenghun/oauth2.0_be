package com.oauth.oauth2_be.controller;

import com.oauth.oauth2_be.dto.UserResponse;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.security.AccessTokenCookie;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AccessTokenCookie accessTokenCookie;

    @GetMapping("/me")
    @Operation(summary = "Current user from the access_token cookie")
    public UserResponse me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return UserResponse.from(user);
    }

    @PostMapping("/logout")
    @Operation(summary = "Clear the access_token cookie")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, accessTokenCookie.clear().toString());
        return ResponseEntity.noContent().build();
    }
}
