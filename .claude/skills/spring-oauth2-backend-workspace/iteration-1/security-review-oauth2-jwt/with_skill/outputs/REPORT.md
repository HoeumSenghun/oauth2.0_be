# Security Review: Google/GitHub OAuth2 + JWT Backend (with_skill)

No changes were made to the project copy — this was a read-only review. All files under `security-review-oauth2-jwt-with_skill` were only read, never edited.

Reviewed project root:
`C:\Users\ASUS\AppData\Local\Temp\claude\C--java-oauth2-0-be\b83c29d9-43ad-465f-aabc-3bd0fb197d44\scratchpad\spring-oauth2-backend-repos\security-review-oauth2-jwt-with_skill`

Files read: `SecurityConfig`, `CorsConfig`, `JwtService`, `JwtAuthenticationFilter`, `AccessTokenCookie`, `OAuth2SuccessHandler`, `OAuth2FailureHandler`, `OAuth2UserInfo`, `UserService`, `User`/`AuthProvider`/`Role` entities, `AuthController`, `HelloController`, `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`, `OpenApiConfig`, `application.yml` (main + test), `pom.xml`, and the existing tests.

## Summary

The core design is sound: provider identity is keyed on `(provider, provider_id)` rather than email (so it isn't vulnerable to the classic "unverified email lets you take over an account" bug), the JWT is kept out of JS via an `httpOnly` cookie, the JWT secret is required from the environment and length-checked at startup, and client secrets are never hardcoded. The main gaps are things that are fine for local dev but become real production risks: no safety net forcing secure cookie/HTTPS config in prod, no way to revoke a JWT once issued (including on logout), CSRF protection relies entirely on `SameSite=Lax` with no CSRF token as defense-in-depth, and a couple of weak defaults (DB password fallback, public Swagger).

## Findings

### 1. HIGH — `app.cookie.secure` defaults to `false`, and nothing stops it from shipping that way
**File:** `src/main/resources/application.yml` (`app.cookie.secure: false`), consumed by `AccessTokenCookie.java`.

There is only one `application.yml` — no `application-prod.yml` and no profile-based override. The cookie carrying the JWT (`AccessTokenCookie.base()`) sets `.secure(secure)` straight from that property. If this config ships to production unchanged (easy to do — it's the only file, and it looks like a normal default), the `access_token` cookie will be sent over plain HTTP. Unlike the JWT secret (`JwtService` throws `IllegalStateException` if it's missing/too short), there is no fail-fast check here — a forgotten env override just silently degrades security instead of breaking the build.

**Recommendation:** Make `app.cookie.secure` default to `true` and require an explicit opt-out for local dev (e.g. via a `dev`/`local` Spring profile), not the other way round. Same treatment for `app.frontend-url` and the OAuth2 `redirect-uri`s, which are currently hardcoded to `http://127.0.0.1:...` with no prod profile — before deploy these must be set to the real HTTPS frontend/callback URLs or the whole login flow (and CORS) breaks or is insecure.

### 2. MEDIUM-HIGH — No JWT revocation; "logout" doesn't actually invalidate the token
**Files:** `AuthController.logout()`, `AccessTokenCookie.clear()`, `JwtService`.

`POST /api/auth/logout` only clears the cookie client-side (`Set-Cookie: access_token=; Max-Age=0`). The JWT itself is stateless and signature-only — there is no server-side denylist/allowlist, no refresh-token table, nothing checked in `JwtAuthenticationFilter`/`JwtService.validate()` beyond signature and expiry. With `app.jwt.expiration: 86400000` (24 hours), any copy of a token obtained before logout (packet capture when `cookie.secure=false`, a shared/public machine, a leaked log line, etc.) remains fully valid for up to 24 hours after the user "logs out." This implementation has neither refresh-token rotation nor revocation — it's a single long-lived access token with no server-side kill switch.

**Recommendation:** Either shorten the access-token lifetime significantly and add a revocable refresh token (rotated, stored server-side, checked on refresh), or at minimum add a server-side revocation check (e.g. a `token_version`/`sessionVersion` column on `User`, bumped on logout and compared against a claim in the JWT) so logout actually invalidates the token rather than just the browser's copy of it.

### 3. MEDIUM — CSRF is disabled with no compensating control beyond `SameSite=Lax`
**Files:** `SecurityConfig.java` (`.csrf(AbstractHttpConfigurer::disable)`), `AccessTokenCookie.java` (`sameSite("Lax")`).

The code's own comment flags this ("Reconsider before production: a cross-site page could send that cookie"), and it's a legitimate concern. `SameSite=Lax` blocks the cookie on cross-site `POST`/`PUT`/`DELETE` fetches, but it is still attached on top-level cross-site **GET navigations**, and Lax's cross-site behavior has historically had browser-specific quirks/bugs. Right now the only state-changing endpoint is `POST /api/auth/logout` (low-impact CSRF target), but the security chain has `anyRequest().authenticated()` with no CSRF token at all, so every mutating endpoint added later (profile updates, any future POST/PUT/DELETE under `/api/**`) inherits this gap by default — there's nothing forcing a future developer to think about it.

**Recommendation:** Before deploy, either re-enable Spring Security's CSRF protection with a cookie-readable XSRF token for the SPA to echo back in a header (the standard double-submit pattern compatible with `httpOnly` auth cookies), or explicitly document and enforce that every mutating endpoint must be `SameSite=Lax`-safe and add an `Origin`/`Referer` check as defense-in-depth. Don't rely on `SameSite=Lax` as the sole CSRF control long-term.

### 4. MEDIUM — Database credentials fall back to weak hardcoded defaults
**File:** `src/main/resources/application.yml`: `username: ${DB_USERNAME:postgres}`, `password: ${DB_PASSWORD:root}`.

Unlike `app.jwt.secret` (which fails startup if unset or too short) or the OAuth2 client credentials (`${GOOGLE_CLIENT_ID}` etc., which have no default and fail if unset), the DB credentials silently fall back to `postgres`/`root` if the env vars aren't set. In a deploy where `DB_USERNAME`/`DB_PASSWORD` are forgotten, the app doesn't fail loudly — it just connects with a well-known, guessable credential pair.

**Recommendation:** Drop the defaults (`${DB_USERNAME}`, `${DB_PASSWORD}` with no fallback) so a missing credential fails fast, consistent with how the JWT secret and OAuth2 secrets are already handled.

### 5. LOW-MEDIUM — `ddl-auto: update` and `show-sql: true` in the only shipped config
**File:** `src/main/resources/application.yml`.

`spring.jpa.hibernate.ddl-auto: update` lets Hibernate auto-alter the production schema on boot based on entity mappings — fine for local iteration, risky for a real deploy (unreviewed/unlogged schema changes, no migration history). `show-sql: true` logs every SQL statement; combined with parameter logging this can leak PII (emails, names) into application logs.

**Recommendation:** Use a proper migration tool (Flyway/Liquibase) with `ddl-auto: none` (or `validate`) in any non-local profile, and turn `show-sql` off outside local dev.

### 6. LOW — Swagger UI / OpenAPI docs are publicly reachable with no auth
**Files:** `SecurityConfig.java` (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` all `permitAll()`), `OpenApiConfig.java`.

These are intentionally public (reasonable for an internal/dev tool), but there's no profile gate, so they'll also be public in production, exposing the full API surface (including that `access_token` is a cookie-based API key scheme) to anyone.

**Recommendation:** Gate Swagger/OpenAPI behind a profile check (disable in `prod`) or put it behind auth/IP allowlisting if it must stay available.

### 7. INFORMATIONAL — Role claim embedded in the JWT is unused for authorization
**Files:** `JwtService.generateToken()` (puts `role` claim in the token), `JwtAuthenticationFilter.authenticate()` (ignores the claim and re-reads `user.getRole()` from the DB on every request).

This is actually the *safer* pattern (a role change takes effect immediately rather than waiting for token expiry, avoiding stale-privilege issues), but it means the `role` claim in the JWT payload is dead weight — unused and only adds a (currently low-value) piece of information visible to anyone who inspects the token. Not a vulnerability as implemented; flagging only so it's a deliberate choice rather than leftover scaffolding once more roles/permissions are added.

### 8. INFORMATIONAL — No `iss`/`aud` claims on the JWT
**File:** `JwtService.generateToken()` / `parse()`.

The token only carries `sub`, `role`, `iat`, `exp`. For a single-service backend this is low risk today, but if this JWT is ever accepted by another service, or if the signing key is ever shared across environments, the lack of issuer/audience validation means a token minted for one context could be replayed in another. Cheap to add now (`.issuer(...)` / `.audience()` on generation, with matching `.requireIssuer()` / `.requireAudience()` on the parser) before it becomes a multi-service problem.

### 9. Confirmed OK (no finding — called out so they aren't mistaken for gaps)
- **Account linking is not email-based.** `UserService.findOrCreate` keys on `(provider, providerId)` — backed by a DB unique constraint (`uk_users_provider_provider_id`). Google's `sub` / GitHub's `id` are provider-issued, stable identifiers, so this avoids the common "unverified email for account takeover" vulnerability even though `email_verified` is never explicitly checked.
- **CORS is explicit, not wildcarded.** `CorsConfig` binds `allowedOrigins` to the single configured `app.frontend-url` and only enables credentials for that origin — correct pattern for cookie-based auth with CORS.
- **Secrets aren't hardcoded.** `JWT_SECRET`, `GOOGLE_CLIENT_SECRET`, and `GITHUB_CLIENT_SECRET` are all required env vars with no defaults in `application.yml`; the test `application.yml` only contains clearly-fake test values. Nothing scanned contains a real secret.
- **No algorithm-confusion / `alg:none` risk.** `JwtService` uses `Keys.hmacShaKeyFor` + `Jwts.parser().verifyWith(key)`, which requires a validly signed HMAC token; it won't accept unsigned or differently-algorithmed tokens.
- **OAuth2 state handling is on Spring Security's default path.** `oauth2Login()` uses the stock authorization-request flow (session-backed `AuthorizationRequestRepository`), which performs the `state` check against CSRF on the OAuth2 redirect itself; the success handler also calls `session.invalidate()` after minting the cookie, avoiding session-fixation leftovers.
- **Cookie is `httpOnly`**, so a basic XSS payload can't read the JWT directly via `document.cookie` (though it could still *ride* the cookie for same-origin requests — standard httpOnly tradeoff, not a flaw).

## Priority order before deploy

1. Fix the cookie/HTTPS/frontend-URL defaults (#1) — this is the one that silently breaks "secure" in production if nobody remembers to override it.
2. Decide on a token-revocation story for logout (#2) — right now logout is cosmetic.
3. Make a deliberate CSRF decision rather than leaving it disabled with only `SameSite` backing it (#3).
4. Remove the DB credential fallback defaults (#4).
5. #5–#8 are good cleanup before/shortly after deploy but are not blocking in the same way as 1–3.
