package com.oauth.oauth2_be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Health")
@SecurityRequirements
public class HelloController {
    @GetMapping("/ping")
    @Operation(summary = "Health check")
    public String ping() {
        return "backend is running";
    }
}
