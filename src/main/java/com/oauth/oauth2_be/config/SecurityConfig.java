package com.oauth.oauth2_be.config;

import com.oauth.oauth2_be.security.JwtAuthenticationFilter;
import com.oauth.oauth2_be.security.OAuth2FailureHandler;
import com.oauth.oauth2_be.security.OAuth2SuccessHandler;
import com.oauth.oauth2_be.security.RestAccessDeniedHandler;
import com.oauth.oauth2_be.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disabled so the SPA can call the API with the JWT cookie.
                // Reconsider before production: a cross-site page could send that cookie.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // STATELESS: this API is authenticated purely by the access_token JWT on every
                // request. IF_REQUIRED would let Spring Security persist the authenticated
                // identity into an HttpSession (JSESSIONID), which would keep a user logged in
                // after their JWT expires or is revoked on logout — the session, not the JWT,
                // would end up being the real credential. OAuth2 login still works under
                // STATELESS: the authorization-request session (state/PKCE) used during the
                // provider handshake is created and read explicitly by Spring's
                // HttpSessionOAuth2AuthorizationRequestRepository, independent of this policy.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/ping",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/api/auth/refresh",
                                "/api/auth/register",
                                "/api/auth/login",
                                // Logout never relies on the authenticated principal — it reads
                                // whatever cookies are present and revokes/clears them, tolerating
                                // a missing or already-invalid access token (see AuthController).
                                "/api/auth/logout",
                                "/error",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new RestAuthenticationEntryPoint(),
                                PathPatternRequestMatcher.pathPattern("/api/**")
                        )
                        .defaultAccessDeniedHandlerFor(
                                new RestAccessDeniedHandler(),
                                PathPatternRequestMatcher.pathPattern("/api/**")
                        )
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter
    ) {
        // The filter is already in the security chain. Skip the servlet-container registration.
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
