# Report: Add refresh token support (rotation + revocation on logout) — without_skill

Project copy worked in:
`C:\Users\ASUS\AppData\Local\Temp\claude\C--java-oauth2-0-be\b83c29d9-43ad-465f-aabc-3bd0fb197d44\scratchpad\spring-oauth2-backend-repos\add-refresh-token-rotation-without_skill`

## Summary of what was implemented and why

The backend previously issued only a single JWT `access_token` httpOnly cookie on OAuth2 login (Google/GitHub), with a fixed 24h expiration and no way to get a new one short of logging in again, and no way to invalidate a session server-side (logout only cleared the cookie client-side; a copy of the JWT was valid until it naturally expired).

Added a classic **opaque, rotating refresh token** on top of the existing access-token JWT:

- A new `refresh_token` httpOnly cookie, scoped to `/api/auth` only (it's only ever read by `/api/auth/refresh` and `/api/auth/logout`, so there's no reason to send it on every request the way `access_token` is).
- The refresh token itself is a random 256-bit value handed to the browser, but the server never stores that raw value — only its SHA-256 hash, in a new `refresh_tokens` table. A DB leak alone can't be replayed as a session.
- **Rotation on use**: `POST /api/auth/refresh` looks up the token by hash, and if it's valid (not expired, not already revoked), it revokes that row and issues a brand-new access JWT + a brand-new refresh token in the same transaction, so each refresh token is good for exactly one use.
- **Reuse detection**: if a refresh token that's already been rotated out (or expired) is presented again, that's treated as a signal the token may have been stolen and replayed — the entire token "family" for that user is revoked, not just the one row. This is the standard mitigation for refresh-token theft under rotation.
- **Revocation on logout**: `POST /api/auth/logout` now looks up the `refresh_token` cookie (if present) and marks it revoked in the DB, in addition to clearing both the `access_token` and `refresh_token` cookies. Previously logout only cleared the access cookie client-side; a copy of the JWT (e.g. stolen before logout) stayed valid until it expired. Now the refresh chain is actually cut server-side.
- `SecurityConfig` had to be updated to `permitAll()` on `/api/auth/refresh` and `/api/auth/logout`: both must work using only the `refresh_token` cookie, specifically in the case where the `access_token` is already missing/expired (that's the whole point of calling `/refresh`), so they cannot require an authenticated `Authentication` the way `/api/auth/me` does.

### A real bug caught by the tests

While wiring up reuse-detection, `rotate()` was initially annotated with plain `@Transactional`. The method, on detecting reuse, calls `revokeAllForUser(...)` and then throws `InvalidRefreshTokenException`. Spring's default behavior is to roll back a `@Transactional` method on any unchecked exception — which silently undid the entire protective revocation just performed, because the exception that's supposed to *report* the problem was also erasing the *fix* for it. The `reusingARotatedOutTokenIsRejectedAndRevokesTheFamily` test caught this: the "already-rotated" sibling token kept working after the reuse was detected. Fixed by marking the method `@Transactional(noRollbackFor = InvalidRefreshTokenException.class)` so the revocation commits even though the method still throws to signal the caller. This is exactly the kind of bug that would have shipped silently without an integration test exercising the reuse path end-to-end.

## Files created (relative to project root)

- `src/main/java/com/oauth/oauth2_be/entity/RefreshToken.java` — new JPA entity (hashed token, owning user, `expiresAt`, `revoked`, `createdAt`).
- `src/main/java/com/oauth/oauth2_be/repository/RefreshTokenRepository.java`
- `src/main/java/com/oauth/oauth2_be/service/RefreshTokenService.java` — `issue`, `rotate`, `revoke`, `revokeAllForUser`; SHA-256 hashing, `SecureRandom` token generation.
- `src/main/java/com/oauth/oauth2_be/service/InvalidRefreshTokenException.java`
- `src/main/java/com/oauth/oauth2_be/security/RefreshTokenCookie.java` — cookie builder mirroring the existing `AccessTokenCookie`, scoped to `/api/auth`.
- `src/test/java/com/oauth/oauth2_be/RefreshTokenFlowTest.java` — new integration tests (6 cases: missing cookie, unknown token, successful rotation, reuse-of-rotated-token detection + family revocation, logout revokes the token, logout clears both cookies).

## Files modified (relative to project root)

- `src/main/java/com/oauth/oauth2_be/controller/AuthController.java` — added `POST /api/auth/refresh` (rotates, returns new access+refresh cookies, or 401 `{"error":"invalid_refresh_token"}` and clears cookies on failure) and updated `POST /api/auth/logout` to revoke the presented refresh token and clear both cookies.
- `src/main/java/com/oauth/oauth2_be/security/OAuth2SuccessHandler.java` — issues a refresh token (and sets its cookie) alongside the access token on successful Google/GitHub login.
- `src/main/java/com/oauth/oauth2_be/config/SecurityConfig.java` — added `/api/auth/refresh` and `/api/auth/logout` to the `permitAll()` matcher list (see rationale above).
- `src/main/resources/application.yml` — added `app.jwt.refresh-expiration: 2592000000` (30 days).
- `src/test/resources/application.yml` — added `app.jwt.refresh-expiration: 604800000` (7 days, test only).
- `src/test/java/com/oauth/oauth2_be/AuthFlowTest.java` — `cleanUsers()` now also deletes `refresh_tokens` before `users` (the new FK from `refresh_tokens.user_id` to `users` would otherwise break `userRepository.deleteAll()` if the other test class's rows were still present in the shared H2 context).

## Maven commands run and outcomes (exact)

All run from the project root listed above, using the project's own Maven wrapper (no local Maven install needed), against the H2 in-memory DB configured in `src/test/resources/application.yml` — no PostgreSQL was started or needed.

1. `./mvnw.cmd -q -DskipTests compile` → succeeded, no output (clean compile of main sources).
2. `./mvnw.cmd -q test` (first run, before the transaction fix) → **1 failure**: `RefreshTokenFlowTest.reusingARotatedOutTokenIsRejectedAndRevokesTheFamily` expected 401, got 204 — this is the rollback bug described above, caught for real by the test, not hypothesized.
3. Applied the `noRollbackFor` fix to `RefreshTokenService.rotate()`.
4. `./mvnw.cmd test` (grepped for summary lines) →
   ```
   Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- ApplicationTests
   Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- AuthFlowTest
   Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- RefreshTokenFlowTest
   Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- JwtServiceTest
   Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
   BUILD SUCCESS
   ```
5. `./mvnw.cmd clean test` (full clean rebuild, to rule out stale artifacts) → same result, `BUILD SUCCESS`, 13/13 tests passing.

All commands were run with the project copy directory as the working directory, as instructed.

## What was not done / not verified

- Did not start the full application against a real PostgreSQL instance or exercise the live Google/GitHub OAuth2 redirect flow end-to-end (only the H2-backed test suite needed to pass per the task). The `OAuth2SuccessHandler` change (issuing a refresh token on login) is verified by compilation and code review, and indirectly by the fact the full Spring context loads under `@SpringBootTest`, but there is no integration test in this codebase (before or after this change) that drives an actual `oauth2Login` handshake, so that specific code path has no automated test coverage.
- Did not add scheduled cleanup of expired/revoked `refresh_tokens` rows. The table will grow over time in a real deployment; out of scope for "rotate on use / revoke on logout" but worth flagging as a follow-up.
- Did not add a "logout of all devices" endpoint, though `RefreshTokenService.revokeAllForUser` already exists (used internally for reuse detection) and could be exposed later with minimal effort.
- No `pom.xml` dependency changes were needed — everything uses what was already on the classpath (Spring Data JPA, JDK `SecureRandom`/`MessageDigest`).

## Final message for the user

Added real refresh-token support on top of the existing JWT access cookie:

- Login (Google/GitHub) now also sets a `refresh_token` httpOnly cookie (opaque random value; only its SHA-256 hash is stored in a new `refresh_tokens` table), scoped to `/api/auth` so it isn't sent on every request.
- `POST /api/auth/refresh` validates the refresh token, **rotates** it (revokes the one used, issues a new access JWT + new refresh token), and returns 401 and clears both cookies if it's missing, unknown, expired, or already used.
- Replaying an already-rotated-out refresh token is treated as a possible theft signal: every other active refresh token for that user is revoked as well, not just the one presented.
- `POST /api/auth/logout` now revokes the refresh token server-side (not just clearing cookies client-side), so a copy of the token can't be used to keep renewing access after logout.
- `SecurityConfig` now permits `/api/auth/refresh` and `/api/auth/logout` without requiring an existing authenticated session, since by definition the access token may already be gone when those are called.

While writing the reuse-detection integration test, a bug was caught in the first implementation pass: the revocation-on-reuse was getting silently rolled back by Spring's default transaction behavior because the method also threw an exception to report the problem. Fixed with `@Transactional(noRollbackFor = InvalidRefreshTokenException.class)`.

Verified with `./mvnw.cmd clean test`: **13/13 tests pass, BUILD SUCCESS** (6 of those are new tests for this feature). Did not run the app against a live PostgreSQL/OAuth2 provider — only the H2-backed test suite, per the task's instructions — so the OAuth2-login code path that now also issues a refresh token is verified by compilation and code review, not by an end-to-end login test.
