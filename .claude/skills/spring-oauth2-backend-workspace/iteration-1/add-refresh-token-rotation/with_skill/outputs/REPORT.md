# Report: Add refresh token support (rotation on use, revocation on logout)

Project worked in (isolated test copy, confirmed as working directory for every command/file below):
C:\Users\ASUS\AppData\Local\Temp\claude\C--java-oauth2-0-be\b83c29d9-43ad-465f-aabc-3bd0fb197d44\scratchpad\spring-oauth2-backend-repos\add-refresh-token-rotation-with_skill

## 1. Analysis (what existed before)

This is a Spring Boot 4.1 / Spring Security 6 OAuth2 backend (Google + GitHub login via
OAuth2SuccessHandler) that only issued a single JWT access token on login
(JwtService.generateToken), carried in an httpOnly access_token cookie
(AccessTokenCookie, path /, SameSite=Lax, 24h expiry). There was no refresh token at
all: /api/auth/logout just cleared the access-token cookie, and once the JWT expired the
user had to go through the full OAuth2 login flow again. SecurityConfig required
authentication (anyRequest().authenticated()) for everything under /api/auth/** except the
explicitly listed public paths.

## 2. What was implemented and why

Added a database-backed, rotating, revocable refresh-token mechanism alongside the existing
JWT access token:

- Storage, not a JWT. Refresh tokens are opaque, cryptographically random (32 bytes,
  SecureRandom, URL-safe Base64), and only their SHA-256 hash is persisted
  (RefreshToken entity / RefreshTokenRepository). The raw value is handed to the client once
  (in the cookie) and never logged or stored, so a DB leak alone cannot be replayed.
- Rotation on use. RefreshTokenService.rotate(rawToken) looks the token up by hash,
  rejects it if missing/expired/already-revoked, marks it revoked, and issues a brand-new token
  in the same transaction, so each refresh token is single-use, as requested.
- Reuse/theft detection. If an already-revoked token is presented again (a strong signal of
  token theft or a replay), the service revokes every other active refresh token for that
  user, forcing full re-authentication everywhere, not just failing the one request.
- Revocation on logout. POST /api/auth/logout now also revokes the refresh token found in
  the refresh_token cookie (not just clearing cookies as before).
- New cookie, scoped tightly. RefreshTokenCookie mirrors AccessTokenCookie but is
  httpOnly, scoped to path=/api/auth (it is only ever needed by /refresh and /logout), and
  SameSite=Strict (stricter than the access-token cookie's Lax, since it is never needed on a
  cross-site navigation).
- New endpoint POST /api/auth/refresh: reads the refresh cookie, rotates it, issues a new
  JWT access token, and sets both new cookies (204 No Content on success).
- Issuing on login. OAuth2SuccessHandler now also issues a refresh token (and sets its
  cookie) right after generating the access-token JWT.
- Security config fix required for this to actually work. /api/auth/refresh and
  /api/auth/logout had to be added to the permitAll matcher list in SecurityConfig
  (POST only). Previously everything under /api/auth/** required a valid, non-expired
  access-token JWT via anyRequest().authenticated(), which would have made /refresh
  pointless (you would only ever be able to call it while your access token was still valid) and
  would have left logout unreachable once a session's access token had already expired. /me
  is untouched and still requires authentication.
- Error handling. Added InvalidRefreshTokenException (deliberately generic, does not leak
  why a token was rejected) and a small GlobalExceptionHandler (@RestControllerAdvice)
  that turns it into the same {"error": "..."} JSON shape already used by
  RestAuthenticationEntryPoint/RestAccessDeniedHandler, so API error responses stay
  consistent.
- Config. Added app.jwt.refresh-expiration (30 days) next to the existing
  app.jwt.expiration in both src/main/resources/application.yml and
  src/test/resources/application.yml.

### Design choices / placement rationale
- RefreshTokenService, RefreshTokenCookie, InvalidRefreshTokenException were placed in the
  existing security package, consistent with how JwtService/AccessTokenCookie already live
  there (refresh tokens are a credential/token concern, same category as the JWT).
- RefreshToken entity / RefreshTokenRepository went into the existing entity/repository
  packages, consistent with User/UserRepository.
- Kept RefreshTokenService as a plain @Service class (no interface/impl split), matching the
  existing UserService convention in this codebase rather than introducing a new pattern.
- No DB migration file was added/needed: like the rest of the schema, RefreshToken relies on
  Hibernate's ddl-auto: update (main) / create-drop (test), matching the existing approach
  (no Flyway/Liquibase in this project).

## 3. Files created

- src/main/java/com/oauth/oauth2_be/entity/RefreshToken.java
- src/main/java/com/oauth/oauth2_be/repository/RefreshTokenRepository.java
- src/main/java/com/oauth/oauth2_be/util/TokenHasher.java
- src/main/java/com/oauth/oauth2_be/security/RefreshTokenService.java
- src/main/java/com/oauth/oauth2_be/security/RefreshTokenCookie.java
- src/main/java/com/oauth/oauth2_be/security/InvalidRefreshTokenException.java
- src/main/java/com/oauth/oauth2_be/exception/GlobalExceptionHandler.java
- src/test/java/com/oauth/oauth2_be/RefreshTokenFlowTest.java

## 4. Files modified

- src/main/java/com/oauth/oauth2_be/controller/AuthController.java - added POST /api/auth/refresh;
  logout now also revokes the refresh token and clears its cookie.
- src/main/java/com/oauth/oauth2_be/security/OAuth2SuccessHandler.java - issues a refresh token
  and sets its cookie on successful OAuth2 login, alongside the existing access-token cookie.
- src/main/java/com/oauth/oauth2_be/config/SecurityConfig.java - permitAll added for
  POST /api/auth/refresh and POST /api/auth/logout (see rationale above); everything else
  unchanged.
- src/main/resources/application.yml - added app.jwt.refresh-expiration: 2592000000 (30 days).
- src/test/resources/application.yml - added matching app.jwt.refresh-expiration.

## 5. Bug caught and fixed during testing

First test run exposed a real transactional bug: in the reuse-detection branch of
RefreshTokenService.rotate(), the code revoked all active sessions for the user and then threw
InvalidRefreshTokenException. Because rotate() is @Transactional and the exception is an
unchecked RuntimeException, Spring's default rollback rule rolled back the entire
transaction on the way out, silently undoing the defensive revocation it had just performed.
Fixed by annotating rotate() with
@Transactional(noRollbackFor = InvalidRefreshTokenException.class), so the revocation commits
while the exception still propagates (and still yields a 401 to the caller). This was caught by
the reuseOfARevokedTokenAlsoRevokesOtherActiveSessionsForThatUser test, not by inspection.

## 6. Maven commands run, and actual results (not assumed)

All run with working directory =
...\scratchpad\spring-oauth2-backend-repos\add-refresh-token-rotation-with_skill, using the
project's mvnw.cmd, offline (-o), against the in-memory H2 test database configured in
src/test/resources/application.yml (no PostgreSQL required or used):

1. ./mvnw.cmd -o compile
   -> BUILD SUCCESS

2. ./mvnw.cmd -o test (first run, before the transactional fix)
   -> BUILD FAILURE - 1 of 13 tests failed:
   RefreshTokenFlowTest.reuseOfARevokedTokenAlsoRevokesOtherActiveSessionsForThatUser
   (expected 401, got 204) - this is the rollback bug described above.

3. After the noRollbackFor fix:
   ./mvnw.cmd -o test -Dtest=RefreshTokenFlowTest
   -> BUILD SUCCESS - 6/6 tests passed.

4. ./mvnw.cmd -o clean test (full suite, clean build)
   -> BUILD SUCCESS - Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
   (covers ApplicationTests, AuthFlowTest (3, pre-existing, still green),
   RefreshTokenFlowTest (6, new), JwtServiceTest (3, pre-existing, still green)).

I did not run the application against a real PostgreSQL instance or exercise the live Google/GitHub
OAuth2 redirect flow end-to-end (that needs real OAuth client credentials and a browser, which
is out of scope for this environment). The OAuth2 login path's refresh-token issuance was
verified by code review and by the fact that OAuth2SuccessHandler compiles and wires
correctly, but not via a live login. The refresh/rotate/reuse-detection/revoke-on-logout logic
itself is covered end-to-end by RefreshTokenFlowTest through the real Spring Security filter
chain and a real (H2) database.

## 7. New API surface

- POST /api/auth/refresh - no body. Reads refresh_token cookie. On success: 204 No Content,
  sets a new access_token cookie and a new, rotated refresh_token cookie. On missing/invalid/
  expired/reused token: 401 with {"error": "invalid_refresh_token"}. Public endpoint (no
  access-token auth required, that is the point).
- POST /api/auth/logout - no body. If a refresh_token cookie is present, revokes it in the
  database. Always clears both cookies and returns 204 No Content, even with no refresh cookie
  present. Public endpoint.
- GET /api/auth/me - unchanged, still requires authentication.

## 8. Message to give back to the user

Added a database-backed refresh token: it is issued alongside your JWT on login (Google or
GitHub), stored only as a SHA-256 hash (never the raw value), and lives in a separate
refresh_token cookie that is httpOnly, SameSite=Strict, and scoped to /api/auth only.

POST /api/auth/refresh rotates it: present the current refresh token, get back a new access
token and a new refresh token, while the old refresh token is immediately revoked, so each one
is single-use. If a revoked token ever gets replayed (a sign of theft), every other active
refresh token for that user is revoked too, forcing re-login everywhere. POST /api/auth/logout
now revokes the refresh token in the database (not just clearing the cookie like before).

I also had to open up /api/auth/refresh and /api/auth/logout in the security config, since
they were previously locked behind anyRequest().authenticated(), which would have required a
valid, unexpired access token to call /refresh, defeating its purpose entirely.

Caught and fixed one real bug along the way: the "revoke everything on detected reuse" logic was
getting silently rolled back by Spring's default transaction behavior when the method threw
afterward, fixed with noRollbackFor, and there is now a test
(reuseOfARevokedTokenAlsoRevokesOtherActiveSessionsForThatUser) specifically guarding against
that regressing.

Full test suite: 13/13 passing (./mvnw.cmd -o clean test), including 6 new tests covering
refresh, rotation, reuse detection, and logout revocation. I did not test against a live
PostgreSQL or a real Google/GitHub OAuth redirect, that needs real credentials and a browser,
outside this environment, but the new logic runs through the real security filter chain against
an H2 database in the tests, so confidence in it is high.
