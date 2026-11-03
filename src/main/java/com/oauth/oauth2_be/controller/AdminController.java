package com.oauth.oauth2_be.controller;

import com.oauth.oauth2_be.dto.RoleUpdateRequest;
import com.oauth.oauth2_be.dto.UserResponse;
import com.oauth.oauth2_be.entity.User;
import com.oauth.oauth2_be.service.UserNotFoundException;
import com.oauth.oauth2_be.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin")
public class AdminController {

    private final UserService userService;

    @GetMapping("/users")
    @Operation(summary = "List all users (ADMIN only)")
    public List<UserResponse> listUsers() {
        return userService.listAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Change a user's role (ADMIN only)")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        try {
            User user = userService.updateRole(id, request.role());
            return ResponseEntity.ok(UserResponse.from(user));
        } catch (UserNotFoundException ex) {
            return ResponseEntity.status(404).body(Map.of("error", "not_found"));
        }
    }
}
