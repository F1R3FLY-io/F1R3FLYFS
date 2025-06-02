package io.f1r3fly.f1r3drive.filesystem.local;

import java.util.HashSet;
import java.util.Set;

import io.f1r3fly.f1r3drive.errors.OperationNotPermitted;
import io.f1r3fly.f1r3drive.filesystem.common.Directory;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import org.jetbrains.annotations.NotNull;

public class RootDirectory extends AbstractLocalPath implements Directory {

    private final Set<Path> children;

    public RootDirectory() {
        super("/", null);
        children = new HashSet<>();
    }

    @Override
    public @NotNull String getAbsolutePath() {
        return "/";
    }

    // Special find method for root directory to handle absolute paths
    @Override
    public Path find(String path) {
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
            Path child = findDirectChild(firstComponent);
            return child != null ? child.find(remainingPath) : null;
        }
        
        // For non-absolute paths, use the default logic
        return Directory.super.find(path);
    }

    @Override
    public void addChild(Path child) throws OperationNotPermitted {
        children.add(child);
    }

    @Override
    public void deleteChild(Path child) throws OperationNotPermitted {
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
    public Set<Path> getChildren() {
        return children;
    }
}
