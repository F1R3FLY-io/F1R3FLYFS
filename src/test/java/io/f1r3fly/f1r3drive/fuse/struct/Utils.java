package io.f1r3fly.f1r3drive.fuse.struct;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

public class Utils {

    @SafeVarargs
    public static <TKey, TValue> HashMap<TKey, TValue> asMap(Pair<? extends TKey, ? extends TValue>... pairs) {
        HashMap<TKey, TValue> map = new HashMap<>();
        for (Pair<? extends TKey, ? extends TValue> pair : pairs) {
            map.put(pair.left, pair.right);
        }
        return map;
    }

    public static class Pair<L, R> {
        final L left;
        final R right;

        public Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }

        public static <L, R> Pair<L, R> pair(L left, R right) {
            return new Pair<>(left, right);
        }
    }

    public static void cleanDataDirectory(String destination, List<String> excludeList) {
        try {
            // if test fails, try to cleanup the data folder of the node manually
            // cd data && rm -rf blockstorage dagstorage eval rspace casperbuffer deploystorage rnode.log && cd
            cleanDirectoryExcept(destination, excludeList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void cleanDirectoryExcept(String directoryPath, List<String> excludeList) throws IOException {
        File directory = new File(directoryPath);
        Path dirPath = directory.toPath();
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                Path filePath = file.toPath();
                String relativePath = dirPath.relativize(filePath).toString();

                if (!excludeList.contains(relativePath)) {
                    if (file.isDirectory()) {
                        FileUtils.deleteDirectory(file);
                    } else {
                        Files.deleteIfExists(file.toPath());
                    }
                }
            }
        }
    }
}
