package io.f1r3fly.fs.examples.storage.inmemory.deployable;

import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RemountedDirectory extends InMemoryDirectory {
    public RemountedDirectory(String name, InMemoryDirectory parent) {
        super(name, parent, false);
    }

    public void setChildren(Set<IPath> children) {
        this.children = children;
    }
}