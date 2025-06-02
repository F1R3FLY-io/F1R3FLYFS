package io.f1r3fly.fs.examples.storage.inmemory.notdeployable;

import java.util.Set;

import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import org.jetbrains.annotations.NotNull;

public class RootDirectory extends AbstractNotDeployablePath implements IDirectory {

    private Set<IPath> children;

    public RootDirectory(Set<IPath> children) {
        super("/", null);
        this.children = children;
    }

    @Override
    public @NotNull String getAbsolutePath() {
        return "/";
    }

    // Special find method for root directory to handle absolute paths
    @Override
    public IPath find(String path) {
        // For root directory, handle absolute paths specially
        if (path.startsWith(separator())) {
            // Remove leading separator and search in children
            path = path.substring(separator().length());
            
            // If path is empty after removing separator, return this (root)
            if (path.isEmpty()) {
                return this;
            }
            
            // If path doesn't contain separator, look for direct child
            if (!path.contains(separator())) {
                return findDirectChild(path);
            }
            
            // Split path into first component and rest
            int separatorIndex = path.indexOf(separator());
            String firstComponent = path.substring(0, separatorIndex);
            String remainingPath = path.substring(separatorIndex);
            
            // Find the first component child and recursively search in it
            IPath child = findDirectChild(firstComponent);
            return child != null ? child.find(remainingPath) : null;
        }
        
        // For non-absolute paths, use the default logic
        return IDirectory.super.find(path);
    }

    @Override
    public void addChild(IPath child) throws OperationNotPermitted {
        children.add(child);
    }

    @Override
    public void deleteChild(IPath child) throws OperationNotPermitted {
        children.remove(child);
    }

    @Override
    public void mkdir(String lastComponent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    public void mkfile(String lastComponent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    public Set<IPath> getChildren() {
        return children;
    }
}
