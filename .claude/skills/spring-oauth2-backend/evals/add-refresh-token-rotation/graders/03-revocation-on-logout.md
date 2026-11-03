---
type: llm
focus: trace
weight: 1
---

Logging out revokes the refresh token server-side (e.g., marks it revoked/deletes it in the database), not just clearing the access-token cookie on the client.
