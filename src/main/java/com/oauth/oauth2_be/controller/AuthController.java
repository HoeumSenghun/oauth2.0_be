package com.oauth.oauth2_be.controller;

import com.oauth.oauth2_be.dto.EmailAuthRequest;
import com.oauth.oauth2_be.dto.UserResponse;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.security.AccessTokenCookie;
import com.oauth.oauth2_be.security.CookieUtils;
import com.oauth.oauth2_be.security.JwtService;
import com.oauth.oauth2_be.security.RefreshTokenCookie;
import com.oauth.oauth2_be.service.EmailAlreadyInUseException;
import com.oauth.oauth2_be.service.InvalidCredentialsException;
import com.oauth.oauth2_be.service.InvalidRefreshTokenException;
import com.oauth.oauth2_be.service.RefreshTokenService;
import com.oauth.oauth2_be.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AccessTokenCookie accessTokenCookie;
    private final RefreshTokenCookie refreshTokenCookie;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Current user from the access_token cookie")
    public UserResponse me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return UserResponse.from(user);
    }

    @PostMapping("/register")
    @Operation(summary = "Create a local email/password account and sign in")
    public ResponseEntity<?> register(@Valid @RequestBody EmailAuthRequest request, HttpServletResponse response) {
        try {
            User user = userService.registerLocal(request.email(), request.password());
            return issueTokensAndRespond(user, response);
        } catch (EmailAlreadyInUseException ex) {
            return ResponseEntity.status(409).body(Map.of("error", "email_in_use"));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password")
    public ResponseEntity<?> login(@Valid @RequestBody EmailAuthRequest request, HttpServletResponse response) {
        try {
            User user = userService.authenticateLocal(request.email(), request.password());
            return issueTokensAndRespond(user, response);
        } catch (InvalidCredentialsException ex) {
            return unauthorized();
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token and issue a new access token")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = CookieUtils.extract(request, RefreshTokenCookie.NAME);
        if (refreshToken == null) {
            return unauthorized();
        }

        try {
            RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);
            return issueTokensAndRespond(rotated.user(), response, rotated.rawToken());
        } catch (InvalidRefreshTokenException ex) {
            return unauthorized();
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the refresh token and clear both auth cookies")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = CookieUtils.extract(request, RefreshTokenCookie.NAME);
        if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }

        setCookie(response, accessTokenCookie.clear());
        setCookie(response, refreshTokenCookie.clear());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> issueTokensAndRespond(User user, HttpServletResponse response) {
        return issueTokensAndRespond(user, response, refreshTokenService.issue(user));
    }

    private ResponseEntity<?> issueTokensAndRespond(User user, HttpServletResponse response, String refreshRawToken) {
        String accessToken = jwtService.generateToken(user);
        setCookie(response, accessTokenCookie.create(accessToken, Duration.ofMillis(jwtService.getExpirationMs())));
        setCookie(response, refreshTokenCookie.create(
                refreshRawToken, Duration.ofMillis(refreshTokenService.getRefreshExpirationMs())
        ));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    private static ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
    }

    private static void setCookie(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
