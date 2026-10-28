package com.oauth.oauth2_be.config;

import com.oauth.oauth2_be.security.AccessTokenCookie;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OAuth2 backend")
                        .version("0.0.1")
                        .description("Google and GitHub login. Protected calls use the access_token cookie."))
                .components(new Components().addSecuritySchemes("accessToken", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(AccessTokenCookie.NAME)))
                .addSecurityItem(new SecurityRequirement().addList("accessToken"));
    }
}
