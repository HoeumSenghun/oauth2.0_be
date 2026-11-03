# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot backend for an OAuth2 demo. Handles Google & GitHub login via Spring Security's OAuth2 client, creates/updates users in PostgreSQL, and issues the app's own JWT (not the provider's token) as an httpOnly cookie for the frontend SPA to use.

## Commands

- Build: `./mvnw.cmd clean compile` (or `./mvnw clean compile` in bash)
- Run tests: `./mvnw.cmd test`
- Run a single test class: `./mvnw.cmd test -Dtest=AuthFlowTest`
- Run a single test method: `./mvnw.cmd test -Dtest=AuthFlowTest#meWithValidCookieReturnsUser`
- Run the app: run `Application.java` from IntelliJ, or `./mvnw.cmd spring-boot:run`
- Package: `./mvnw.cmd clean package`

Tests use H2 in-memory (`src/test/resources/application.yml`), so no local Postgres is needed to run the test suite. The real app (`src/main/resources/application.yml`) requires a running PostgreSQL instance at `jdbc:postgresql://localhost:5432/oauth2_db` and these env vars: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `JWT_SECRET` (32+ chars, enforced in `JwtService`), `DB_USERNAME`, `DB_PASSWORD`.

App runs on `http://localhost:8080`. OAuth redirect URIs are pinned to `localhost` — see `application.yml`. `127.0.0.1` is a different host and must not be mixed in.

## Architecture

Package root: `com.oauth.oauth2_be`, organized by layer: `config`, `controller`, `dto`, `entity`, `repository`, `security`, `service`.

`service/` is a single flat `UserService` class (no interface/impl split) — don't introduce a `ServiceImpl` layer that isn't there. `JwtService` lives under `security/`, alongside the other auth-handling classes, not under `service/`.

### Auth flow (the core of this codebase)

1. Browser hits `/oauth2/authorization/google` or `/oauth2/authorization/github` — handled entirely by Spring Security's OAuth2 client (`SecurityConfig`).
2. On success, `OAuth2SuccessHandler` runs:
   - Normalizes the provider's user payload into a common shape via `OAuth2UserInfo` (package-private; has separate `google()`/`github()` extractors — GitHub has no guaranteed email, so it falls back to `<id>+<login>@users.noreply.github.com`).
   - Calls `UserService.findOrCreate` (keyed on `provider` + `providerId`, a DB unique constraint on `User`) to upsert the user.
   - Calls `JwtService.generateToken` to mint the app's own JWT (subject = user id, claim `role` = user's role), and sets it as the `access_token` httpOnly cookie via `AccessTokenCookie`.
   - Redirects to `${app.frontend-url}/auth/callback` (or `...?error=oauth_failed` if the provider didn't return enough profile data).
3. On failure, `OAuth2FailureHandler` redirects to the frontend with an error query param instead of rendering a Spring error page.
4. On every subsequent request, `JwtAuthenticationFilter` (a custom filter inserted before `UsernamePasswordAuthenticationFilter`) reads the `access_token` cookie, validates it via `JwtService`, loads the `User` via `UserService.getById`, and populates `SecurityContextHolder` directly with the `User` entity as principal (not a `UserDetails` wrapper) — so controllers cast `authentication.getPrincipal()` straight to `User` (see `AuthController.me`).
   - A valid JWT for a since-deleted user is silently left unauthenticated (`UserNotFoundException` caught in the filter) rather than erroring.
5. `/api/auth/logout` clears the cookie via `AccessTokenCookie.clear()`. This does not revoke the JWT server-side — there's no refresh-token/revocation mechanism yet (see Roadmap in the README).

### Security config notes

- CSRF is disabled app-wide (documented as acceptable for this demo cookie-based SPA setup, but flagged as something to reconsider before production — see comment in `SecurityConfig`).
- `/ping`, `/oauth2/**`, `/login/oauth2/**`, `/error`, and the Swagger/OpenAPI paths are public; everything else requires authentication.
- JSON (not HTML) 401/403 responses are enforced only under `/api/**` via `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`, scoped with `PathPatternRequestMatcher`.
- CORS (`CorsConfig`) allows only `${app.frontend-url}` with credentials enabled — needed because the JWT travels as a cookie, not an `Authorization` header.
- Cookie `Secure` flag is driven by `app.cookie.secure` (false for local HTTP dev; must be true in production over HTTPS).
- `Role` currently only has `USER` — there's no RBAC yet.

### Adding a new OAuth2 provider

Register it under `spring.security.oauth2.client.registration` in `application.yml`, then add a corresponding extractor in `OAuth2UserInfo` and a branch in `OAuth2SuccessHandler.profile(...)`.
