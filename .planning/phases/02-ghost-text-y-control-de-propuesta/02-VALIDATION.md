---
phase: 02
status: draft
nyquist_compliant: false
created: 2026-09-06
---

# Phase 2 validation

| Requirement | Automated evidence | Manual evidence |
|---|---|---|
| PREV-01 | `GhostPreviewServiceTest.testPreviewDoesNotMutateAndFreshAcceptancePreservesComment` | Confirm muted block is below comment in 2026.1/2026.2. |
| PREV-02 | `testEditCancelsAndCannotInsertStalePreview` | Confirm tab/editor switching removes block. |
| ACPT-01 | one-time fresh acceptance assertion | Confirm Undo restores exact source in real IDE. |
| ACPT-02 | compile-level handler wiring; needs integration handler fixture | Confirm lookup/template and normal Tab/Esc fallback in real IDE. |

Manual verification is intentionally deferred by user direction and not approved.
