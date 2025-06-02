package io.f1r3fly.fs.examples.storage.inmemory.notdeployable;

import fr.acinq.secp256k1.Hex;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import io.f1r3fly.fs.examples.storage.inmemory.common.IReadOnlyDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.InMemoryDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.RemountedDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.RemountedFile;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.UnlockedRemoteDirectory;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.utils.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


public class LockedRemoteDirectory extends AbstractNotDeployablePath implements IReadOnlyDirectory {
    private String revAddress;

    private static Logger logger = LoggerFactory.getLogger(LockedRemoteDirectory.class);

    public static class InvalidSigningKeyException extends RuntimeException {
        public InvalidSigningKeyException(String message) {
            super(message);
        }
    }

    public LockedRemoteDirectory(String ravAddress) {
        super("LOCKED-REMOTE-REV-" + ravAddress, null);
        this.revAddress = ravAddress;

    }

    @Override
    public Set<IPath> getChildren() {
        return Set.of();
    }

    public UnlockedRemoteDirectory unlock(String signingKeyRaw, DeployDispatcher deployDispatcher) {
        // check if key is related to the rev address
        byte[] signingKey;
        try {
            signingKey = Hex.decode(signingKeyRaw);
        } catch (IllegalArgumentException e) {
            throw new InvalidSigningKeyException("Invalid signing key format: " + e.getMessage());
        }

        try {
            IPath root = fetchDirectoryFromShard(
                deployDispatcher.getF1R3FlyApi(),
                PathUtils.getPathDelimiterBasedOnOS(),
                revAddress,
                null
            );

            if (!(root instanceof RemountedDirectory)) {
                throw new IllegalStateException("Root directory is not a directory");
            }

            // create a root and won't deploy it to the shard
            return new UnlockedRemoteDirectory(
                revAddress,
                signingKey,
                ((RemountedDirectory) root).getChildren(),
                deployDispatcher,
                false // skip deploy
            );

        } catch (NoDataByPath e) {
            // no previous mount: need to create a new root and deploy to the shard
            return new UnlockedRemoteDirectory(
                revAddress,
                signingKey,
                new HashSet<>(),
                deployDispatcher,
                true); // do deploy
        }
    }

    public IPath fetchDirectoryFromShard(F1r3flyApi f1R3FlyApi, String absolutePath, String name, InMemoryDirectory parent) throws NoDataByPath {
        try {
            List<RhoTypes.Par> pars = f1R3FlyApi.findDataByName(absolutePath);

            RholangExpressionConstructor.ChannelData fileOrDir = RholangExpressionConstructor.parseChannelData(pars);

            if (fileOrDir.isDir()) {
                RemountedDirectory dir = new RemountedDirectory(name, parent);

                Set<IPath> children = fileOrDir.children().stream().map((childName) -> {
                    try {
                        return fetchDirectoryFromShard(f1R3FlyApi, absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, childName, dir);
                    } catch (NoDataByPath e) {
                        logger.error("Error fetching child directory from shard for path: {}",
                            absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, e);
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toSet());

                dir.setChildren(children);
                return dir;

            } else {
                RemountedFile file = new RemountedFile(PathUtils.getFileName(absolutePath), parent);
                long offset = 0;
                offset = file.initFromBytes(fileOrDir.firstChunk(), offset);

                if (!fileOrDir.otherChunks().isEmpty()) {
                    Set<Integer> chunkNumbers = fileOrDir.otherChunks().keySet();
                    Integer[] sortedChunkNumbers = chunkNumbers.stream().sorted().toArray(Integer[]::new);

                    for (Integer chunkNumber : sortedChunkNumbers) {
                        String subChannel = fileOrDir.otherChunks().get(chunkNumber);
                        List<RhoTypes.Par> subChannelPars = f1R3FlyApi.findDataByName(subChannel);
                        byte[] data = RholangExpressionConstructor.parseBytes(subChannelPars);

                        offset = offset + file.initFromBytes(data, offset);
                    }
                }

                file.initSubChannels(fileOrDir.otherChunks());
                return file;
            }

        } catch (NoDataByPath e) {
            logger.warn("No data found for path: {}", absolutePath, e);
            throw e;
        } catch (Throwable e) {
            logger.error("Error fetching directory from shard for path: {}", absolutePath, e);
            throw new RuntimeException("Failed to fetch directory data for " + absolutePath, e);
        }
    }

}