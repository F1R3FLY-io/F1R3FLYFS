Installation
==

## Linux

[`libfuse`](https://github.com/libfuse/libfuse) needs to be installed.

#### Ubuntu
```bash
sudo apt-get install libfuse-dev
``` 

## MacOS

[`osxfuse`](https://osxfuse.github.io) needs to be installed.

```bash
brew cask install osxfuse
```

## Windows via WSL2

F1r3Drive's supported Windows path is WSL2 (Windows Subsystem for Linux) using the Linux/libfuse build inside the WSL distribution. Native Windows FUSE/WinFsp execution is not part of the current supported workflow.

### 1. Install WSL2

From an elevated PowerShell prompt on Windows:

```powershell
wsl --install -d Ubuntu
wsl --set-default-version 2
```

Restart Windows if prompted, then open the Ubuntu/WSL terminal.

### 2. Install WSL dependencies

Inside WSL:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk git libfuse-dev fuse
```

If Docker/Testcontainers-based e2e tests are needed, install Docker Desktop on Windows and enable WSL integration for the Ubuntu distribution:

```text
Docker Desktop → Settings → Resources → WSL Integration → Enable Ubuntu
```

Then verify from WSL:

```bash
docker info
```

### 3. Build F1r3Drive inside WSL

Clone and build from the WSL filesystem (for example under `~/src`, not `/mnt/c`) for best filesystem behavior:

```bash
mkdir -p ~/src
cd ~/src
git clone https://github.com/F1R3FLY-io/f1r3drive.git
cd f1r3drive
./gradlew test
./gradlew shadowJar -x test
```

### 4. Mount F1r3Drive inside WSL

Create a mount point in WSL and run F1r3Drive against a running shard:

```bash
mkdir -p ~/f1r3drive-mount
java -jar build/libs/f1r3drive-app.jar ~/f1r3drive-mount \
  --key-file ~/cipher.key \
  --host localhost --port 40402 \
  --observer-host localhost --observer-port 40403 \
  --address <rev-address> \
  --private-key <private-key>
```

### 5. Access the mounted drive from Windows

From Windows Explorer, open the WSL share path for your distribution, for example:

```text
\\wsl$\Ubuntu\home\<wsl-user>\f1r3drive-mount
```

Create/read/write/rename/delete operations should be performed through the mounted directory and verified from both WSL and Windows Explorer.

### 6. Unmount

Inside WSL:

```bash
fusermount -u ~/f1r3drive-mount || fusermount3 -u ~/f1r3drive-mount
```

#### Troubleshooting

* If you see the `service java has failed to start` error or corrupted file names/content, setting
the explicit file encoding `-Dfile.encoding=UTF-8` might help.