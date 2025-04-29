package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;

import java.io.IOException;

public abstract class MemoryPath extends DeployablePath {
    protected String name;
    protected MemoryDirectory parent;
    protected String prefix;

    public MemoryPath(String prefix, String name, DeployDispatcher deployDispatcher) {
        this(prefix, name, null, deployDispatcher);
    }

    public MemoryPath(String prefix, String name, MemoryDirectory parent, DeployDispatcher deployDispatcher) {
        this.name = name;
        this.parent = parent;
        this.prefix = prefix;
        this.deployDispatcher = deployDispatcher;
    }

    public synchronized void delete(boolean sendToShard) {
        if (parent != null) {
            parent.deleteChild(this, sendToShard);
            parent = null;
        }

        if (sendToShard) {
            String rholangExpression = RholangExpressionConstructor.forgetChanel(getAbsolutePath());
            enqueueMutation(rholangExpression);
        }
    }

    public MemoryPath find(String path) {
        while (path.startsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            path = path.substring(1);
        }
        if (path.equals(name) || path.isEmpty()) {
            return this;
        }
        return null;
    }

    public abstract void getattr(FileStat stat, FuseContext fuseContext);

    public void rename(String newName, MemoryDirectory newParent, boolean sendToShard) throws IOException {
        while (newName.startsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            newName = newName.substring(1);
        }
        parent.deleteChild(this, sendToShard);
        parent = newParent;
        String oldPath = getAbsolutePath();
        name = newName;
        if (sendToShard) {
            String newPath = getAbsolutePath();
            enqueueMutation(RholangExpressionConstructor.renameChanel(oldPath, newPath));
        }
    }

    public String getAbsolutePath() {
        if (parent == null) {
            return prefix + name;
        } else {
            return parent.getAbsolutePath() + PathUtils.getPathDelimiterBasedOnOS() + name;
        }
    }

    public String getName() {
        return name;
    }
}
