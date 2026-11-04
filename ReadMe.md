# OAuth2.0 — Backend

Spring Boot backend for the OAuth2 demo project. Handles Google & GitHub login, creates users in PostgreSQL, and issues a JWT for the frontend to use.

---

## Tech Stack

- Java 17
- Spring Boot 4.1
- Spring Security (OAuth2 Client)
- PostgreSQL
- JWT (jjwt)
- Maven

---

## Project Structure

```
com.oauth.oauth2_be
├── config          # SecurityConfig, CORS config
├── controller       # REST controllers
├── dto             # Request/Response DTOs
├── entity          # User entity
├── repository      # UserRepository (JPA)
├── security        # OAuth2 handlers, JWT filter, JWT service
└── service         # Business logic
```

---

## How Login Works

1. Frontend redirects browser to `/oauth2/authorization/google` or `/oauth2/authorization/github`
2. Spring Security handles the OAuth2 flow with the provider
3. On success, backend creates/finds the user in PostgreSQL and issues a JWT
4. JWT is set as an **httpOnly cookie**
5. Backend redirects back to the frontend
6. Frontend calls `/api/auth/me` (with credentials included) to get the logged-in user

We issue our own JWT instead of using Google's/GitHub's token directly, so we control expiry, roles, and what data is inside the token.

---

## Getting Started

### Prerequisites

- Java 17+
- PostgreSQL running locally
- Redis running locally (used to revoke access tokens on logout — e.g. `docker run -p 6379:6379 redis:7-alpine`). Not needed to run the test suite, which uses an in-memory fallback.
- IntelliJ IDEA
- Google Cloud Console project (OAuth credentials)
- GitHub OAuth App (OAuth credentials)

### 1. Database Setup

```sql
CREATE DATABASE oauth2_db;
```

### 2. Environment Variables

Set these in IntelliJ:
**Run/Debug Configurations → Modify options → Environment variables**

```
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
JWT_SECRET=any-random-32-character-string
DB_USERNAME=postgres
DB_PASSWORD=root
```

### 3. Register Redirect URIs with each provider

```
Google:  http://localhost:8080/login/oauth2/code/google
GitHub:  http://localhost:8080/login/oauth2/code/github
```

Open the app at `http://localhost:8080`. Google treats `localhost` and `127.0.0.1` as different redirect URIs.

### 4. Run the app

Run from IntelliJ (green ▶ button). App starts on:
```
http://localhost:8080
```

### 5. Verify it's working

```
GET http://localhost:8080/ping
→ "backend is running"
```

---

## API Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|--------------|
| GET | `/ping` | No | Health check |
| GET | `/oauth2/authorization/google` | No | Starts Google login |
| GET | `/oauth2/authorization/github` | No | Starts GitHub login |
| GET | `/api/auth/me` | Yes (JWT cookie) | Returns current logged-in user |
| POST | `/api/auth/logout` | No (reads cookies directly) | Revokes the access & refresh tokens and clears both cookies |

---

## Environment Variables Reference

| Variable | Description |
|----------|--------------|
| `GOOGLE_CLIENT_ID` | OAuth Client ID from Google Cloud Console |
| `GOOGLE_CLIENT_SECRET` | OAuth Client Secret from Google Cloud Console |
| `GITHUB_CLIENT_ID` | OAuth Client ID from GitHub OAuth App |
| `GITHUB_CLIENT_SECRET` | OAuth Client Secret from GitHub OAuth App |
| `JWT_SECRET` | Secret key used to sign JWT tokens (32+ characters) |
| `DB_USERNAME` | PostgreSQL username (defaults to `postgres`) |
| `DB_PASSWORD` | PostgreSQL password (defaults to `root`) |
| `REDIS_HOST` | Redis host for access-token revocation (defaults to `localhost`) |
| `REDIS_PORT` | Redis port (defaults to `6379`) |
| `REDIS_PASSWORD` | Redis password, if required (defaults to none) |
| `APP_JWT_ISSUER` | JWT `iss` claim, validated on every request (defaults to `oauth2-be`) |
| `APP_JWT_AUDIENCE` | JWT `aud` claim, validated on every request (defaults to `oauth2-be-clients`) |
| `APP_REVOCATION_STORE` | `redis` (default) or `in-memory` (single-instance fallback if Redis is unavailable) |

---

## Notes / Known Limitations

- CSRF is disabled for simplicity. The `access_token`/`refresh_token` cookies are `SameSite=Lax`, which already blocks the classic cross-site form/fetch CSRF attack on `POST`/`PATCH`/`DELETE` routes — but this is not a substitute for a real CSRF token if the cookie policy ever changes.
- `Secure` flag on the JWT cookie is `false` for local HTTP testing — must be `true` in production (HTTPS).
- GitHub may not return an email when the address is private. Login still succeeds with a `<id>+<login>@users.noreply.github.com` fallback.
- The OAuth2 redirect to the frontend (`/auth/callback?token=...`) still carries the JWT in the URL, by design — the frontend reads it from there because the access-token cookie is scoped to the backend's own domain and isn't visible cross-site. This is a known, accepted exposure (browser history, `Referer` headers, frontend-side logs) unless/until the frontend is changed to rely on `SameSite=None; Secure` cookies instead.
- Access tokens are now revocable (Redis-backed, by `jti`): logout revokes both the refresh token and the current access token, so a copied JWT stops working immediately rather than lingering until its natural expiry.

---

## Roadmap

- [ ] Add refresh token support
- [x] Add logout endpoint (clear cookie)
- [ ] Add role-based access control