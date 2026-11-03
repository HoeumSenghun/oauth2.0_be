---
type: llm
focus: trace
weight: 1
---

The new refresh-token logic lives in a service class called from a thin controller, matching the project's existing Controller -> Service -> Repository layering, rather than embedding persistence or token logic directly in the controller.
