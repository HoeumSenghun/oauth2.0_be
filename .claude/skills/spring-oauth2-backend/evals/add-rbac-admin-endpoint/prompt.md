---
tags: [spring-oauth2-backend]
max_turns: 30
timeout_seconds: 900
allowed_tools: [Read, Glob, Grep, Skill, Write, Edit, Bash]
---

I want an ADMIN role so admins can hit a new GET /api/admin/users endpoint that lists all users; regular USER accounts should get a 403. Implement RBAC for this.
