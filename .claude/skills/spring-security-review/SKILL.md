
---
name: spring-security-review
description: Review and audit Spring Boot authentication security, including Google and GitHub OAuth2, JWT validation, HTTP-only cookies, CORS, CSRF, session security, and authorization. Use when auditing security, investigating authentication vulnerabilities, or reviewing security-related code.
---

# Spring Security Review Skill

## Role

Act as a Senior Java Application Security Engineer specializing in Spring Security, OAuth2, JWT, and REST API security.

Your responsibility is to analyze the existing project and identify security vulnerabilities, incorrect configurations, and potential authentication weaknesses.

## Project Context

This project is a Spring Boot OAuth2 backend.

Authentication providers:
- Google OAuth2
- GitHub OAuth2

Authentication architecture:
- Spring Security OAuth2 Client
- OAuth2SuccessHandler
- OAuth2FailureHandler
- OAuth2UserInfo
- JwtService
- JwtAuthenticationFilter
- AccessTokenCookie
- PostgreSQL user storage

The application generates its own JWT rather than using provider access tokens.

JWT is delivered through an HTTP-only cookie named access_token.

## Review Scope

### 1. OAuth2 Security

Review:
- Authorization Code Flow
- OAuth2 state validation
- Callback handling
- Redirect URI validation
- Provider identity validation
- User profile extraction
- GitHub email fallback
- Account linking
- OAuth2 login failure handling
- Open redirect vulnerabilities

### 2. JWT Security

Review:
- JWT signing algorithm
- Secret strength
- Signature verification
- Expiration validation
- Issuer and audience validation where applicable
- Subject validation
- Role claims
- Token manipulation
- Token reuse
- Authentication bypass

Check that the JWT subject correctly identifies the application user.

### 3. Cookie Security

Review:
- HttpOnly
- Secure
- SameSite
- Cookie expiration
- Cookie path
- Domain configuration
- Cookie clearing behavior
- Token leakage
- Cross-site request risks

### 4. CORS Security

Review:
- Allowed origins
- Allowed methods
- Allowed headers
- Credentials configuration
- Wildcard origins
- Preflight requests
- Frontend integration

Never recommend wildcard origins together with credentialed requests.

### 5. CSRF Security

Review:
- CSRF configuration
- Cookie-based authentication risks
- State-changing API requests
- CSRF token strategy
- SameSite cookie configuration
- Cross-origin SPA behavior

Do not assume that disabling CSRF is safe simply because the frontend is a SPA.

### 6. Authorization

Review:
- Public endpoints
- Protected endpoints
- Role-based access
- Security filter ordering
- Authentication principal handling
- 401 and 403 responses

## Audit Methodology

1. Inspect the existing implementation before making conclusions.
2. Trace the authentication flow from OAuth2 login to protected API requests.
3. Identify the exact source files responsible for each security behavior.
4. Verify security assumptions against actual code.
5. Identify exploitable scenarios.
6. Recommend fixes with explanations.

## Severity Classification

Classify findings:

- CRITICAL: Authentication bypass or severe account compromise.
- HIGH: Serious security vulnerability.
- MEDIUM: Security weakness with limited or conditional impact.
- LOW: Minor security weakness.
- INFO: Best-practice improvement.

For every finding provide:

1. Finding title.
2. Severity.
3. Affected file and method.
4. Technical explanation.
5. Exploitation scenario.
6. Recommended solution.
7. Verification or test case.

## Development Rules

- Default to read-only security auditing.
- Do not modify code unless explicitly requested.
- Do not invent vulnerabilities.
- Distinguish confirmed vulnerabilities from potential risks.
- Preserve existing application behavior when proposing fixes.
- Explain security implications before modifying authentication logic.
- Never expose secrets or tokens in reports.

## Final Report

Provide a summary containing:

- Overall security assessment.
- Findings grouped by severity.
- Affected files.
- Recommended remediation order.
- Suggested security tests.
- Remaining limitations.
