---
type: llm
focus: trace
weight: 1
---

The admin users endpoint returns a DTO for each user (matching the project's existing dto package convention) rather than serializing the raw User JPA entity directly.
