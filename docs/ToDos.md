---
doc_type: todos
version: "1.1"
last_updated: 2026-08-27
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
See [Task Tracking Standard](https://gitlab.com/smart-assets.io/gitlab-profile/-/blob/master/docs/common/task-tracking-standard.md)

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
status: complete
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
    status: complete
    claimed_by: pi-session
    claimed_at: 2026-07-07T19:13:22Z
    completed_at: 2026-07-08T00:00:00Z
    acceptance:
      - "Mount, create/read/write/rename/delete, wallet unlock, and token transfer work on a libfuse Linux host"
      - "Existing e2e suite passes on Linux against a live shard"

  - id: TASK-001-2
    title: "Full F1r3Drive file sharing on macOS via macFUSE"
    status: complete
    claimed_by: pi-session
    claimed_at: 2026-07-09T17:57:57Z
    completed_at: 2026-07-09T00:00:00Z
    acceptance:
      - "Mount, create/read/write/rename/delete, wallet unlock, and token transfer work on macOS with macFUSE"
      - "Existing e2e suite passes on macOS against a live shard"

  - id: TASK-001-3
    title: "Full F1r3Drive file sharing on Windows via WSL (Windows Subsystem for Linux)"
    status: complete
    claimed_by: pi-session
    claimed_at: 2026-07-09T22:57:56Z
    completed_at: 2026-07-10T06:44:04Z
    blocked_by: [TASK-001-1]
    acceptance:
      - "F1r3Drive mounts and operates inside WSL2 using the Linux/libfuse build"
      - "Mounted drive contents are reachable from the Windows side (e.g. \\\\wsl$ / Explorer)"
      - "Install/run procedure for WSL documented in INSTALLATION.md"

  - id: TASK-001-4
    title: "Fully synchronized on-chain storage via f1r3node-rust"
    status: complete
    claimed_by: pi-session
    claimed_at: 2026-07-11T23:45:20Z
    completed_at: 2026-07-11T23:49:40Z
    acceptance:
      - "F1r3Drive deploys to and reads from a f1r3node-rust shard (../f1r3node-rust) with full synchronization"
      - "Data written on one OS/mount is readable from another mount after sync"

  - id: TASK-001-5
    title: "Multi-OS integration test suite in OCI containers"
    status: complete
    claimed_by: pi-session
    claimed_at: 2026-07-17T16:34:15Z
    completed_at: 2026-07-17T16:39:37Z
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

### EPIC-002: f1r3node-rust Cost-Accounting Compatibility (fileio-phase-1-2)

```yaml
---
epic_id: EPIC-002
title: "Make the F1r3Drive deploy path compatible with f1r3node-rust cost accounting (phlo removal)"
status: pending
priority: p0
user_story: null
blocked_by: []
created_at: 2026-08-27
claimed_by: null
claimed_at: null
tasks:
  - id: TASK-002-1
    title: "Re-vendor protobuf definitions from f1r3node-rust fileio-phase-1-2"
    status: pending
    acceptance:
      - "src/main/protobuf_models mirrors the fileio-phase-1-2 protos: DeployDataProto reserves tags 7/8/15 (phloPrice, phloLimit, primary_phlo_share); DeployInfo reserves 7/8"
      - "New fields are present: DeployDataProto.authorityPresentations (18); DeployInfo authorityFundingCertificate (15), authorityCostWitness (16), preStateHash (17), postStateHash (18), admissionStatus (19)"
      - "Project builds with regenerated stubs (./gradlew shadowJar -x test)"
  - id: TASK-002-2
    title: "Remove phlo fields from deploy construction"
    status: pending
    blocked_by: [TASK-002-1]
    acceptance:
      - "F1r3flyBlockchainClient no longer sets phloLimit/phloPrice (currently F1r3flyBlockchainClient.java:265,283)"
      - "Deploy cost is read as the reporting scalar (COMM count + byte cost), not treated as the REV debit"
  - id: TASK-002-3
    title: "Surface REV-funding failures as typed errors"
    status: pending
    blocked_by: [TASK-002-2]
    acceptance:
      - "out_of_phlogistons deploy failures map to a typed error in errors/ (e.g. InsufficientRevForDeploy) instead of a generic F1r3flyDeployError"
      - "The error message tells the user to fund the wallet's REV balance and retry"
      - "docs/configuration.md documents that the unlocked wallet also funds deploys"
  - id: TASK-002-4
    title: "Verify e2e suite against a fileio-phase-1-2 shard"
    status: pending
    blocked_by: [TASK-002-2]
    acceptance:
      - "Testcontainers e2e suite runs against a fileio-phase-1-2 f1r3node-rust image"
      - "Write, deploy finalization, cross-mount read, and token transfer tests pass"
      - "A deliberately underfunded wallet test observes the typed out_of_phlogistons error"
---
```

**Context:** The `fileio-phase-1-2` branch of f1r3node-rust removes the phlo gas market: `phloPrice`/`phloLimit` are removed from the wire (their proto tags are reserved), and deploy capacity is derived from the signer's REV custody in the SystemVault. F1r3Drive's deploy path still sets `phloLimit` against vendored protos that predate this change, so deploys against a fileio-era shard are incompatible as written. Discovered during the 2026-08-27 architecture/glossary review; see `docs/multi-os-demo-architecture.md` ("f1r3node-rust: cost accounting and File I/O") and `docs/Glossary.md` ("Shard Cost Accounting").

**Scope:**
- Included: proto re-vendoring, deploy-builder changes, typed funding errors, e2e verification against the new shard.
- Excluded: multi-sig/cosigner deploys (`authorityPresentations` beyond the empty default); cost-witness cryptographic validation; File I/O adoption (EPIC-003).

**Notes:**
- f1r3node-rust lives at `../f1r3node-rust` (branch `fileio-phase-1-2`).
- `POST /api/estimate-cost` can preview deploy cost, but only when called with the real deployer key.
- Until this epic lands, demos must pin a pre-fileio shard build.

---

### EPIC-003: Evaluate Shard File I/O as a Storage Backend

```yaml
---
epic_id: EPIC-003
title: "Evaluate the f1r3node-rust File I/O capability as an alternative F1r3Drive storage backend"
status: pending
priority: p2
user_story: null
blocked_by: [EPIC-002]
created_at: 2026-08-27
claimed_by: null
claimed_at: null
tasks:
  - id: TASK-003-1
    title: "Design doc: channel-based storage vs Fs-capability storage"
    status: pending
    acceptance:
      - "docs/designs/ doc compares the current RholangExpressionConstructor channel model with deploys that use the genesis Fs capability (openFile/openDir by logical name)"
      - "Covers consensus vs oracular mode fit, WAL/snapshot implications, per-syscall cost weights vs current chunk-deploy costs, and MVP limits (shared Fs instance, registry-URI access, static provisioning)"
      - "Recommends adopt / defer with measurable criteria"
  - id: TASK-003-2
    title: "Prototype: one file round-trip through the Fs capability"
    status: pending
    blocked_by: [TASK-003-1]
    acceptance:
      - "A deploy writes and reads a file via Fs.openFile against a shard provisioned with consensus-static-files"
      - "FSERR_* error mapping to F1r3Drive typed errors is sketched"
      - "Observed costs recorded and compared with the design doc's estimates"
---
```

**Context:** File I/O is reached entirely through Rholang deploy source (no new gRPC surface), so F1r3Drive's existing channel-based storage keeps working; this epic evaluates whether the shard-hosted POSIX capability is a better long-term backend. Terminology in `docs/Glossary.md` ("Shard File I/O").

**Notes:**
- Requires a shard with `storage { consensus-static-* }` buckets plus `consensus-fs-snapshot-dir` / `consensus-fs-snapshot-retain >= 2`.
- The `Allocator` agent is compiled but not published upstream, so buffer-based File APIs are dead until upstream PB-B-5.

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
2. **Claim task**: Set `claimed_by` using [Implementer Identification](https://gitlab.com/smart-assets.io/gitlab-profile/-/blob/master/docs/common/stigmergic-collaboration.md#implementer-identification) format and `status: in_progress`
3. **Implement**: Use `/implement` to execute with full context
4. **Complete**: Mark `status: complete` when acceptance criteria met
5. **Move epic**: When all tasks complete, move epic to `docs/CompletedTasks.md`

---

## References

- **User Stories:** `docs/UserStories.md`
- **Completed Work:** `docs/CompletedTasks.md`
- **Backlog:** `docs/Backlog.md`
- **MR/PR Tracking Standard:** [docs/common/todos-mr_pr-tracking-standard.md](https://gitlab.com/smart-assets.io/gitlab-profile/-/blob/master/docs/common/todos-mr_pr-tracking-standard.md)
