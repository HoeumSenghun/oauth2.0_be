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

Tests use H2 in-memory (`src/test/resources/application.yml`) and the in-memory `TokenRevocationService` fallback (`app.revocation.store=in-memory`), so no local Postgres or Redis is needed to run the test suite. The real app (`src/main/resources/application.yml`) requires a running PostgreSQL instance at `jdbc:postgresql://localhost:5432/oauth2_db`, a running Redis instance (access-token revocation) at `localhost:6379` by default, and these env vars: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `JWT_SECRET` (32+ chars, enforced in `JwtService`), `DB_USERNAME`, `DB_PASSWORD`. Optional: `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`, `APP_JWT_ISSUER`/`APP_JWT_AUDIENCE`, `APP_REVOCATION_STORE` (`redis` default, or `in-memory` for a single-instance deployment without Redis).

App runs on `http://localhost:8080`. OAuth redirect URIs are pinned to `localhost` — see `application.yml`. `127.0.0.1` is a different host and must not be mixed in.

## Architecture

Package root: `com.oauth.oauth2_be`, organized by layer: `config`, `controller`, `dto`, `entity`, `repository`, `security`, `service`.

`service/` is a single flat `UserService` class (no interface/impl split) — don't introduce a `ServiceImpl` layer that isn't there. `JwtService` lives under `security/`, alongside the other auth-handling classes, not under `service/`.

### Auth flow (the core of this codebase)

1. Browser hits `/oauth2/authorization/google` or `/oauth2/authorization/github` — handled entirely by Spring Security's OAuth2 client (`SecurityConfig`).
2. On success, `OAuth2SuccessHandler` runs:
   - Normalizes the provider's user payload into a common shape via `OAuth2UserInfo` (package-private; has separate `google()`/`github()` extractors — GitHub has no guaranteed email, so it falls back to `<id>+<login>@users.noreply.github.com`).
   - Calls `UserService.findOrCreate` (keyed on `provider` + `providerId`, a DB unique constraint on `User`) to upsert the user.
   - Calls `JwtService.generateToken` to mint the app's own JWT (`sub` = user id, `jti` = random UUID, `iss`/`aud` = `app.jwt.issuer`/`app.jwt.audience`, claim `role` = user's role), and sets it as the `access_token` httpOnly cookie via `AccessTokenCookie`, plus a rotating refresh token (DB-backed, see below) as the `refresh_token` cookie via `RefreshTokenCookie`.
   - Redirects to `${app.frontend-url}/auth/callback?token=<jwt>` (or `...?error=oauth_failed` if the provider didn't return enough profile data). The JWT is deliberately also passed in this URL, not just the cookie — the frontend lives on a different origin than this backend, so the `access_token` cookie (`SameSite=Lax`) isn't delivered on the frontend's cross-site API calls; the frontend reads the token from the callback URL instead. This is a known, accepted exposure (browser history / `Referer` / frontend logs) — see the Known Limitations note in the README before changing it, since the frontend depends on this exact contract.
3. On failure, `OAuth2FailureHandler` redirects to the frontend with an error query param instead of rendering a Spring error page.
4. On every subsequent request, `JwtAuthenticationFilter` (a custom filter inserted before `UsernamePasswordAuthenticationFilter`) reads the `access_token` cookie, validates signature/expiry/issuer/audience via `JwtService`, checks the token's `jti` against `TokenRevocationService.isRevoked`, loads the `User` via `UserService.getById`, and populates `SecurityContextHolder` directly with the `User` entity as principal (not a `UserDetails` wrapper) — so controllers cast `authentication.getPrincipal()` straight to `User` (see `AuthController.me`).
   - A valid JWT for a since-deleted user is silently left unauthenticated (`UserNotFoundException` caught in the filter) rather than erroring.
5. `/api/auth/logout` (public, reads cookies directly rather than relying on the authenticated principal, so it tolerates a missing/expired/malformed `access_token` cookie) revokes the refresh token in Postgres (`RefreshTokenService`) **and** the access token's `jti` in `TokenRevocationService` (Redis by default, TTL'd to the token's remaining lifetime), then clears both cookies. A copied JWT stops authenticating immediately after logout rather than lingering until its natural expiry.
6. Local email/password auth (`/api/auth/register`, `/api/auth/login`) and refresh-token rotation (`/api/auth/refresh`) also exist, issuing the same access/refresh token pair via `AuthController.issueTokensAndRespond`.

### Security config notes

- CSRF is disabled app-wide. This is lower-risk than it sounds because both auth cookies are `SameSite=Lax`, which already blocks cross-site `POST`/`PATCH`/`DELETE` CSRF in compliant browsers — but it's not a substitute for real CSRF protection if the cookie policy ever moves to `SameSite=None` (would be needed if the URL-token workaround above is ever removed in favor of a fully cookie-based cross-origin flow).
- `/ping`, `/oauth2/**`, `/login/oauth2/**`, `/api/auth/refresh`, `/api/auth/register`, `/api/auth/login`, `/api/auth/logout`, `/error`, and the Swagger/OpenAPI paths are public; everything else requires authentication. `/api/admin/**` additionally requires `ROLE_ADMIN`.
- JSON (not HTML) 401/403 responses are enforced only under `/api/**` via `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`, scoped with `PathPatternRequestMatcher`.
- CORS (`CorsConfig`) allows only `${app.frontend-url}` with credentials enabled — needed because the JWT travels as a cookie, not an `Authorization` header.
- Cookie `Secure` flag is driven by `app.cookie.secure` (false for local HTTP dev; must be true in production over HTTPS).
- `Role` has `USER` and `ADMIN` (`AdminController`, `ROLE_ADMIN`-gated); no finer-grained RBAC beyond that.
- Access-token revocation (`TokenRevocationService`) is backed by Redis in production (`RedisTokenRevocationService`, keys `revoked:{jti}`, fails closed — i.e. treats the token as revoked — if Redis is unreachable) and by an in-memory map in tests/single-instance dev (`InMemoryTokenRevocationService`), selected via `app.revocation.store`.

### Adding a new OAuth2 provider

Register it under `spring.security.oauth2.client.registration` in `application.yml`, then add a corresponding extractor in `OAuth2UserInfo` and a branch in `OAuth2SuccessHandler.profile(...)`.
