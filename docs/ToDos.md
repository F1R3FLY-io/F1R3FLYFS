---
doc_type: todos
version: "1.0"
last_updated: 2026-07-06
mr_status:
  ready: false
  target_branch: main
---

# Tasks and Epics

This document tracks implementation work through **epics** (logical groupings of related tasks).

**Document Structure**
- Active work: This file (`docs/ToDos.md`)
- User stories: `docs/UserStories.md`
- Completed work: `docs/CompletedTasks.md`
- Backlog: `docs/Backlog.md`

**For LLM assistance in multi-repo workspace:**
See [Task Tracking Standard]([RELATIVE_PATH]/top-level-gitlab-profile/docs/common/task-tracking-standard.md)

**For reference (GitLab):**
[Task Tracking Standard](https://gitlab.com/smart-assets.io/gitlab-profile/-/blob/master/docs/common/task-tracking-standard.md)

---

## MR/PR Tracking

When all tasks in this file are complete and ready for merge, update the frontmatter:

```yaml
mr_status:
  ready: true
  target_branch: main
  title: "feat: [PROJECT_SPECIFIC: MR title]"
  description: |
    ## Summary
    - [Completed items]

    ## Test plan
    - [x] All tests passing
  labels: ["feature", "enhancement"]
```

---

## Active Epics

<!-- Epics are ordered by priority. Work on the highest priority epic first. -->

---

### EPIC-001: Multi-OS File Sharing (Windows/WSL, macOS/FUSE, Linux)

```yaml
---
epic_id: EPIC-001
title: "Multi-OS file sharing on Windows (WSL), macOS (macFUSE), and Linux (libfuse)"
status: pending
priority: p0
user_story: US-001
blocked_by: []
created_at: 2026-07-06
claimed_by: null
claimed_at: null
user_flow: FLOW-001
tasks:
  - id: TASK-001-1
    title: "Full F1r3Drive file sharing on compatible Linux systems via libfuse"
    status: pending
    acceptance:
      - "Mount, create/read/write/rename/delete, wallet unlock, and token transfer work on a libfuse Linux host"
      - "Existing e2e suite passes on Linux against a live shard"

  - id: TASK-001-2
    title: "Full F1r3Drive file sharing on macOS via macFUSE"
    status: pending
    acceptance:
      - "Mount, create/read/write/rename/delete, wallet unlock, and token transfer work on macOS with macFUSE"
      - "Existing e2e suite passes on macOS against a live shard"

  - id: TASK-001-3
    title: "Full F1r3Drive file sharing on Windows via WSL (Windows Subsystem for Linux)"
    status: pending
    blocked_by: [TASK-001-1]
    acceptance:
      - "F1r3Drive mounts and operates inside WSL2 using the Linux/libfuse build"
      - "Mounted drive contents are reachable from the Windows side (e.g. \\\\wsl$ / Explorer)"
      - "Install/run procedure for WSL documented in INSTALLATION.md"

  - id: TASK-001-4
    title: "Fully synchronized on-chain storage via f1r3node-rust"
    status: pending
    acceptance:
      - "F1r3Drive deploys to and reads from a f1r3node-rust shard (../f1r3node-rust) with full synchronization"
      - "Data written on one OS/mount is readable from another mount after sync"

  - id: TASK-001-5
    title: "Multi-OS integration test suite in OCI containers"
    status: pending
    blocked_by: [TASK-001-1, TASK-001-4]
    acceptance:
      - "OCI-based integration test suite exercises file sharing across all supported platforms"
      - "Suite runs in CI and gates merges to main"
---
```

**Context:** Multi-OS file sharing is the core value proposition of F1r3Drive (US-001). Today the FUSE mount targets macOS/Linux hosts individually; this epic delivers verified, synchronized file sharing across Windows (via WSL), macOS (via macFUSE), and compatible Linux systems, backed by on-chain storage through f1r3node-rust.

**Scope:**
- Included: platform support/verification on Linux (libfuse), macOS (macFUSE), and Windows via WSL2; f1r3node-rust shard integration for synchronized on-chain storage; OCI-container multi-OS integration test suite in CI.
- Excluded: native Windows (WinFsp) support without WSL; mobile platforms; performance tuning beyond functional parity.

**Notes:**
- f1r3node-rust lives at `../f1r3node-rust` in the workspace.
- Existing e2e tests (`src/e2e/java`, Testcontainers) are the baseline for per-platform verification; the OCI suite extends them across OSes.
- A user flow will be associated via `/user-flow` (see US-001).

---

<!-- Add more epics following the same format -->

---

## Epic Template

Use this template when adding new epics:

```yaml
---
epic_id: EPIC-XXX
title: "Short descriptive title"
status: pending
priority: p2
user_story: US-XXX
blocked_by: []
created_at: YYYY-MM-DD
claimed_by: null         # Implementer ID: human-{email}, {tool}-session[-{id}], or {team}/{role}
claimed_at: null
tasks:
  - id: TASK-XXX-1
    title: "Task description"
    status: pending
    acceptance:
      - "Measurable acceptance criterion"
---
```

---

## Task States

| Status | Meaning | Next Action |
|--------|---------|-------------|
| `pending` | Not started | Available to claim |
| `in_progress` | Being worked on | Continue or handoff |
| `blocked` | Waiting on dependency | Check `blocked_by` |
| `review` | Ready for review | Review and approve |
| `complete` | Done | Move to CompletedTasks.md |

---

## Workflow

1. **Find next task**: Use `/nextTask` to identify the highest priority unclaimed task
2. **Claim task**: Set `claimed_by` using [Implementer Identification](../common/stigmergic-collaboration.md#implementer-identification) format and `status: in_progress`
3. **Implement**: Use `/implement` to execute with full context
4. **Complete**: Mark `status: complete` when acceptance criteria met
5. **Move epic**: When all tasks complete, move epic to `docs/CompletedTasks.md`

---

## References

- **User Stories:** `docs/UserStories.md`
- **Completed Work:** `docs/CompletedTasks.md`
- **Backlog:** `docs/Backlog.md`
- **MR/PR Tracking Standard:** [docs/common/todos-mr_pr-tracking-standard.md]([RELATIVE_PATH]/top-level-gitlab-profile/docs/common/todos-mr_pr-tracking-standard.md)
