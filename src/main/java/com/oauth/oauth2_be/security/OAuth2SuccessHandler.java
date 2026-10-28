package com.oauth.oauth2_be.security;

import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtService jwtService;
    private final AccessTokenCookie accessTokenCookie;
    private final String frontendUrl;

    public OAuth2SuccessHandler(
            UserService userService,
            JwtService jwtService,
            AccessTokenCookie accessTokenCookie,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.accessTokenCookie = accessTokenCookie;
        this.frontendUrl = frontendUrl;
        setDefaultTargetUrl(frontendUrl);
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        OAuth2User oauth2User = oauthToken.getPrincipal();

        OAuth2UserInfo.Profile profile = profile(registrationId, oauth2User);
        if (profile == null || isBlank(profile.providerId()) || isBlank(profile.email())) {
            log.warn("OAuth2 login missing required profile data for provider={}", registrationId);
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/auth/callback?error=oauth_failed");
            return;
        }

        User user = userService.findOrCreate(
                profile.provider(),
                profile.providerId(),
                profile.email(),
                profile.name(),
                profile.avatarUrl()
        );
        String jwt = jwtService.generateToken(user);
        ResponseCookie cookie = accessTokenCookie.create(jwt, Duration.ofMillis(jwtService.getExpirationMs()));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/auth/callback");
    }

    private static OAuth2UserInfo.Profile profile(String registrationId, OAuth2User user) {
        if ("google".equalsIgnoreCase(registrationId)) {
            return OAuth2UserInfo.google(user);
        }
        if ("github".equalsIgnoreCase(registrationId)) {
            return OAuth2UserInfo.github(user);
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
