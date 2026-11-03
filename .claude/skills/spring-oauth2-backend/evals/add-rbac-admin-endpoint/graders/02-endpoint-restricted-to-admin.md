---
type: llm
focus: trace
weight: 1
---

GET /api/admin/users is restricted so only an ADMIN-authenticated caller can access it, and a regular USER-authenticated request to it is expected to receive a 403, not a 200.
