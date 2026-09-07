# Phase 4 validation

| Area | Automated evidence | Manual evidence pending |
|---|---|---|
| CODEX-01 | App Server thread/turn integration compiles; protocol tests pass. | Invoke generation against an authenticated local Codex account. |
| SAFE-01 | Read-only sandbox, web/plugins config disabled, credential env scrubbed, file-change rejection. | Confirm a project-aware request reads references but cannot alter a file. |
| QUAL-01 | Existing preview freshness/Tab/Esc tests still pass in full suite. | Confirm stale response and native completion coexist in the target IntelliJ versions. |
