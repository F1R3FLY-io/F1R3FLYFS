---
doc_type: discovery
discovered_by: claude-session-7d9c31
discovered_at: 2026-05-27
relevance: [PR-39, PR-40, feature/platform-abstraction, feature/integration-updates]
decision: sequential (option 3) — land PA→dev first, then rebase IU onto dev and fix
---

## Finding: feature/platform-abstraction (PA) vs feature/integration-updates (IU)

Mapped to decide a reconciliation strategy before investing in the macOS native-loading gap.

### Branch relationship

```
main  ──(main ⊂ dev; dev = main + 9)──►  dev          ← true integration branch
                                          │
   PR #39: feature/platform-abstraction → dev     PR #40: feature/integration-updates → main
   (PA — clean, this session's branch)            (IU — the 42k overhaul reviewed in PR #40)
```

- Merge-base of PA and IU = `032e3ff` (the #43 e2e fix). **It contains zero `platform/` files** — all
  platform/cache/placeholder code was built *after* the branches diverged, so PA and IU each hold an
  **independently-evolved** copy. A literal `git merge` of the two would be enormous and conflict-heavy;
  reconciliation = pick one line + port specific pieces, not merge.
- PA has 12 commits not in IU; IU has 25 not in PA. Neither contains the other.
- PA's unique-vs-IU commits (CLI/Finder #44, shardctl e2e migration, docs, gitignore) are **all already on `dev`**
  (PA picked them up by merging dev). So PA ≈ dev + the platform-abstraction Java work.
- IU targets `main` directly (bypassing dev). Since main ⊂ dev, merging IU→main would invert the normal flow.

### Production-code inventory

- **PA-only files: none in production** — only 3 demo tests (`BlockchainPhase4DemonstrationTest`,
  `Phase4DemonstrationTest`, `SimplePhase4DemonstrationTest`).
- **IU-only production:** the entire macOS native layer (`src/macos/native/` Makefile + `.m`/`.c`, the two
  `lib*.dylib` in resources, `NativeLibraryLoader`), platform source-sets (`src/linux/java`, `src/macos/java`),
  `platform/F1r3DriveChangeListener` (420 lines), and the entire `folders/` wallet+token system (18 files),
  plus ~14 tests.

### Shared-code divergence (PA → IU: +5,259 / −1,425 across 27 files)

Overwhelmingly additive — IU is a superset even in shared files. Highlights:
`InMemoryFileSystem` +1,555 (atomic ops, wallet integration); `app/linux/fuse/F1r3DriveFuse` +1,000;
`F1r3flyBlockchainClient` +436; `TokenDirectory` +458; `FileProviderIntegration` 521 changed (native binding
rewrite); `LockedWalletDirectory` +271; cache/* +411. The one place IU is *leaner*:
`PlaceholderInfo` −327 (IU simplified it, removing the unused `blockchainAddress` field).

## Implications

- **IU ⊇ PA in production.** They are not complementary; this is a pick-one decision, not a "best of both" merge.
- The **macOS native-loading gap** (FSEventsMonitor/FileProviderIntegration `System.loadLibrary` with no native
  build/dylib/loader on PA) is purely downstream of the branch choice: it exists only because PA is the
  clean-but-incomplete line. Keep IU → native already works; keep PA → port a *cleaned* native layer.
- IU is the line reviewed in PR #40 as **not-mergeable as-is** (auth bypass `PhysicalWalletManager.validatePrivateKey`,
  path traversal in wallet ops, silent write data loss `BlockchainFile`, deploy-queue hang, and the whole
  platform/cache/placeholder layer dead-wired). See `docs/work-logs/task-pr40-review-20260527T184608Z.md`.

## Decision (chosen): Sequential (option 3)

1. Land **PA → `dev`** (PR #39) now — small, clean, reviewable; establishes a good `dev` baseline.
2. Rebase **IU onto `dev`**, retarget **PR #40 → `dev`** (not main), reconcile the diverged shared code.
3. Work the PR #40 review findings and port/clean the native layer on the rebased IU — not into PA.

Native work is deliberately deferred until after PA lands on dev and IU is rebased.
