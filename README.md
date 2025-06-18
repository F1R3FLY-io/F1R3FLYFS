# F1r3Drive

F1r3Drive is a FUSE implementation in java using Java Native Runtime (JNR) and F1r3fly Node.

## Prerequisites

To build and run F1r3Drive, you'll need the following:

- [Nix](https://nixos.org/download/)
- [direnv](https://direnv.net/#basic-installation)
- [Protobuf Compiler](https://grpc.io/docs/protoc-installation/)
- [Docker and Docker Compose](https://www.docker.com/)
- [jnr-fuse](https://github.com/SerCeMan/jnr-fuse/blob/master/INSTALLATION.md) (or [macFUSE](https://github.com/macfuse/macfuse/wiki/Getting-Started) on macOS)

## Installation

1.  Clone this repository and `cd` into it.
2.  Run `direnv allow`. This will configure your shell for the project.
3.  Start a local F1r3fly test shard using Docker:
    ```bash
    docker-compose up -d
    ```
    This command starts a small, 2-node shard (one validator and one observer) that's sufficient for trying out F1r3Drive locally.

## Running F1r3Drive

You can either download the pre-built application or build it from source.

### Using the pre-built JAR

Download the latest `f1r3drive-*.jar` from the [GitHub Releases page](https://github.com/f1r3fly/F1R3FLYFS/releases).

### Building from source

You can build the project using Gradle:

```bash
./gradlew shadowJar -x test
```
The JAR file will be located in `build/libs/f1r3drive-*.jar`.

Once you have the JAR file, see [Demo.md](./Demo.md) for a step-by-step guide on how to run F1r3Drive.
