package com.oauth.oauth2_be.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailAuthRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password
) {
}
