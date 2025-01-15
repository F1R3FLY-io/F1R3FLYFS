package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.examples.Config;
import io.f1r3fly.fs.examples.storage.grcp.listener.NotificationConstructor;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MemoryDirectory extends MemoryPath {

    protected List<MemoryPath> children = new ArrayList<>();

    public MemoryDirectory(Config config, String name, boolean sendToShard) {
        super(config, name);
        if (sendToShard) {
            enqueueCreatingDirectory();
        }
    }

    public Collection<MemoryPath> getChild() {
        return children;
    }

    public MemoryDirectory(Config config, String name, MemoryDirectory parent, boolean sendToShard) {
        super(config, name, parent);
        if (sendToShard) {
            enqueueCreatingDirectory();
        }
    }

    private void enqueueCreatingDirectory() {
        String rholang = RholangExpressionConstructor.sendDirectoryIntoNewChannel(getChannelName(), Set.of());
        enqueueMutation(rholang);
        triggerNotificationWithReason(NotificationConstructor.NotificationReasons.DIRECTORY_CREATED);
    }



    public synchronized void addChildren(MemoryPath p, boolean sendToRChain) {
        if (children.stream().anyMatch((c) -> c.getName().equals(p.name))) {
            return;
        }

        children.add(p);
        p.parent = this;

        if (sendToRChain) {
            enqueueUpdatingChildrenList();
        }
    }

    public void enqueueUpdatingChildrenList() {
        String rholang = RholangExpressionConstructor.updateChildren(
            getChannelName(),
            children.stream().map(MemoryPath::getName).collect(Collectors.toSet())
        );

        enqueueMutation(rholang);
    }

    public synchronized void deleteChild(MemoryPath child, boolean sendToShard) {
        children.remove(child);

        if (sendToShard) {
            enqueueUpdatingChildrenList();
        }
    }

    @Override
    @Nullable
    public MemoryPath find(String path) {
        if (super.find(path) != null) {
            return super.find(path);
        }
        while (path.startsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            path = path.substring(1);
        }
        synchronized (this) {
            if (!path.contains(PathUtils.getPathDelimiterBasedOnOS())) {
                for (MemoryPath p : children) {
                    if (p.name.equals(path)) {
                        return p;
                    }
                }
                return null;
            }
            String nextName = path.substring(0, path.indexOf(PathUtils.getPathDelimiterBasedOnOS()));
            String rest = path.substring(path.indexOf(PathUtils.getPathDelimiterBasedOnOS()));
            for (MemoryPath p : children) {
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

    public synchronized void mkdir(String lastComponent, boolean sendToShard) {
        children.add(new MemoryDirectory(config, lastComponent, this, sendToShard));

        if (sendToShard) {
            enqueueUpdatingChildrenList();
        }
    }

    public synchronized void mkfile(String lastComponent, boolean sendToRChain) {
        MemoryFile memoryFile = new MemoryFile(config, lastComponent, this, sendToRChain);
        children.add(memoryFile);
        if (sendToRChain) {
            enqueueUpdatingChildrenList();
        }
    }



    public synchronized void read(Pointer buf, FuseFillDir filler) {
        for (MemoryPath p : children) {
            filler.apply(buf, p.name, null, 0);
        }
    }

    public boolean isEmpty() {
        return children.isEmpty();
    }


}