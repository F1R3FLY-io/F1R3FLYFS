package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.examples.datatransformer.AESCipher;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import io.f1r3fly.fs.utils.PathUtils;
import jnr.ffi.Pointer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;

public class MemoryFile extends MemoryPath {

    private final Logger log = org.slf4j.LoggerFactory.getLogger(MemoryFile.class);

    // it should be a number that can be divisible by 16 because of AES block size
    public static final int MAX_FILE_CHUNK_SIZE = 16 * 1024 * 1024; // 16MB
    protected static int ENCRYPTING_CHUNK_SIZE = 16 * 1024; // 16KB

    protected RandomAccessFile rif;
    protected File cachedFile;
    protected int lastDeploymentOffset = 0;
    protected boolean isDirty = true;
    // cached file size; avoid IO operations at getattr
    protected int size = -1;

    public MemoryFile(String prefix, String name, MemoryDirectory parent, DeployDispatcher deployDispatcher, boolean sendToShard) {
        super(prefix, name, parent, deployDispatcher);
        if (sendToShard) {
            enqueueCreatingFile();
        }
        try {
            cachedFile = File.createTempFile(name, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void enqueueCreatingFile() {
        String rholang = RholangExpressionConstructor.sendFileIntoNewChanel(getAbsolutePath(), 0, new byte[0]);
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
        int bytesToRead = (int) Math.min(this.size - offset, size);
        byte[] chunk = new byte[bytesToRead];

        synchronized (this) {
            rif.seek(offset);
            rif.read(chunk);
        }

        buffer.put(0, chunk, 0, bytesToRead);
        return bytesToRead;
    }

    public synchronized void truncate(long offset) throws IOException {
//        if (size < cachedFile.length()) {
//            // Need to create a new, smaller buffer
//            ByteBuffer newContents = ByteBuffer.allocate((int) size);
//            byte[] bytesRead = new byte[(int) size];
//            contents.get(bytesRead);
//            newContents.put(bytesRead);
//            contents = newContents;
//        }
        // unsupported
//        throw new RuntimeException("Unsupported");
        if (offset != 0) {
            throw new RuntimeException("Unsupported");
        }

        if (size >= 0) {
            size = 0; // size changed, reset it
        }

        close();

        isDirty = true;

        lastDeploymentOffset = 0;

        cachedFile.delete();
        cachedFile = Files.createTempFile(name, null).toFile();

        enqueueMutation(RholangExpressionConstructor.forgetChanel(getAbsolutePath()));
        enqueueCreatingFile();

        open();
    }

    public int write(Pointer buffer, long bufSize, long writeOffset) throws IOException {
        log.trace("Writing to file {} at offset {}", cachedFile.getAbsolutePath(), writeOffset);

        open(); // make sure file is open

        if (size >= 0) {
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
        int notDeployedChunkSize = writeEnd - lastDeploymentOffset;
        if (notDeployedChunkSize >= MAX_FILE_CHUNK_SIZE) {
            deployChunk();
        }

        return (int) bufSize;
    }

    private void deployChunk() throws IOException {
        open(); // make sure file is open

        int size = Math.min(getSize() - lastDeploymentOffset, MAX_FILE_CHUNK_SIZE);

        byte[] bytes = new byte[size];
        synchronized (this) {
            rif.seek(lastDeploymentOffset);
            rif.read(bytes, 0, size);
        }

        if (PathUtils.isEncryptedExtension(name)) {
            bytes = AESCipher.getInstance().encrypt(bytes);
        }

        String rholang = RholangExpressionConstructor.appendValue(getAbsolutePath(), bytes, bytes.length);
        enqueueMutation(rholang);

        lastDeploymentOffset = lastDeploymentOffset + size;
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

            if (rif != null) {
                rif.close();
                rif = null;
            }
        } catch (IOException e) {
            // Ignore
            e.printStackTrace();
        }
    }

    int getSize() {
        if (size < 0) {
            size = (int) cachedFile.length();
        }
        return size;
    }

    public void initFromBytes(byte[] bytes) {
        // write bytes to file
        try (FileOutputStream fos = new FileOutputStream(cachedFile)) {

            if (PathUtils.isEncryptedExtension(name)) {
                bytes = AESCipher.getInstance().decrypt(bytes);
            }

            fos.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        size = bytes.length;
        lastDeploymentOffset = size;
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
    public void rename(String newName, MemoryDirectory newParent, boolean sendToShard) throws IOException {

        isDirty = true;

        boolean wasEncrypted = PathUtils.isEncryptedExtension(name);
        boolean willBeEncrypted = PathUtils.isEncryptedExtension(newName);

        // wasn't encrypted and now will be encrypted
        boolean needEncrypt = !wasEncrypted && willBeEncrypted;

        // was encrypted and now won't be encrypted
        boolean needDecrypt = wasEncrypted && !willBeEncrypted;

        if (needEncrypt || needDecrypt) {
            enqueueMutation(RholangExpressionConstructor.forgetChanel(getAbsolutePath())); // delete old
            super.rename(newName, newParent, false); // skip event, chanel wil be re-created the below
            redeployFileIntoChanel();
        } else {

            super.rename(newName, newParent, sendToShard); // just rename the rholang chanel
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
}
