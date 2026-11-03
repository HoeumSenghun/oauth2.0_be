# RBAC Admin Endpoint — Implementation Report (without_skill)

Project root used: `C:\Users\ASUS\AppData\Local\Temp\claude\C--java-oauth2-0-be\b83c29d9-43ad-465f-aabc-3bd0fb197d44\scratchpad\spring-oauth2-backend-repos\add-rbac-admin-endpoint-without_skill`

## Summary

Implemented role-based access control (RBAC) for a new admin-only endpoint, built on top of the project's existing JWT-cookie authentication.

Key observation during exploration: the authorization groundwork was already half-built. `JwtAuthenticationFilter` already builds the `Authentication` with a `SimpleGrantedAuthority` named `"ROLE_" + user.getRole().name()`, and `JwtService.generateToken` already embeds the role claim in the JWT. The only things missing were (1) a second role value to grant, (2) a protected URL pattern that requires it, and (3) the actual endpoint.

Changes made:

1. **`Role` enum** — added `ADMIN` alongside the existing `USER`. No migration needed since the column is `@Enumerated(EnumType.STRING)` and H2/Postgres just store the new string value; existing rows keep defaulting to `USER` (`@Builder.Default`).
2. **`SecurityConfig`** — added `.requestMatchers("/api/admin/**").hasRole("ADMIN")` right before the catch-all `.anyRequest().authenticated()`. Spring Security's `hasRole("ADMIN")` expects an authority literally named `ROLE_ADMIN`, which matches what `JwtAuthenticationFilter` already produces for a user whose `role` is `ADMIN`. This is enforced at the filter-chain level (URL-based), consistent with how the rest of the security config is written (no method-level `@PreAuthorize` is used anywhere else in the codebase, so no `@EnableMethodSecurity` was introduced just for this one endpoint).
3. **`UserService.listAll()`** — new read-only transactional method that delegates to `userRepository.findAll()` (already provided by `JpaRepository`, no repository change needed). Added for symmetry with the existing `getById` and to keep the controller thin.
4. **`AdminController`** (new) — `GET /api/admin/users`, returns `List<UserResponse>` built from `UserService.listAll()`, mapped through the existing `UserResponse.from(User)` factory (same DTO already used by `/api/auth/me`).
5. **`AdminControllerTest`** (new) — three MockMvc tests against the real security chain and H2:
   - no cookie → `401` with the existing `{"error":"unauthorized"}` body (via `RestAuthenticationEntryPoint`).
   - valid cookie for a `USER` → `403` (via `RestAccessDeniedHandler`).
   - valid cookie for an `ADMIN` → `200` with a JSON array containing both seeded users (one `USER`, one `ADMIN`).

`OAuth2SuccessHandler`/`UserService.findOrCreate` were not touched — new OAuth sign-ins still default to `USER`, which is correct given the task scope. Nothing asked for a self-promotion path; that's a separate decision (DB seed vs. invite flow), flagged rather than silently picked.

## Files created

- `src/main/java/com/oauth/oauth2_be/controller/AdminController.java`
- `src/test/java/com/oauth/oauth2_be/AdminControllerTest.java`

## Files modified

- `src/main/java/com/oauth/oauth2_be/entity/Role.java` — added `ADMIN` to the enum.
- `src/main/java/com/oauth/oauth2_be/config/SecurityConfig.java` — added the `/api/admin/**` → `hasRole("ADMIN")` matcher.
- `src/main/java/com/oauth/oauth2_be/service/UserService.java` — added `listAll()`.

(All paths relative to the project root above.)

## Maven commands run and real outcomes

Run from the project root (no PostgreSQL needed; tests use the H2 config in `src/test/resources/application.yml`):

1. `./mvnw.cmd -q -B compile`
   → **Succeeded**, no output (quiet mode), no compile errors.

2. `./mvnw.cmd -B test`
   → **BUILD SUCCESS**. Actual final summary from the console:
   ```
   [INFO] Running com.oauth.oauth2_be.AdminControllerTest
   [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
   [INFO] Running com.oauth.oauth2_be.ApplicationTests
   [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
   [INFO] Running com.oauth.oauth2_be.AuthFlowTest
   [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
   [INFO] Running com.oauth.oauth2_be.security.JwtServiceTest
   [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
   [INFO] Results:
   [INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```
   All 10 tests pass, including the 3 new admin RBAC tests and the 3 pre-existing `AuthFlowTest` tests (unaffected, confirming no regression on `/api/auth/me`, `/ping`, or the 401 JSON shape).

Ran for real inside the project's own directory; console output above is real, not simulated.

## Honesty notes / things not verified

- Did **not** start the full application (`spring-boot:run`) against a real PostgreSQL or real Google/GitHub OAuth — only the Maven test suite against H2 was run.
- There is currently no way to create an `ADMIN` user through the app itself (OAuth sign-in always defaults to `USER`). For a real admin to exist, someone will need to promote a row manually (e.g. `UPDATE users SET role = 'ADMIN' WHERE id = ...`) or a future seeding/promotion mechanism needs to be added.
- Used URL-pattern-based authorization (`SecurityConfig`) rather than `@PreAuthorize` on the controller method, matching the existing style in this codebase (no method-level security is used anywhere else in the project).

## Final message for the user

I added RBAC for the new admin endpoint:

- `Role` now has `USER` and `ADMIN` (`src/main/java/com/oauth/oauth2_be/entity/Role.java`).
- `GET /api/admin/users` (new `AdminController`) returns all users in the same `UserResponse` shape already used by `/api/auth/me`.
- `SecurityConfig` now requires `ROLE_ADMIN` for anything under `/api/admin/**`; this reuses the existing JWT filter, which already puts `ROLE_<role>` as the granted authority, so no token/filter changes were needed.
- Added `AdminControllerTest` covering: no cookie → 401, `USER` cookie → 403, `ADMIN` cookie → 200 with both users listed.
- Ran `mvnw test`: all 10 tests pass (the 3 new ones plus the 3 pre-existing `AuthFlowTest` tests, so nothing regressed).

One thing to decide separately: there's currently no way to actually become an `ADMIN` — new OAuth logins always get `USER`. A manual DB update for the first admin, or a small promotion mechanism, is needed before this is useful in a real environment.
