package com.oauth.oauth2_be.dto;

import com.oauth.oauth2_be.entity.Role;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
        @NotNull Role role
) {
}
