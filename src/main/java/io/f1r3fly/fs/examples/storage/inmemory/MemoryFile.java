package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.examples.Config;
import io.f1r3fly.fs.examples.datatransformer.AESCipher;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.grcp.listener.NotificationConstructor;
import io.f1r3fly.fs.examples.storage.grcp.listener.NotificationConstructor.NotificationReasons;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import rhoapi.RhoTypes;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryFile extends MemoryPath {

    private final Logger log = org.slf4j.LoggerFactory.getLogger(MemoryFile.class);

    // it should be a number that can be divisible by 16 because of AES block size
    private static final int MAX_FILE_CHUNK_SIZE = 16 * 10 * 1024 * 1024; // 160 mb

    protected RandomAccessFile rif;
    protected File cachedFile;
    protected long lastDeploymentOffset = 0;
    protected boolean isDirty = true;
    // cached file size; avoid IO operations at getattr
    protected long size = 0;

    // Illegal Filename Characters.
    // In theory, it can't be used in filename, so it's safe to use it as a delimiter
    private static String delimiter = "/";

    private boolean isOtherChunksDeployed = false;
    private Map<Integer, String> otherChunks = new ConcurrentHashMap<>();

    public MemoryFile(Config config, String name, MemoryDirectory parent, boolean sendToShard) {
        super(config, name, parent);
        if (sendToShard) {
            enqueueCreatingFile();
            triggerNotificationWithReason(NotificationReasons.FILE_CREATED);
        }
        try {
            cachedFile = File.createTempFile(name, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enqueueCreatingFile() {
        String rholang = RholangExpressionConstructor.sendEmptyFileIntoNewChanel(getChannelName());
        enqueueMutation(rholang);
    }

    @Override
    public void getattr(FileStat stat, FuseContext fuseContext) {
        stat.st_mode.set(FileStat.S_IFREG | 0777);
        stat.st_size.set(getSize());
        stat.st_uid.set(fuseContext.uid.get());
        stat.st_gid.set(fuseContext.gid.get());
    }

    public int read(Pointer buffer, long size, long offset) throws IOException {
        open(); // make sure file is open

        int bytesToRead = (int) Math.min(getSize() - offset, size);
        byte[] chunk = new byte[bytesToRead];

        synchronized (this) {
            rif.seek(offset);
            rif.read(chunk);
        }

        buffer.put(0, chunk, 0, bytesToRead);
        return bytesToRead;
    }

    public synchronized void truncate(long offset, boolean sendToShard) throws IOException {
        if (offset != 0) {
            throw new RuntimeException("Unsupported");
        }

        if (getSize() >= 0) {
            size = 0; // size changed, reset it
        }

        close();

        isDirty = true;

        lastDeploymentOffset = 0;

        cachedFile.delete();
        cachedFile = Files.createTempFile(name, null).toFile();

        otherChunks = new ConcurrentHashMap<>();
        isOtherChunksDeployed = false;

        if (sendToShard) {
            enqueueMutation(RholangExpressionConstructor.forgetChanel(getChannelName()));
            otherChunks.forEach((chunkNumber, subChannel) -> {
                enqueueMutation(RholangExpressionConstructor.forgetChanel(subChannel));
            });
            enqueueCreatingFile();
            triggerNotificationWithReason(NotificationReasons.TRUNCATED);
        }



        open();
    }

    public int write(Pointer buffer, long bufSize, long writeOffset) throws IOException {
        log.trace("Writing to file {} at offset {}", cachedFile.getAbsolutePath(), writeOffset);

        open(); // make sure file is open

        if (getSize() >= 0) {
            size = -1; // size changed, reset it
        }

        isDirty = true;

        byte[] bytesToWrite = new byte[(int) bufSize];
        buffer.get(0, bytesToWrite, 0, (int) bufSize);
        synchronized (this) {
            rif.seek(writeOffset);
            rif.write(bytesToWrite, 0, (int) bufSize);
        }

        int writeEnd = (int) (writeOffset + bufSize);
        int notDeployedChunkSize = (int) (writeEnd - lastDeploymentOffset);
        if (notDeployedChunkSize >= MAX_FILE_CHUNK_SIZE) {
            deployChunk();
        }

        return (int) bufSize;
    }

    private void deployChunk() throws IOException {
        open(); // make sure file is open

        int size = (int) Math.min(getSize() - lastDeploymentOffset, MAX_FILE_CHUNK_SIZE);

        byte[] bytes = new byte[size];
        synchronized (this) {
            rif.seek(lastDeploymentOffset);
            rif.read(bytes, 0, size);
        }

        if (PathUtils.isEncryptedExtension(name)) {
            bytes = AESCipher.getInstance().encrypt(bytes);
        }

        int chunkNumber = (int) (lastDeploymentOffset / MAX_FILE_CHUNK_SIZE);
        String rholang;
        if (chunkNumber == 0) {
            rholang = RholangExpressionConstructor.updateFileContent(getChannelName(), bytes, getSize());
        } else {
            String subChannel = getChannelName() + delimiter + chunkNumber;
            rholang = RholangExpressionConstructor.sendFileContentChunk(subChannel, bytes);
            otherChunks.put(chunkNumber, subChannel);
            isOtherChunksDeployed = false;
        }
        enqueueMutation(rholang);

        lastDeploymentOffset = lastDeploymentOffset + size;

        if (lastDeploymentOffset == getSize()) {
           // deployed a last part of the file
            triggerNotificationWithReason(NotificationReasons.FILE_WROTE);
        }

    }

    public void open() throws IOException {
        try {
            if (rif == null) {
                rif = createRIF();
            }
        } catch (FileNotFoundException e) {
            // TODO: if file not found, re-pull it from Node?
            cachedFile = File.createTempFile(name, null);
            rif = createRIF();
        }
    }

    private @NotNull RandomAccessFile createRIF() throws FileNotFoundException {
        return new RandomAccessFile(cachedFile, "rw");
    }

    public void close() {
        try {
            // append a last part of the file if any
            if (lastDeploymentOffset < getSize()) {
                deployChunk();
            }

            if (!isOtherChunksDeployed) {
                if (!otherChunks.isEmpty()) {
                    enqueueMutation(RholangExpressionConstructor.updateOtherChunksMap(getChannelName(), otherChunks));
                }
                isOtherChunksDeployed = true;
            }

            if (rif != null) {
                rif.close();
                rif = null;
            }
        } catch (IOException e) {
            // Ignore
            e.printStackTrace();
        }
    }

    long getSize() {
        if (size <= 0) {
            size = cachedFile.length();
        }
        return size;
    }

    public int initFromBytes(byte[] bytes, long offset) throws IOException {
        open(); // make sure file is open

        if (PathUtils.isEncryptedExtension(name)) {
            bytes = AESCipher.getInstance().decrypt(bytes);
        }

        synchronized (this) {
            rif.seek(offset);
            rif.write(bytes);
        }

        lastDeploymentOffset = offset + bytes.length;

        return bytes.length;
    }

    public void initSubChannels(Map<Integer, String> subChannels) {
        this.otherChunks = subChannels;
        this.isOtherChunksDeployed = true;
    }

    public void onChange() {
        if (isDirty) {
            if (isDeployable()) {
                try {
                    String rholangExpression = Files.readString(cachedFile.toPath());
                    enqueueMutation(rholangExpression); // deploy a file as rho expression
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            isDirty = false;
        }
    }

    @Override
    public void rename(String newName, MemoryDirectory newParent, boolean renameOnShard, boolean updateParentOnShard) throws IOException {

        isDirty = true;

        boolean wasEncrypted = PathUtils.isEncryptedExtension(name);
        boolean willBeEncrypted = PathUtils.isEncryptedExtension(newName);

        // wasn't encrypted and now will be encrypted
        boolean needEncrypt = !wasEncrypted && willBeEncrypted;

        // was encrypted and now won't be encrypted
        boolean needDecrypt = wasEncrypted && !willBeEncrypted;

        if (needEncrypt || needDecrypt) {
            enqueueMutation(RholangExpressionConstructor.forgetChanel(getChannelName())); // delete old
            String oldPath = getAbsolutePath();
            super.rename(newName, newParent, false, true); // skip event, channel wil be re-created the below
            redeployFileIntoChanel();
            String newPath = getAbsolutePath();
            triggerRenameNotification(oldPath, newPath); // notify subscribers separately b/c `super.rename` doesn't trigger it
        } else {

            super.rename(newName, newParent, renameOnShard, updateParentOnShard); // just rename the rholang chanel
        }

    }

    private void redeployFileIntoChanel() throws IOException {
        enqueueCreatingFile(); // create new

        open(); // make sure file is open
        // append
        lastDeploymentOffset = 0;
        while (lastDeploymentOffset < getSize()) {
            try {
                deployChunk();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        isDirty = false;
        close(); // close file
    }

    private boolean isDeployable() {
        return PathUtils.isDeployableFile(name);
    }

    public void fetchContent(byte[] firstChunk, Map<Integer, String> otherChunks) throws NoDataByPath, IOException {
        long offset = 0;
        offset = initFromBytes(firstChunk, offset);

        if (!otherChunks.isEmpty()) {
            Set<Integer> chunkNumbers = otherChunks.keySet();
            Integer[] sortedChunkNumbers = chunkNumbers.stream().sorted().toArray(Integer[]::new);

            for (Integer chunkNumber : sortedChunkNumbers) {
                String subChannel = otherChunks.get(chunkNumber);
                List<RhoTypes.Par> subChannelPars = config.f1r3flyAPI.findDataByName(subChannel);
                byte[] data = RholangExpressionConstructor.parseBytes(subChannelPars);

                offset = offset + initFromBytes(data, offset);
            }
        }

        initSubChannels(otherChunks);
    }

    @Override
    public synchronized void delete(boolean sendToShard) {
        super.delete(sendToShard);
        cachedFile.delete();
    }
}
