package io.f1r3fly.f1r3drive.filesystem.local;

import fr.acinq.secp256k1.Hex;
import io.f1r3fly.f1r3drive.blockchain.client.DeployDispatcher;
import io.f1r3fly.f1r3drive.errors.NoDataByPath;
import io.f1r3fly.f1r3drive.blockchain.client.F1r3flyBlockchainClient;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import io.f1r3fly.f1r3drive.filesystem.common.ReadOnlyDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.BlockchainDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.FetchedDirectory;
import io.f1r3fly.f1r3drive.filesystem.deployable.FetchedFile;
import io.f1r3fly.f1r3drive.filesystem.deployable.UnlockedWalletDirectory;
import io.f1r3fly.f1r3drive.blockchain.rholang.RholangExpressionConstructor;
import io.f1r3fly.f1r3drive.filesystem.utils.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


public class LockedRemoteDirectory extends AbstractLocalPath implements ReadOnlyDirectory {
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
    public Set<Path> getChildren() {
        return Set.of();
    }

    public UnlockedWalletDirectory unlock(String signingKeyRaw, DeployDispatcher deployDispatcher) {
        // check if key is related to the rev address
        byte[] signingKey;
        try {
            signingKey = Hex.decode(signingKeyRaw);
        } catch (IllegalArgumentException e) {
            throw new InvalidSigningKeyException("Invalid signing key format: " + e.getMessage());
        }

        try {
            Path root = fetchDirectoryFromShard(
                deployDispatcher.getF1R3FlyApi(),
                PathUtils.getPathDelimiterBasedOnOS(),
                revAddress,
                null
            );

            if (!(root instanceof FetchedDirectory)) {
                throw new IllegalStateException("Root directory is not a directory");
            }

            // create a root and won't deploy it to the shard
            return new UnlockedWalletDirectory(
                revAddress,
                signingKey,
                ((FetchedDirectory) root).getChildren(),
                deployDispatcher,
                false // skip deploy
            );

        } catch (NoDataByPath e) {
            // no previous mount: need to create a new root and deploy to the shard
            return new UnlockedWalletDirectory(
                revAddress,
                signingKey,
                new HashSet<>(),
                deployDispatcher,
                true); // do deploy
        }
    }

    public Path fetchDirectoryFromShard(F1r3flyBlockchainClient f1R3FlyBlockchainClient, String absolutePath, String name, BlockchainDirectory parent) throws NoDataByPath {
        try {
            List<RhoTypes.Par> pars = f1R3FlyBlockchainClient.findDataByName(absolutePath);

            RholangExpressionConstructor.ChannelData fileOrDir = RholangExpressionConstructor.parseChannelData(pars);

            if (fileOrDir.isDir()) {
                FetchedDirectory dir = new FetchedDirectory(name, parent);

                Set<Path> children = fileOrDir.children().stream().map((childName) -> {
                    try {
                        return fetchDirectoryFromShard(f1R3FlyBlockchainClient, absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, childName, dir);
                    } catch (NoDataByPath e) {
                        logger.error("Error fetching child directory from shard for path: {}",
                            absolutePath + PathUtils.getPathDelimiterBasedOnOS() + childName, e);
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toSet());

                dir.setChildren(children);
                return dir;

            } else {
                FetchedFile file = new FetchedFile(PathUtils.getFileName(absolutePath), parent);
                long offset = 0;
                offset = file.initFromBytes(fileOrDir.firstChunk(), offset);

                if (!fileOrDir.otherChunks().isEmpty()) {
                    Set<Integer> chunkNumbers = fileOrDir.otherChunks().keySet();
                    Integer[] sortedChunkNumbers = chunkNumbers.stream().sorted().toArray(Integer[]::new);

                    for (Integer chunkNumber : sortedChunkNumbers) {
                        String subChannel = fileOrDir.otherChunks().get(chunkNumber);
                        List<RhoTypes.Par> subChannelPars = f1R3FlyBlockchainClient.findDataByName(subChannel);
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


    // TODO: fix
//    @Override
//    public synchronized void addChild(Path p) throws OperationNotPermitted {
//        if (p instanceof TokenFile tokenFile) {
//            long amount = tokenFile.value;
//            String rholang = RholangExpressionConstructor.transfer(ConfigStorage.getRevAddress(), this.revAddress, amount);
//
//            deployDispatcher.enqueueDeploy(new DeployDispatcher.Deployment(
//                rholang, false, F1r3flyBlockchainClient.RHOLANG,
//                signingKey
//            ));
//        } else {
//            throw OperationNotPermitted.instance;
//        }
//    }
}