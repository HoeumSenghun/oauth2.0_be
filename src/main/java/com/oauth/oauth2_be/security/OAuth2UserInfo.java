package com.oauth.oauth2_be.security;

import com.oauth.oauth2_be.entity.AuthProvider;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

final class OAuth2UserInfo {

    private OAuth2UserInfo() {
    }

    record Profile(
            AuthProvider provider,
            String providerId,
            String email,
            String name,
            String avatarUrl
    ) {
    }

    static Profile google(OAuth2User user) {
        String providerId = attribute(user, "sub");
        String email = attribute(user, "email");
        String name = attribute(user, "name");
        String avatarUrl = attribute(user, "picture");
        if (user instanceof OidcUser oidcUser) {
            if (isBlank(providerId)) {
                providerId = oidcUser.getSubject();
            }
            if (isBlank(email)) {
                email = oidcUser.getEmail();
            }
            if (isBlank(name)) {
                name = oidcUser.getFullName();
            }
            if (isBlank(avatarUrl)) {
                avatarUrl = oidcUser.getPicture();
            }
        }
        return new Profile(AuthProvider.GOOGLE, providerId, email, name, avatarUrl);
    }

    static Profile github(OAuth2User user) {
        Object id = user.getAttribute("id");
        String providerId = id == null ? null : String.valueOf(id);
        String login = attribute(user, "login");
        String email = attribute(user, "email");
        if (isBlank(email)) {
            email = githubFallbackEmail(providerId, login);
        }
        String name = attribute(user, "name");
        if (isBlank(name)) {
            name = login;
        }
        return new Profile(AuthProvider.GITHUB, providerId, email, name, attribute(user, "avatar_url"));
    }

    private static String githubFallbackEmail(String providerId, String login) {
        if (isBlank(login)) {
            return providerId + "@users.noreply.github.com";
        }
        return providerId + "+" + login + "@users.noreply.github.com";
    }

    private static String attribute(OAuth2User user, String name) {
        Object value = user.getAttribute(name);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
