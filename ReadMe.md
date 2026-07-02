# OAuth2.0 — Backend

Spring Boot backend for the OAuth2 demo project. Handles Google & GitHub login, creates users in PostgreSQL, and issues a JWT for the frontend to use.

---

## Tech Stack

- Java 17
- Spring Boot 3
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
```

### 3. Register Redirect URIs with each provider

```
Google:  http://localhost:8080/login/oauth2/code/google
GitHub:  http://localhost:8080/login/oauth2/code/github
```

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

---

## Environment Variables Reference

| Variable | Description |
|----------|--------------|
| `GOOGLE_CLIENT_ID` | OAuth Client ID from Google Cloud Console |
| `GOOGLE_CLIENT_SECRET` | OAuth Client Secret from Google Cloud Console |
| `GITHUB_CLIENT_ID` | OAuth Client ID from GitHub OAuth App |
| `GITHUB_CLIENT_SECRET` | OAuth Client Secret from GitHub OAuth App |
| `JWT_SECRET` | Secret key used to sign JWT tokens (32+ characters) |

---

## Notes / Known Limitations

- CSRF is disabled for simplicity — fine for a demo, should be reconsidered for production.
- `Secure` flag on the JWT cookie is `false` for local HTTP testing — must be `true` in production (HTTPS).
- GitHub may not return an email if the user has a private email set — need to handle that case.

---

## Roadmap

- [ ] Add refresh token support
- [ ] Add logout endpoint (clear cookie)
- [ ] Add role-based access control