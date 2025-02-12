# F1r3flyFS

F1r3flyFS is a FUSE implementation in java using Java Native Runtime (JNR) and F1r3fly Node.

## Installation

1. Install Nix: https://nixos.org/download/
   - For more information about Nix and how it works see: https://nixos.org/guides/how-nix-works/

2. Install direnv: https://direnv.net/#basic-installation
   - For more information about direnv and how it works see: https://direnv.net/
  
3. Install protobuf compiler: https://grpc.io/docs/protoc-installation/

4. Clone this repository and after entering the repository, run `direnv allow`. There should be a message asking you to do this. 
   - This will do a one-time compile of all our libraries which will take a couple of minutes. After completion, your environment will be setup.

5. Install `f1r3fly` as per the instructions in the [F1r3fly repository](https://github.com/F1R3FLY-io/f1r3fly/tree/preston/rholang_rust?tab=readme-ov-file#installation).

6. Install [jnr-fuse](https://github.com/SerCeMan/jnr-fuse/blob/master/INSTALLATION.md).

## Usage

See [Demo.md](./Demo.md) for a step-by-step guide on how to run F1r3flyFS.