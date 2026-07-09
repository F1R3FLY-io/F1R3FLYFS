Installation
==

## Linux

[`libfuse`](https://github.com/libfuse/libfuse) needs to be installed.

#### Ubuntu
```bash
sudo apt-get install libfuse-dev
``` 

## macOS

[`macFUSE`](https://github.com/macfuse/macfuse/wiki/Getting-Started) needs to be installed.

```bash
brew install --cask macfuse
```

After installation, open **System Settings → Privacy & Security** and allow the macFUSE system extension if macOS prompts for approval. A reboot may be required before FUSE mounts are available.

F1r3Drive uses macOS-specific mount options so the volume appears in Finder and avoids writing Apple metadata files to the blockchain:

- `volname=F1r3Drive`
- `local`
- `noappledouble`
- `noatime`
- `attr_timeout=0`, `entry_timeout=0`, `negative_timeout=0`

## Windows

A library implementing the fuse API needs to be installed and the library path must be set via the `jnr-fuse.windows.libpath` system property.
If the system property is not set, jnr-fuse falls back to [`winfsp`](https://github.com/billziss-gh/winfsp), if it is installed.
```batch
choco install winfsp
```

#### Troubleshooting

* If you see the `service java has failed to start` error or corrupted file names/content, setting
the explicit file encoding `-Dfile.encoding=UTF-8` might help.