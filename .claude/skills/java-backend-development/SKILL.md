
---
name: java-backend-development
description: Implement and maintain Java Spring Boot backend features using layered architecture, controllers, services, service implementations, repositories, entities, DTOs, validation, and PostgreSQL. Use when developing REST APIs, fixing backend bugs, or extending application business logic.
---

# Java Backend Development Skill

## Role

Act as a Senior Java Spring Boot Backend Developer.

Write clean, maintainable, scalable, and production-oriented Java code.

## Project Context

Existing project:
- Java
- Spring Boot
- Spring Security
- OAuth2 Client
- JWT
- PostgreSQL
- Maven
- H2 for testing

Package root:

com.oauth.oauth2_be

Follow the existing project architecture and naming conventions.

## Architecture

Use the following layered architecture where appropriate:

Controller
|
v
Service Interface
|
v
Service Implementation
|
v
Repository
|
v
Database

Package responsibilities:

- controller: REST API endpoints.
- service: Business service interfaces.
- service.impl: Business service implementations.
- repository: Database access.
- entity: Database entities.
- dto.request: API request objects.
- dto.response: API response objects.
- config: Application configurations.
- security: Authentication and authorization.
- exception: Custom exceptions and global exception handling.
- util: Reusable utilities.

Do not create packages or classes that duplicate existing functionality.

## Coding Standards

### Controller

- Keep controllers thin.
- Use appropriate HTTP methods.
- Use correct HTTP status codes.
- Validate request parameters.
- Delegate business logic to services.
- Return consistent response structures.
- Never expose internal exceptions to clients.

### Service

- Define clear business operations.
- Separate interfaces and implementations where appropriate.
- Keep business rules in service implementations.
- Use transactions where necessary.

### Repository

- Handle database operations.
- Use existing persistence technology.
- Avoid unnecessary custom queries.
- Prevent SQL injection.
- Consider database indexes and constraints.

### Entity

- Match actual database schema.
- Use appropriate column types.
- Define relationships carefully.
- Add unique constraints where necessary.
- Avoid exposing sensitive fields.

### DTO

- Separate request and response models.
- Never expose passwords, secrets, or internal tokens unnecessarily.
- Use validation annotations.
- Return only required information.

## Java Best Practices

- Use constructor injection.
- Prefer final fields for dependencies.
- Use meaningful naming conventions.
- Avoid duplicated code.
- Keep methods focused.
- Use appropriate exception handling.
- Avoid unnecessary dependencies.
- Avoid overengineering.
- Follow SOLID principles where they improve maintainability.

## Database Requirements

Use PostgreSQL for the actual application.

Use H2 for automated tests.

When changing database structures:

1. Explain the schema change.
2. Provide migration scripts when applicable.
3. Consider existing data.
4. Add appropriate indexes and constraints.
5. Avoid destructive changes without explicit approval.

## API Development Workflow

For every new API:

1. Analyze the requirements.
2. Inspect existing endpoints and architecture.
3. Design the endpoint and HTTP method.
4. Define request and response DTOs.
5. Implement service logic.
6. Implement repository operations.
7. Implement the controller.
8. Add validation.
9. Add exception handling.
10. Add unit and integration tests.
11. Update Swagger documentation.

## Bug Fixing Workflow

When fixing a bug:

1. Understand the reported problem.
2. Trace the execution flow.
3. Identify the root cause.
4. Identify affected classes.
5. Propose the smallest appropriate fix.
6. Implement the fix.
7. Run relevant tests.
8. Report the results.

Do not change unrelated functionality.

## Code Quality

Before finishing:

- Check compilation.
- Check imports.
- Check null handling.
- Check transaction boundaries.
- Check database consistency.
- Check API response behavior.
- Check backward compatibility.
- Run relevant tests.

## Final Development Report

Provide:

1. Feature or bug summary.
2. Root cause if applicable.
3. Files created.
4. Files modified.
5. API changes.
6. Database changes.
7. Test results.
8. Remaining issues.
