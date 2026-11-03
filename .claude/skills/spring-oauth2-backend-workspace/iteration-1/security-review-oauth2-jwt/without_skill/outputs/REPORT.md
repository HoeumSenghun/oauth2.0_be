# SECURITY REVIEW — Google/GitHub OAuth2 + JWT Backend (without_skill)

No changes were made to the project copy. This was a read-only review; nothing in `security-review-oauth2-jwt-without_skill` was created, edited, or deleted.

Project reviewed: `spring-oauth2-backend-repos/security-review-oauth2-jwt-without_skill` (Spring Boot 4.1.0 / Java 17, `spring-boot-starter-security-oauth2-client`, `io.jsonwebtoken` 0.12.5).

Files read in full: SecurityConfig.java, CorsConfig.java, JwtService.java, JwtAuthenticationFilter.java, AccessTokenCookie.java, OAuth2SuccessHandler.java, OAuth2FailureHandler.java, OAuth2UserInfo.java, AuthController.java, HelloController.java, UserService.java, UserRepository.java, User.java, Role.java, AuthProvider.java, RestAccessDeniedHandler.java, RestAuthenticationEntryPoint.java, UserResponse.java, OpenApiConfig.java, Application.java, application.yml (main + test), pom.xml.

## SUMMARY
The core design is reasonable for an OAuth2-only backend (no password auth to get wrong): httpOnly, SameSite=Lax JWT cookie; account lookup keyed on (provider, provider_id) rather than email, so there's no email-collision account takeover between Google and GitHub; OAuth2 state/session handled by Spring Security defaults; login session explicitly invalidated after the JWT is issued. The real problems are in deployment configuration (the single application.yml hardcodes dev-insecure values with no production profile) and a handful of missing hardening controls (no token revocation, no CSRF defense-in-depth, public Swagger). Nothing found is actively exploitable against a correctly-configured prod deploy, but several items will bite the first time this file is pushed to production unmodified.

## FINDINGS

**1) `app.cookie.secure` hardcoded `false` in the only application.yml — HIGH**

`src/main/resources/application.yml`: `app.cookie.secure: false`. `AccessTokenCookie` reads this via `@Value("${app.cookie.secure:false}")` and sets `.secure(secure)` on the JWT cookie. There is no `application-prod.yml` or any profile override anywhere in the repo (confirmed — only the main resource and the test resource exist). If deployed as-is, the JWT cookie — the entire auth credential — is sent over plain HTTP, trivially sniffable on any non-TLS-terminated network path.

Recommendation: add a prod/env-driven profile setting `app.cookie.secure: true`; better, flip the default to `true` and require explicit opt-out for local dev (mirroring how `JwtService` fails fast on a short secret).

**2) OAuth2 redirect URIs and frontend URL hardcoded to `http://127.0.0.1` with no prod override — HIGH**

```
redirect-uri: "http://127.0.0.1:8080/login/oauth2/code/google"
redirect-uri: "http://127.0.0.1:8080/login/oauth2/code/github"
app.frontend-url: http://127.0.0.1:3000
```

Same root cause as #1 — one config file, no profile split. Deploying unmodified either breaks OAuth2 login outright or downgrades the flow to plaintext HTTP.

Recommendation: externalize `redirect-uri` / `frontend-url` via env vars with no insecure default (like `JWT_SECRET` already does), and add a real prod profile.

**3) DB credentials fall back to weak defaults — MEDIUM**

```
username: ${DB_USERNAME:postgres}
password: ${DB_PASSWORD:root}
```

If `DB_PASSWORD` isn't set in the deployment environment, the app silently starts against `postgres/root` instead of failing fast. Contrast with `JwtService`, which correctly throws `IllegalStateException` if its secret is missing/short.

Recommendation: drop the defaults for DB_USERNAME/DB_PASSWORD outside local profiles so a missing secret is a startup failure.

**4) No server-side token revocation; "logout" only clears the cookie — MEDIUM**

`AuthController.logout()` just re-sets the `access_token` cookie with `Duration.ZERO`. `JwtService.validate()` only checks signature/expiry — no denylist/jti, and `User` has no tokenVersion field. Tokens live 24h (`app.jwt.expiration=86400000`) regardless of logout. A token exfiltrated by any non-XSS means (shared browser profile, proxy/log capture, lost unlocked device) stays valid for up to 24h after "logout," and there's no admin mechanism to force-expire a specific user's sessions.

Recommendation: add a `tokenVersion` column on User, embed as a JWT claim, check it in the filter/JwtService, and bump it on logout-everywhere/admin action. Alternatively shorten access-token TTL and add a rotating refresh token if true logout matters.

**5) CSRF disabled, relying solely on SameSite=Lax — MEDIUM**

`SecurityConfig` disables CSRF with a comment acknowledging the risk ("a cross-site page could send that cookie"). `SameSite=Lax` does block the classic cross-site form-POST CSRF attack in modern browsers, so this isn't as dangerous as CSRF-off-with-no-SameSite, but it's a single control with no defense in depth — a future GET-based state change, non-standard browser/extension SameSite handling, or a later switch to `SameSite=None` (likely forced if frontend/backend end up on different parent domains in prod) would silently remove the protection.

Recommendation: either re-enable Spring Security CSRF (e.g. CookieCsrfTokenRepository pattern) or explicitly accept SameSite-Lax-only as a documented tradeoff with a regression test pinning the cookie's SameSite attribute.

**6) Swagger UI / OpenAPI docs publicly exposed, no auth — LOW/MEDIUM**

`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs`, `/v3/api-docs/**` are all `permitAll()` with no profile gating, handing anonymous callers the full API surface and the cookie-auth scheme description for free recon.

Recommendation: gate springdoc behind a non-prod profile or put it behind auth/IP allowlisting in production.

**7) Provider email trusted without verification check — LOW**

`OAuth2UserInfo.google()/github()` take the provider's `email` attribute at face value. Google is low-risk here; GitHub's `/user` endpoint can return null for private emails (handled with a sensible synthetic fallback), but when GitHub does return an email, its `verified` flag isn't checked. Because the account-lookup key is `(provider, provider_id)`, not email, this is NOT an account-takeover vector today.

Recommendation: if email is later used for anything higher-stakes, check GitHub's verified flag via `/user/emails`.

**8) No rate limiting on OAuth2 callback / JWT verification path — LOW / informational**

No throttling anywhere in the stack; normal for pre-launch but worth a line item before internet-facing launch.

Recommendation: rate-limit at the edge (reverse proxy/gateway) before internet exposure.

## CHECKED AND FOUND FINE (not re-litigated, for completeness)
- Account-linking/IDOR via email collision: lookup key is `(provider, provider_id)` with a DB unique constraint (User.java) — a Google and GitHub account sharing an email become two distinct rows, not a hijacked account.
- Privilege escalation via JWT claims: `role` claim is sourced from the DB (`user.getRole()`), never from client input or OAuth claims; `Role` currently only has USER anyway.
- Open redirect: success/failure handlers redirect to a fixed server-side `frontendUrl` config value, never a request parameter.
- Session fixation: `OAuth2SuccessHandler` explicitly invalidates the HttpSession used during the OAuth2 handshake right after issuing the JWT cookie, before redirecting.
- CORS: `CorsConfig` uses a single explicit allowed origin from `app.frontend-url` (not a wildcard) combined with `allowCredentials(true)` — correct, spec-compliant combination.
- JWT secret handling: `JwtService` fails fast if `app.jwt.secret` is missing or under 32 chars; `${JWT_SECRET}` has no insecure default (unlike the DB credentials — see Finding 3).
- Deleted-user handling: `JwtAuthenticationFilter` catches `UserNotFoundException` and leaves the request unauthenticated (clean 401 via `RestAuthenticationEntryPoint`), no leak.
- Error responses: `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` return minimal generic JSON, no stack traces.

## PRIORITY BEFORE DEPLOY
1. Fix #1 and #2 together — really one root cause (no prod profile); the cookie-security gap is the most dangerous item here. (High)
2. Fix #3 — remove the weak DB credential fallback. (Medium)
3. Deliberately decide and implement #4 (token revocation) and #5 (CSRF posture) rather than leaving current defaults. (Medium)
4. Gate Swagger (#6) behind a non-prod profile. (Low/Medium)
5. #7 and #8 can stay backlog, not blockers.

Relevant file paths (all under the project root `...\security-review-oauth2-jwt-without_skill`):
- src/main/java/com/oauth/oauth2_be/config/SecurityConfig.java
- src/main/java/com/oauth/oauth2_be/config/CorsConfig.java
- src/main/java/com/oauth/oauth2_be/config/OpenApiConfig.java
- src/main/java/com/oauth/oauth2_be/security/JwtService.java
- src/main/java/com/oauth/oauth2_be/security/JwtAuthenticationFilter.java
- src/main/java/com/oauth/oauth2_be/security/AccessTokenCookie.java
- src/main/java/com/oauth/oauth2_be/security/OAuth2SuccessHandler.java
- src/main/java/com/oauth/oauth2_be/security/OAuth2FailureHandler.java
- src/main/java/com/oauth/oauth2_be/security/OAuth2UserInfo.java
- src/main/java/com/oauth/oauth2_be/controller/AuthController.java
- src/main/java/com/oauth/oauth2_be/service/UserService.java
- src/main/java/com/oauth/oauth2_be/entity/User.java
- src/main/resources/application.yml
- pom.xml
