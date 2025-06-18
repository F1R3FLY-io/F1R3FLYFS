# F1r3Drive

F1r3Drive is a FUSE implementation in java using Java Native Runtime (JNR) and F1r3fly Node.

## Prerequisites

The only strict prerequisite for running F1r3Drive is having the necessary FUSE libraries for your system.

- On macOS, install [macFUSE](https://github.com/macfuse/macfuse/wiki/Getting-Started).
- On other systems, see the [jnr-fuse installation guide](https://github.com/SerCeMan/jnr-fuse/blob/master/INSTALLATION.md).

## Getting Started

There are two main parts to using F1r3Drive: running a F1r3fly shard and running the F1r3Drive application itself.

### 1. Running a F1r3fly shard

F1r3Drive connects to a F1r3fly shard. You have two options:

-   **Run a local shard using Docker (Recommended for getting started)**
    This is the easiest way to get a test environment running.
    -   **Requirement:** [Docker and Docker Compose](https://www.docker.com/).
    -   **Command:**
        ```bash
        docker-compose up -d
        ```
    This starts a small, 2-node shard (one validator and one observer).

-   **Connect to an existing remote shard**
    If you have access to a remote shard, you can configure F1r3Drive to connect to it.

### 2. Running the F1r3Drive Application

You can either download a pre-built JAR or build it from the source code.

#### Option A: Use the Pre-built JAR

Download the latest `f1r3drive-*.jar` from the [GitHub Releases page](https://github.com/f1r3fly-io/F1R3FLYFS/releases).

**To run the JAR, you need Java 17.**
If you have cloned this repository and have `direnv` installed, you can simply run `direnv allow` in the project root to get a shell with the correct Java version.

#### Option B: Build from Source

This is for developers who want to modify or contribute to F1r3Drive.

**Development & Build Requirements:**
- [Nix](https://nixos.org/download/)
- [direnv](https://direnv.net/#basic-installation)
- [Protobuf Compiler](https://grpc.io/docs/protoc-installation/)

**Build Steps:**
1.  Clone this repository and `cd` into it.
2.  Run `direnv allow`. This uses Nix to create a shell with all dependencies for the project.
3.  Build the project with Gradle:
    ```bash
    ./gradlew shadowJar -x test
    ```
    The resulting JAR file will be in `build/libs/f1r3drive-*.jar`.

## Usage

Once you have the JAR file and a running shard, see [Demo.md](./Demo.md) for a step-by-step guide on how to mount and use F1r3Drive.
