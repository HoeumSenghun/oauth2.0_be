package com.oauth.oauth2_be.dto;

import com.oauth.oauth2_be.entity.Role;
import com.oauth.oauth2_be.entity.User;

public record UserResponse(
        Long id,
        String email,
        String name,
        String avatarUrl,
        Role role
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getAvatarUrl(),
                user.getRole()
        );
    }
}
