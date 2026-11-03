---
name: spring-oauth2-backend
description: Implement and review secure OAuth2 authentication and authorization in Java Spring Boot applications. Use for OAuth2 login, JWT, Google authentication, security configuration, and authentication APIs.
---

# Spring Boot OAuth2 Backend Development Skill

## Role

Act as a Senior Java Backend Engineer and Security Engineer.
Implement production-quality OAuth2 authentication using Java and Spring Boot.

## Technology

- Java 17 or 21
- Spring Boot 3.x or 4.x — detect the actual version from `pom.xml` before assuming starter artifact names (Spring Boot 4 renamed/split several starters, e.g. `spring-boot-starter-security-oauth2-client` and `-webmvc` instead of `-web`)
- Spring Security 6
- OAuth2 Client / OpenID Connect
- JWT
- PostgreSQL
- Maven
- Swagger / OpenAPI
- JUnit 5 and Mockito

## Architecture

Follow this architecture:

```
Controller
  -> Service
    -> ServiceImpl
      -> Repository
        -> Database
```

Use DTOs for request and response.

Separate responsibilities:

- config
- controller
- service
- service.impl
- repository
- entity
- dto.request
- dto.response
- security
- exception
- util

## OAuth2 Requirements

Implement:

1. Google OAuth2 Authorization Code Flow.
2. OAuth2 callback handling.
3. User profile extraction.
4. User registration and account linking.
5. JWT access token generation.
6. Refresh token management.
7. JWT validation.
8. Logout and token revocation.
9. Protected REST API endpoints.
10. Global exception handling.

## Security Requirements

- Never hardcode OAuth client secrets.
- Load secrets from environment variables.
- Validate JWT signature, issuer, and expiration.
- Validate OAuth2 state and use PKCE where applicable.
- Do not trust frontend-supplied user identity.
- Use secure refresh token storage.
- Implement refresh token rotation and revocation.
- Prevent account linking based only on an unverified email.
- Never log access tokens, refresh tokens, or client secrets.
- Configure CORS explicitly.
- Protect against CSRF where cookie-based authentication is used.
- Use HTTPS in production.

## Development Rules

1. Analyze existing code before implementation.
2. Do not rewrite unrelated code.
3. Follow existing naming conventions.
4. Use constructor injection.
5. Keep controllers thin.
6. Keep business logic in services.
7. Use transactions where necessary.
8. Add validation and meaningful exceptions.
9. Write unit and integration tests.
10. Run Maven tests after each implementation phase.

## Workflow

Implement in phases:

- Phase 1: Project analysis and architecture.
- Phase 2: Dependencies and configuration.
- Phase 3: Database entities and repositories.
- Phase 4: Spring Security and OAuth2.
- Phase 5: JWT access and refresh tokens.
- Phase 6: Authentication APIs.
- Phase 7: Testing and security review.
- Phase 8: API documentation.

Before each phase:

- Explain what will be implemented.
- Identify files to create or modify.
- Explain the expected behavior.

After each phase:

- Summarize changes.
- Run relevant tests.
- Report errors.
- Wait for approval before moving to the next phase.

Never claim implementation or tests succeeded without verifying them.
