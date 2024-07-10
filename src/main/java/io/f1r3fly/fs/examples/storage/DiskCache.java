package io.f1r3fly.fs.examples.storage;

import org.slf4j.Logger;
import io.f1r3fly.fs.examples.storage.errors.CacheIOException;
import io.f1r3fly.fs.examples.storage.errors.F1r3flyDeployError;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.errors.PathIsNotAFile;
import io.f1r3fly.fs.utils.PathUtils;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentHashMap;

public class DiskCache {

    private Logger logger = LoggerFactory.getLogger(DiskCache.class.getName());

    record CacheEntry(RandomAccessFile raf, File file, boolean wasModified) {
    }

    private final ConcurrentHashMap<String, CacheEntry> links;

    public DiskCache() {
        this.links = new ConcurrentHashMap<>();
    }

    public long getSize(String path) {
        CacheEntry cached = this.links.get(path);

        if (cached == null) {
            return 0;
        }

        return cached.file.length();
    }

    /**
     * Check if the cache contains the specified path
     *
     * @param path the path to check
     * @return true if the cache contains the path, false otherwise
     */
    public Boolean wasModified(String path) {
        CacheEntry cached = this.links.get(path);

        if (cached == null) {
            return null;
        }

        return cached.wasModified;
    }

    public byte[] read(String path, long offset, long size) throws CacheIOException, NoDataByPath, PathIsNotAFile, F1r3flyDeployError {
        CacheEntry cached = links.get(path);
        if (cached == null) {

            return null;
        }

        // Read data from the cache
        byte[] data = new byte[(int) size];
        try {
            cached.raf.seek(offset);
            cached.raf.read(data);
        } catch (IOException e) {
            throw new CacheIOException(path, e);
        }

        return data;
    }

    /**
     * Write data to the cache at the specified offset
     *
     * @param path   the path of the file to write to
     * @param data   the data to write
     * @param offset the offset to write the data at
     * @throws CacheIOException if an error occurs while writing to the cache
     */
    public void write(String path, byte[] data, long offset, boolean wasModified) throws CacheIOException {
        try {
            CacheEntry cached = links.get(path);
            if (cached == null) {
                File cachedFile = createCachedFile(path);
                RandomAccessFile raf = null;
                raf = new RandomAccessFile(cachedFile, "rw");
                cached = new CacheEntry(raf, cachedFile, wasModified);
                links.put(path, cached);
            }

            // Add data to cache at the correct offset
            cached.raf.seek(offset);
            cached.raf.write(data);
        } catch (IOException e) {
            throw new CacheIOException(path, e);
        }
    }

    /**
     * Remove the cache file from the cache and close the file
     *
     * @param path the path of the file to remove
     * @return true if the file was removed, false otherwise
     */
    public boolean remove(String path) {
        CacheEntry cached = links.get(path);
        if (cached != null) {
            try {
                cached.raf.close();
            } catch (IOException e) {
                logger.warn("Failed to close cache file: " + cached.file.getAbsolutePath(), e);
            }

            boolean wasDeleted = cached.file.delete();

            links.remove(path);

            return wasDeleted;
        }

        return false;
    }


    private File createCachedFile(String realPath) throws CacheIOException {
        String cachedPath = realPath.replace(PathUtils.getPathDelimiterBasedOnOS(), "_");

        try {
            return File.createTempFile(cachedPath, ".cache");
        } catch (IOException e) {
            throw new CacheIOException(realPath, e);
        }
    }
}
