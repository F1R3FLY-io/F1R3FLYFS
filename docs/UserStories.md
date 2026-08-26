---
doc_type: user_stories
version: "1.0"
last_updated: "2026-07-06"
---

# User Stories

This document captures user stories that drive feature development. User stories are reverse-engineered from completed epics and updated as new features are planned.

**Document Structure**
- Active stories: This file (`docs/UserStories.md`)
- Implementation tracking: `docs/ToDos.md` (epics and tasks)
- Completed work: `docs/CompletedTasks.md`

**Format:** Each story follows the standard template:
> As a [persona], I want [capability] so that [benefit].

**User Stories Standard Reference** (canonical):
[user-stories-standard.md](https://gitlab.com/smart-assets.io/gitlab-profile/-/blob/master/docs/common/user-stories-standard.md)

---

## Completed Stories

<!-- Add completed user stories here -->

---

## Planned Stories

Stories below are candidates for future epics. Move to "Completed Stories" when implemented.

<!-- Add planned user stories here -->

#### US-001: Multi-OS file sharing (Windows/WSL, macOS/FUSE, Linux)

> As a **F1r3Drive user on multiple operating systems (Windows, macOS, Linux)**, I want **to share files through F1r3Drive with full capability on Windows (via WSL), macOS (via FUSE), and compatible Linux systems** so that **multi-OS file sharing is delivered as the core value proposition of F1r3Drive**.

**Implemented in:** EPIC-001
**User Flow:** FLOW-001

**Status:** Implemented

**Acceptance Criteria:**
- [x] Full F1r3Drive file sharing works on Windows via WSL (Windows Subsystem for Linux)
- [x] Full F1r3Drive file sharing works on macOS via macFUSE
- [x] Full F1r3Drive file sharing works on compatible Linux systems via libfuse
- [x] Fully synchronized support using f1r3node-rust (../f1r3node-rust) with on-chain storage
- [x] Multi-OS integration test suite runs in OCI containers covering all supported platforms

---

## Story Template

Use this template when adding new user stories:

```markdown
#### US-XXX: [Short Title]

> As a **[persona]**, I want **[capability]** so that **[benefit]**.

**Implemented in:** [EPIC-ID or "Planned"]

**Acceptance Criteria:**
- [ ] Criterion 1
- [ ] Criterion 2
- [ ] Criterion 3

**Completed:** [Date or "Planned"]
```

---

## Relationship to Epics

User stories capture the **why** (user need and benefit). Epics capture the **what** (technical implementation tasks).

| Artifact | Purpose | Location |
|----------|---------|----------|
| User Story | Business/user need | `docs/UserStories.md` |
| Epic | Implementation scope | `docs/ToDos.md` |
| Task | Technical work item | Nested in epic YAML |
| Acceptance Criteria | Definition of done | In user story |

**Workflow:**
1. Identify user need -> Create user story
2. Design solution -> Create epic with tasks
3. Implement -> Work through tasks via `/nextTask` and `/implement`
4. Complete -> Mark epic complete, update story status

---

## References

- **Task Tracking:** `docs/ToDos.md`
- **Completed Work:** `docs/CompletedTasks.md`
- **User Stories Standard** (canonical): [user-stories-standard.md](https://gitlab.com/smart-assets.io/gitlab-profile/-/blob/master/docs/common/user-stories-standard.md)
