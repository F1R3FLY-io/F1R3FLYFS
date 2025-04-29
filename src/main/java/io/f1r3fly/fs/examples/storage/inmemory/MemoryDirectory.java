package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MemoryDirectory extends MemoryPath {
    private List<MemoryPath> contents = new ArrayList<>();

    public MemoryDirectory(String prefix, String name, DeployDispatcher deployDispatcher, boolean sendToShard) {
        super(prefix, name, deployDispatcher);
        if (sendToShard) {
            enqueueCreatingDirectory();
        }
    }

    public MemoryDirectory(String prefix, String name, MemoryDirectory parent, DeployDispatcher deployDispatcher, boolean sendToShard) {
        super(prefix, name, parent, deployDispatcher);
        if (sendToShard) {
            enqueueCreatingDirectory();
        }
    }

    private void enqueueCreatingDirectory() {
        String rholang = RholangExpressionConstructor.sendDirectoryIntoNewChannel(getAbsolutePath(), Set.of());
        enqueueMutation(rholang);
    }

    public synchronized void add(MemoryPath p, boolean sendToRChain) {
        contents.add(p);
        p.parent = this;

        if (sendToRChain) {
            enqueueUpdatingChildrenList();
        }
    }

    private void enqueueUpdatingChildrenList() {
        String rholang = RholangExpressionConstructor.updateChildren(
            getAbsolutePath(),
            contents.stream().map(MemoryPath::getName).collect(Collectors.toSet())
        );

        enqueueMutation(rholang);
    }

    public synchronized void deleteChild(MemoryPath child, boolean sendToRChain) {
        contents.remove(child);

        if (sendToRChain) {
            enqueueUpdatingChildrenList();
        }
    }

    @Override
    public MemoryPath find(String path) {
        if (super.find(path) != null) {
            return super.find(path);
        }
        while (path.startsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            path = path.substring(1);
        }
        synchronized (this) {
            if (!path.contains(PathUtils.getPathDelimiterBasedOnOS())) {
                for (MemoryPath p : contents) {
                    if (p.name.equals(path)) {
                        return p;
                    }
                }
                return null;
            }
            String nextName = path.substring(0, path.indexOf(PathUtils.getPathDelimiterBasedOnOS()));
            String rest = path.substring(path.indexOf(PathUtils.getPathDelimiterBasedOnOS()));
            for (MemoryPath p : contents) {
                if (p.name.equals(nextName)) {
                    return p.find(rest);
                }
            }
        }
        return null;
    }

    @Override
    public void getattr(FileStat stat, FuseContext fuseContext) {
        stat.st_mode.set(FileStat.S_IFDIR | 0777);
        stat.st_uid.set(fuseContext.uid.get());
        stat.st_gid.set(fuseContext.gid.get());
    }

    public synchronized MemoryDirectory mkdir(String lastComponent, boolean sendToShard) {
        MemoryDirectory newDir = new MemoryDirectory(prefix, lastComponent, this, deployDispatcher, sendToShard);
        contents.add(newDir);

        enqueueUpdatingChildrenList();
        return newDir;
    }

    public synchronized void mkfile(String lastComponent, boolean sendToRChain) {
        contents.add(new MemoryFile(prefix, lastComponent, this, deployDispatcher, sendToRChain));
        enqueueUpdatingChildrenList();
    }

    public synchronized void read(Pointer buf, FuseFillDir filler) {
        for (MemoryPath p : contents) {
            filler.apply(buf, p.name, null, 0);
        }
    }

    public boolean isEmpty() {
        return contents.isEmpty();
    }
}