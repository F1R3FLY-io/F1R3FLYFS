# User Flows

Detailed user interaction patterns for this project. Each flow captures the
journey, ordered steps, key interactions (which become integration test
assertions), and success metrics (which become test budgets). Flows are
linked bidirectionally to user stories (`docs/UserStories.md`) and to
epics (`docs/ToDos.md`).

**Document Structure**
- User flows: this file (`docs/User-Flows.md`)
- User stories: `docs/UserStories.md`
- Implementation tracking: `docs/ToDos.md` (epics + tasks)

**Linkage**
- A flow lists `Related Stories` and an `Implemented in` epic
- Stories carry a `User Flow:` back-reference
- Epic YAML carries a `user_flow:` field
- Integration tests appear on the flow as `Integration Tests:` once authored
  (late-stage: written after the implementation epic completes and unit
  tests pass). Run `test-spec FLOW-XXX` to seed the artifact and `link
  FLOW-XXX <ITEST-NNN|path>` for subsequent additions.

---

## Personas

Add reusable persona definitions here (Name + one-line description) and
reference them by name from each flow's `Personas:` field.

---

## Core Workflows

<!-- Created flows are inserted above the "Planned Flows" section below. -->

---

### FLOW-001: Multi-OS File Sharing via Mounted F1r3Drive

**Status:** Implemented
**Implemented in:** EPIC-001
**Related Stories:** US-001
**Related Flows:** None
**Personas:** F1r3Drive user on multiple operating systems (Windows, macOS, Linux)
**Integration Tests:** `./gradlew multiOsIntegrationTest`, `.github/workflows/multi-os-integration.yml`

**Journey:** Install FUSE prerequisites -> Mount drive -> Unlock wallet -> Create/edit files -> Background on-chain sync -> Access same files from another OS

**Steps:**
1. **Install and Mount** - User installs the platform prerequisite (macFUSE on macOS, libfuse on Linux, WSL2 on Windows) and mounts F1r3Drive against a running f1r3node-rust shard
2. **Unlock Wallet** - User unlocks the LOCKED-REMOTE-REV-* directory with a valid REV address and private key, exposing the wallet directory
3. **Create and Edit Files** - User creates directories and files in the mounted drive; writes are acknowledged locally and staged for deployment
4. **On-Chain Sync** - DeployDispatcher deploys file chunks and directory updates to the shard in the background until state is finalized
5. **Cross-OS Access** - The same wallet is unlocked from a mount on a different OS and the shared files are read back

**Key Interactions:**
- Mount succeeds on Linux (libfuse), macOS (macFUSE), and Windows via WSL2
- Wallet unlock with valid credentials exposes wallet contents; invalid credentials leave the directory locked
- A file written on one OS mount is readable byte-identical from a mount on a different OS after sync
- Files survive unmount/remount, served from on-chain state

**Success Metrics:**
- Mount completes in <30s on every supported platform
- Cross-OS read-after-sync succeeds in <60s after deploy finalization
- Multi-OS e2e suite passes in OCI CI on all three platforms

---

## Planned Flows

(Empty)

---

## Related Documentation

- [User Stories](UserStories.md)
- [ToDos](ToDos.md)
