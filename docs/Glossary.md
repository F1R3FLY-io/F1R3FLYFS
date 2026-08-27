---
doc_type: glossary
version: "1.1"
last_updated: 2026-08-27
status: proposed # becomes `ratified` after human review (see /review-codebase)
---

# F1r3Drive Glossary

Canonical terminology for F1r3Drive code, documentation, and reviews. When a doc, comment, or PR needs one of these concepts, use the term defined here. The [Preferred Terminology](#preferred-terminology) table at the end lists terms to avoid.

Related documents: [multi-os-demo-architecture.md](multi-os-demo-architecture.md), [system_data_flow.md](system_data_flow.md), [rholang_data_storage.md](rholang_data_storage.md), [features.md](features.md), [configuration.md](configuration.md).

---

## Platform and Network

**F1r3Drive** — This project: a FUSE-based filesystem, written in Java 17, that stores its data on the F1r3fly blockchain. The runnable artifact is the shaded JAR `f1r3drive-app.jar` with entry point `F1r3DriveCli`.

**F1r3fly** — The blockchain platform F1r3Drive stores data on. F1r3Drive talks to it over gRPC. Node implementations include the original Scala node and **f1r3node-rust**, the Rust node used for synchronized on-chain storage (EPIC-001 / TASK-001-4).

**Shard** — A running F1r3fly network that F1r3Drive connects to: at minimum one validator and one observer. Always "shard", never "cluster". A local demo shard is managed with **shardctl** from the `system-integration` repository.

**Validator** — The shard node that accepts deploys (writes). F1r3Drive reaches it through the validator gRPC endpoint (`--host`/`--port`, demo default port `40412`).

**Observer** — The read-only shard node that serves finalized state. F1r3Drive reaches it through the observer gRPC endpoint (`--observer-host`/`--observer-port`, demo default port `40452`). Reads from the observer are **exploratory reads**: queries against finalized state that do not create a deploy.

**Deploy** — A signed Rholang term submitted to the validator. Every file write, directory update, and token transfer becomes one or more deploys.

**Propose** — The step that gathers pending deploys into a block. Two modes exist: **autopropose** (the shard's heartbeat proposes on an interval; the default) and **manual propose** (`--manual-propose`: F1r3Drive proposes and awaits finalization after each deploy).

**Finalization** — The point at which a proposed block becomes permanent on-chain state. Only finalized data is visible to other mounts. A local write is not evidence of cross-platform availability; "deploy finalized" is.

**REV** — The F1r3fly token. A **REV address** identifies a wallet; a REV **private key** signs its deploys and unlocks its wallet directory. The smallest unit is **dust**. On f1r3node-rust, a deploy's execution capacity is derived from the signer's REV custody (see [Shard Cost Accounting](#shard-cost-accounting-f1r3node-rust)).

**Rholang** — The smart-contract language of F1r3fly. `RholangExpressionConstructor` builds the Rholang terms that store filesystem data. **MeTTa** is a second deployable language; `.rho` and `.metta` deploy-on-rename are planned features.

---

## Filesystem Model

**Mount point** — The host directory where F1r3Drive is mounted (the first CLI argument). FUSE operations on paths under it are routed into F1r3Drive.

**FUSE** — Filesystem in Userspace, the kernel interface F1r3Drive implements through **JNR-FUSE** (Java binding). The host library is **libfuse** on Linux and WSL2, **macFUSE** on macOS. Native Windows (WinFsp) is out of scope; Windows uses **WSL2** with the Linux path.

**Path node** — A typed node in the in-memory tree (base types `Path`, `Directory`, `File` in `filesystem/common/`). Two families exist:

- **Local path** (`filesystem/local/`) — exists only in the mount, never deployed: `RootDirectory`, `LockedWalletDirectory`, `TokenDirectory`, `TokenFile`.
- **Deployable path** (`filesystem/deployable/`) — state is pushed to the shard: `BlockchainFile`, `BlockchainDirectory`, `FetchedFile`, `FetchedDirectory`, `UnlockedWalletDirectory`.

**Wallet directory** — A top-level directory bound to one REV address. It is **locked** (`LockedWalletDirectory`, shown as `LOCKED-REMOTE-REV-<address>`, appears empty) until **unlock** with a valid REV address + private key turns it into an `UnlockedWalletDirectory`, whose contents are readable and writable. Unlock happens via CLI flags (`--address`/`--private-key`), the Finder Extension, or filesystem convention.

**Fetched node** — A `FetchedFile` or `FetchedDirectory`: the local representation of data that already exists on-chain and was pulled through the observer at mount/unlock time, as opposed to `BlockchainFile`/`BlockchainDirectory` nodes created locally and deployed out.

**Token file** — A `.token` file under a wallet's `TokenDirectory` (`.token` folder) representing REV balance. Moving a token file into another wallet's folder triggers an on-chain **token transfer**.

**Fresh remount** — Unmounting and remounting so a mount picks up state finalized after it was opened. Existing mounts do not live-refresh remote writes; cross-platform verification is write → finalize → fresh-remount reader → read.

**Bridge** — `filesystem/bridge/` (`FSFileStat`, `FSFillDir`, `FSPointer`, `FSStatVfs`, `FSContext`): adapter types that decouple JNR-FUSE structs from filesystem logic.

---

## On-Chain Storage Model

Defined in [rholang_data_storage.md](rholang_data_storage.md); implemented by `RholangExpressionConstructor`.

**Channel-based addressing** — Every stored item (directory, file, chunk) lives on a Rholang channel named by its full virtual path, e.g. `@"/mountID/1.txt"`.

**Root namespace (mountID)** — The base path that prefixes all channels for one wallet; it embeds the wallet's REV address.

**Directory node** — An on-chain record with `"type":"d"`, holding `children` (relative names of entries) and `lastUpdated`.

**File metadata node** — An on-chain record with `"type":"f"`, holding `lastUpdated`, the inline **firstChunk** (first bytes of the file, so small files need no extra reads), and an **otherChunks** map.

**Chunk** — A segment of file content. Chunks beyond the first are published on numbered sub-channels (`/mountID/1.txt/1`, `/mountID/1.txt/2`, …); `otherChunks` maps sequence index → channel for ordered reassembly. File size is derived at mount time, not stored on-chain.

---

## Shard File I/O (f1r3node-rust)

The `fileio-phase-1-2` line of f1r3node-rust implements the **File I/O FIP**: a capability-secured POSIX filesystem that Rholang deploys can use. F1r3Drive today stores data through its own channel-based model (above); File I/O is the shard-side capability that future F1r3Drive storage can target. Spelling: "File I/O" in prose, `fileio` in commit scopes and test names; never "FileIO".

**`Fs` capability** — The top-level File I/O agent, minted once at genesis. Deploys call `openFile`/`openDir` with a **logical name**, never a host path. It is reached through a registry URI (`rho:id:<hash>`); the canonical `rho:io:fs:1.0.0` URI is not wired yet. MVP caveat: all deploys currently share one global `Fs` instance.

**Rholang agent library** — `Fs`, `File`, `Dir`, `Stream`, `Buffer`, `Stdin`, `Stdout`: the only user-reachable File I/O surface. The 30 native syscall bridges under `rho:io:fs:native:1.0.0/*` are deliberately unreachable from user deploys.

**Static provisioning** — The operator maps logical names to host paths and modes through four HOCON buckets (`oracle-static-files`/`-dirs`, `consensus-static-files`/`-dirs`). Unprovisioned names return `FSERR_UNSUPPORTED`. Opening can **attenuate** (downgrade) the provisioned mode but never upgrade it.

**Oracular vs consensus mode** — Per-capability execution mode. **Oracular**: node-local, best-effort, the operator's business. **Consensus**: every mutation is journaled to the WAL, snapshotted, Merkle-anchored on-chain, and deterministically replayable by every validator; host-transient metadata (timestamps, owner) is omitted. The default is consensus (fail-closed).

**WAL (write-ahead log)** — The journal of operations on consensus-mode capabilities. Bounded at 65,536 entries per deploy (`FSERR_QUOTA_EXCEEDED` on overflow); drained at deploy boundaries, and any range locks the deploy held are auto-released.

**Snapshot / Merkle root** — A snapshot is a canonical encoding of a WAL slice (not a materialized filesystem image), split into 4 MiB chunks; a Blake2b256 Merkle tree over the chunk hashes yields the root committed on-chain. Snapshot cadence is a shard-wide genesis parameter; joining validators fetch chunks peer-to-peer.

**`FSERR_*` codes** — File I/O errors are `[false, code, msg]` tuples: `FSERR_BAD_ARG`, `FSERR_IO`, `FSERR_NOT_FOUND`, `FSERR_ALREADY_EXISTS`, `FSERR_PERM`, `FSERR_UNSUPPORTED`, `FSERR_QUARANTINE`, `FSERR_CLOSED`, `FSERR_BUSY`, `FSERR_QUOTA_EXCEEDED`, `FSERR_CROSS_DEVICE`, `FSERR_CANCELLED`.

**Range lock** — A byte-range lock keyed on `(device, inode)`, granted as a `LockToken`. A hard invariant under consensus mode (contention yields a deterministic `FSERR_BUSY`); best-effort under oracular mode. All locks release automatically at deploy end.

**Quarantine (safe descend)** — TOCTOU-hardened path resolution: each path component is descended with `openat(O_NOFOLLOW)`; symlinks and root-inode swaps abort with `FSERR_QUARANTINE`.

---

## Shard Cost Accounting (f1r3node-rust)

f1r3node-rust replaces the legacy phlo gas market. There is no client-selected `phloLimit × phloPrice`; a deploy's capacity is derived by the protocol from the signer's authenticated REV custody. See "Client impact" in [multi-os-demo-architecture.md](multi-os-demo-architecture.md#f1r3node-rust-cost-accounting-and-file-io).

**Cost accounting** — Two dimensions: **compute authority** (one unit per COMM) and **storage bytes** (introduced, delivered, and committed-trace bytes under a versioned byte-cost schedule). Admission requires the signer's supply to cover the deploy's frozen bounds plus the proposer fee.

**COMM** — One successful atomic RSpace synchronization between a waiting continuation and its required data. The unit of compute-authority accounting; an N-channel join is one COMM.

**Phlogiston** — Legacy name that survives only in identifiers: the `out_of_phlogistons` API error, the `min_phlo_price` ingress config, and the validator-side `initial-phlogiston`/`epoch-phlogiston` PoS parameters. Never a client-settable deploy parameter.

**SystemVault** — The REV custody contract (called "RevVault" in the cost-accounting papers). Settlement happens inside one transaction: reserve → exact debit → fee transfer → refund of the unused allocation.

**Deploy cost (`cost` field)** — The reported scalar is committed COMM count plus canonical byte cost — **not** the physical REV debit. Clients that need settlement proof must validate the funding-certificate/cost-witness bundle instead.

**Funding certificate / cost witness** — New deploy-response evidence: `authorityFundingCertificate` (frozen compute/byte bounds and fee against one pre-state), `authorityCostWitness` (the execution's realized draws; replay mismatch is objective block invalidity), plus `preStateHash`, `postStateHash`, and `admissionStatus`.

**Out-of-phlo (OOP) boundary** — The deterministic event at which a deploy's budget exhausts. Every validator fails the deploy at the same point with `out_of_phlogistons`, and all tuplespace effects revert.

**File I/O cost weights** — Every File I/O syscall charges a consensus-parameter weight at handler entry: constant ops (open, stat, close, seek…) cost 100; path mutations (rename, copy, remove) 200; reads 100 + 1/byte **on the requested count**; writes 100 + 2/byte (the WAL append); directory listings 50 + 32/entry. Divergent weights split consensus, so changes are hard-fork events.

**Cost estimation** — `POST /api/estimate-cost` returns a cost preview. Pass the real `deployer` key: an estimate under an ephemeral identity can be significantly lower than the real cost.

---

## Runtime Components

**`F1r3DriveCli`** (`app/`) — picocli entry point; parses flags, owns mount/unmount lifecycle including the shutdown hook and double-Ctrl+C hard stop.

**`F1r3DriveFuse` / `FuseAdapter`** (`app/linux/fuse/`) — The FUSE binding: translates JNR-FUSE callbacks into `FileSystem` operations.

**`FileSystem` / `InMemoryFileSystem`** (`filesystem/`) — The core abstraction and its implementation: the in-memory path-node tree plus local staging cache. Writes are acknowledged here immediately (**local acknowledgment**) before background sync.

**Background state pipeline** (`background/state/`) — The asynchronous event flow: a `BlockingEventQueue` of `StateChangeEvents` feeds `EventProcessor`s via `StateChangeEventsManager`, so filesystem changes sync to the shard without blocking FUSE callers.

**`DeployDispatcher`** (`blockchain/client/`) — Background queue that batches file chunks and directory updates into bulk deploys.

**`F1r3flyBlockchainClient`** (`blockchain/client/`) — The gRPC client for validator and observer; logs `Deploy finalized successfully` (DEBUG level) when a deploy finalizes.

**`AESCipher`** (`encryption/`) — Singleton AES cipher initialized from `--key-file`; backs the planned transparent `.encrypted` feature.

**Finder Extension** — The optional macOS extension (separate repo `f1r3drive-extension`) for badges, context menus, and auto-unlock. It talks to `FinderSyncExtensionServiceServer`, a local gRPC server on `localhost:54000`.

**Typed filesystem errors** (`errors/`) — `PathNotFound`, `FileAlreadyExists`, `OperationNotPermitted`, `DirectoryNotEmpty`, `PathIsNotADirectory`, `PathIsNotAFile`, `NoDataByPath`, `F1r3flyDeployError`, `InvalidSigningKeyException`. Prefer these over generic exceptions in filesystem code.

---

## Testing and Delivery

**Unit tests** — `src/test/java`, JUnit 5, run by `./gradlew test`; no shard needed.

**E2E tests** — `src/e2e/java`, a separate source set run by `./gradlew e2eTest`; use Testcontainers and require a live shard. [features.md](features.md) maps each user-facing feature to its e2e test.

**OCI integration suite** — The multi-OS integration tests run in OCI containers via GitHub Actions (`.github/workflows/multi-os-integration.yml`), extending the e2e baseline across Linux, macOS, and WSL2 (TASK-001-5).

**Checksum verification** — The demo convention of pairing `<label>.txt` with `<label>.txt.sha256` and comparing recomputed SHA-256 hashes to prove cross-platform integrity.

---

## Preferred Terminology

| Preferred | Avoid | Why |
|---|---|---|
| F1r3fly **shard** | cluster, Spark cluster | "Shard" is the established term (README, Demo.md, shardctl); "Spark" collides with Apache Spark |
| **validator** / **observer** | write node / read node | Matches CLI flags and shard roles |
| **deploy** (noun and verb) | upload, push, commit | Matches F1r3fly and gRPC API vocabulary |
| **finalization / finalized** | confirmed, synced | The on-chain visibility boundary other mounts depend on |
| **wallet directory** (locked/unlocked) | account folder | Matches `LockedWalletDirectory` / `UnlockedWalletDirectory` |
| **unlock** | login, sign in | The filesystem-level operation on a wallet directory |
| **token file / token transfer** | coin, payment file | Matches `TokenFile` and the `.token` convention |
| **fresh remount** | refresh, reload | The documented recovery step for stale mounts |
| **path node** (local / deployable / fetched) | inode, entry | Matches the `filesystem/` type hierarchy |
| **chunk** | block, segment | "Block" is reserved for blockchain blocks |
| Linux (plain) | Sparky | Demo hostnames stay out of shared docs |
| **File I/O** (prose), `fileio` (code/commit scope) | FileIO | Matches f1r3node-rust usage; "FileIO" appears nowhere in that codebase |
| REV custody / cost accounting | phlo limit, gas | The phlo gas market is removed in f1r3node-rust; `phloLimit`/`phloPrice` no longer exist on the wire |
| **SystemVault** | RevVault | Implementation name; "RevVault" is the papers' name for the same contract |
| **dust** | smallest denomination, wei | The named smallest REV unit in the wire protocol |
| **oracular** / **consensus** (File I/O modes) | local / replicated | The exact mode strings at the Rholang boundary |
