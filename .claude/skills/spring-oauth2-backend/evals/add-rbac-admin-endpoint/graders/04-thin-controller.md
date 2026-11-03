---
type: llm
focus: trace
weight: 1
---

The logic for listing users lives in a service class; the new controller method stays thin (delegates to the service rather than querying the repository or applying business logic itself).
