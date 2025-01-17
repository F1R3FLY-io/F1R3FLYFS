package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.examples.Config;
import io.f1r3fly.fs.examples.storage.grcp.listener.NotificationConstructor;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;

import java.io.IOException;

public abstract class MemoryPath extends DeployablePath {
    protected String name;
    protected MemoryDirectory parent;

    public MemoryPath(Config config, String name) {
        this(config, name, null);
    }

    public MemoryPath(Config config, String name, MemoryDirectory parent) {
        this.name = name;
        this.parent = parent;
        this.config = config;
    }

    public synchronized void delete(boolean sendToShard) {
        if (sendToShard) {
            String rholangExpression = RholangExpressionConstructor.forgetChanel(getChannelName());
            enqueueMutation(rholangExpression);
            triggerNotificationWithReason(NotificationConstructor.NotificationReasons.DELETED);
        }

        if (parent != null) {
            parent.deleteChild(this, sendToShard);
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

    public void rename(String newName, MemoryDirectory newParent, boolean renameOnShard, boolean updateParentsOnShard) throws IOException {
        if (parent == null) {
            throw new IOException("Cannot rename root directory");
        }


        while (newName.startsWith(PathUtils.getPathDelimiterBasedOnOS())) {
            newName = newName.substring(1);
        }
        String oldChannelName = getChannelName();
        String oldPath = getAbsolutePath();


        name = newName;

        boolean parentChanged = !parent.getAbsolutePath().equals(newParent.getAbsolutePath());



        if (parentChanged) {
            parent.deleteChild(this, updateParentsOnShard);
            newParent.addChildren(this, updateParentsOnShard);
        } else {
            if (updateParentsOnShard) {
                parent.enqueueUpdatingChildrenList();
            }
        }

        if (renameOnShard) {
            String newChannelName = getChannelName();
            String newPath = getAbsolutePath();

            enqueueMutation(RholangExpressionConstructor.renameChanel(oldChannelName, newChannelName));

            // use old name in a notification, so sending now and then changing the name
            triggerRenameNotification(oldPath, newPath);
        }
    }

    public String getChannelName() {
        return config.mountName + getAbsolutePath();
    }
    public String getAbsolutePath() {
        if (parent == null) {
            return "";
        } else {
            return parent.getAbsolutePath() + PathUtils.getPathDelimiterBasedOnOS() + name;
        }
    }

    public String getName() {
        return name;
    }
}
