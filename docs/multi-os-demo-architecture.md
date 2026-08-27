# F1r3Drive Multi-OS File Sharing Architecture

This document describes the architecture and operating model for F1r3Drive file sharing across Linux, macOS, and Windows via WSL2.

F1r3Drive is a FUSE-based filesystem backed by the F1r3fly blockchain. Files written into the mounted drive are staged locally, deployed to a F1r3fly/Spark cluster, finalized on-chain, and then fetched by other clients that mount the same wallet.

For setup and installation details, see:

- [README.md](../README.md)
- [INSTALLATION.md](../INSTALLATION.md)
- [Demo.md](../Demo.md)
- [docs/configuration.md](configuration.md)
- [docs/system_data_flow.md](system_data_flow.md)
- [docs/rholang_data_storage.md](rholang_data_storage.md)

---

## Supported demo platforms

| Platform | Supported path | FUSE dependency |
|---|---|---|
| Linux / Sparky | Native Java process using JNR-FUSE | libfuse |
| macOS | Native Java process using JNR-FUSE | macFUSE |
| Windows | Ubuntu on WSL2 using the Linux/libfuse path | WSL2 + libfuse |

Native Windows FUSE/WinFsp support is not part of the current supported workflow. Windows demonstrations should use WSL2.

---

## Architecture

```mermaid
flowchart LR
    subgraph Clients["Client platforms"]
        Linux["Linux / Sparky\nF1r3Drive + libfuse"]
        Mac["macOS\nF1r3Drive + macFUSE"]
        WSL["Windows / WSL2\nF1r3Drive + libfuse"]
    end

    subgraph ClientRuntime["F1r3Drive runtime"]
        Fuse["FUSE mount\nF1r3DriveFuse"]
        FS["InMemoryFileSystem\nlocal tree/cache"]
        Deploy["DeployDispatcher\nbackground deploy queue"]
        Client["F1r3flyBlockchainClient\ngRPC client"]
    end

    subgraph Cluster["F1r3fly / Spark cluster"]
        Validator["Validator gRPC\nwrite/deploy endpoint"]
        Observer["Observer gRPC\nread/query endpoint"]
        Chain[("Finalized on-chain state\nfile chunks + directory metadata")]
    end

    Linux --> Fuse
    Mac --> Fuse
    WSL --> Fuse

    Fuse --> FS
    FS --> Deploy
    Deploy --> Client
    Client -->|deploy + propose| Validator
    Validator -->|finalized blocks| Chain
    Chain --> Observer
    Observer -->|exploratory reads| Client
    Client --> FS
```

---

## Data flow

### Write flow

When a user creates or edits a file in the mounted wallet directory:

1. The operating system sends a FUSE operation to `F1r3DriveFuse`.
2. `F1r3DriveFuse` translates the operation into the internal filesystem API.
3. `InMemoryFileSystem` updates the local in-memory tree/cache.
4. The write is acknowledged locally so the file appears immediately to the writer.
5. `DeployDispatcher` queues one or more blockchain deploys for file content and directory metadata.
6. `F1r3flyBlockchainClient` sends deploys to the validator gRPC endpoint.
7. After proposal and finalization, the file state becomes available from the cluster.

A local write is not sufficient evidence of cross-platform availability. Other machines can reliably read the file only after deploy finalization succeeds.

### Read flow

When another platform mounts the same wallet:

1. The F1r3Drive process connects to the configured validator and observer endpoints.
2. The wallet is unlocked using the configured REV address and private key.
3. F1r3Drive fetches finalized directory and file metadata from the observer.
4. Fetched on-chain files are represented locally as `FetchedFile` / `FetchedDirectory` nodes.
5. The user reads files through the mounted filesystem path.

---

## Cluster endpoints

F1r3Drive requires two gRPC endpoints:

| Endpoint | Purpose | Typical local demo port |
|---|---|---:|
| Validator gRPC | Accepts deploys/writes | `40412` |
| Observer gRPC | Serves finalized reads/query state | `40452` |

On the machine running the cluster, these endpoints are usually referenced as `localhost`. Remote clients such as macOS or WSL should use the cluster host or IP address.

Example placeholder configuration:

```bash
export F1R3DRIVE_VALIDATOR_HOST=<cluster-host-or-ip>
export F1R3DRIVE_VALIDATOR_PORT=40412
export F1R3DRIVE_OBSERVER_HOST=<cluster-host-or-ip>
export F1R3DRIVE_OBSERVER_PORT=40452
```
---

## Fresh remount behavior

In the current implementation, remote on-chain state is fetched when a wallet is mounted and unlocked. Existing mounts may not immediately live-refresh files written by another platform.

For reliable cross-platform verification:

1. Write the file on platform A.
2. Wait for deploy finalization on platform A.
3. Reset/remount platform B.
4. Verify the file on platform B.

This behavior is important for demos and troubleshooting. If a file was written from macOS or WSL after Linux was already mounted, Linux may need a fresh remount before the new file appears.

---

## Verification model

The demo helper scripts use SHA-256 checksums to verify file integrity.

Each demo write creates two files:

```text
<label>.txt
<label>.txt.sha256
```

- `<label>.txt` contains the demo content.
- `<label>.txt.sha256` contains the expected SHA-256 hash of the content.

Verification recalculates the SHA-256 hash of `<label>.txt` and compares it with the expected value in `<label>.txt.sha256`.

Successful verification indicates that the file content read from the mounted filesystem matches the expected content.

---

## Operational notes

- Keep the F1r3Drive mount process running while using the mounted directory.
- Use fresh labels for repeated demos to avoid stale data from earlier failed runs.
- Wait for `Deploy finalized successfully` before expecting another platform to read a newly written file.
- Reset stale FUSE mounts if the mount point reports `Transport endpoint is not connected`.
- Avoid destructive cluster resets unless needed; resetting Docker volumes removes local demo chain state.

---

## Common failure modes

| Symptom | Likely cause | Resolution |
|---|---|---|
| Wallet directory is missing | F1r3Drive is not mounted or wallet unlock failed | Start the mount process and keep it running |
| File appears locally but not on another OS | Deploy has not finalized or reader mount is stale | Wait for finalization and fresh-remount the reader |
| `Could not deploy, casper instance was not available yet` | Cluster is not ready to accept deploys | Wait for cluster readiness or restart the demo cluster |
| `Checksum mismatch` | File content and `.sha256` file differ, or stale label was reused | Use a fresh label or regenerate checksum after edits |
| `Unable to resolve host` | Remote platform cannot resolve cluster hostname | Use the cluster IP or configure hostname resolution |

