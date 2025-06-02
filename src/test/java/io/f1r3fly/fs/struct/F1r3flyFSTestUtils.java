package io.f1r3fly.fs.struct;

import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import org.jetbrains.annotations.NotNull;
import rhoapi.RhoTypes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class F1r3flyFSTestUtils extends F1r3flyFSTestFixture {

    public static @NotNull byte[] getFileContentFromShardDirectly(File file) {
        try {
            RholangExpressionConstructor.ChannelData dirOrFile = getChanelData(file);

            assertTrue(dirOrFile.isFile(), "Chanel data should be a file");
            assertNotNull(dirOrFile.firstChunk(), "Chanel data should contain firstChunk field");
            assertNotNull(dirOrFile.otherChunks(), "Chanel data should contain otherChunks field");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            outputStream.write(dirOrFile.firstChunk());

            Integer[] sortedKeys = dirOrFile.otherChunks().keySet().stream().sorted().toArray(Integer[]::new);
            for (Integer key : sortedKeys) {
                String subChannel = dirOrFile.otherChunks().get(key);
                List<RhoTypes.Par> data = f1R3FlyApi.findDataByName(subChannel);
                byte[] chunk = RholangExpressionConstructor.parseBytes(data);
                outputStream.write(chunk);
            }

            return outputStream.toByteArray();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static @NotNull Set<String> getFolderChildrenFromShardDirectly(File file) {
        RholangExpressionConstructor.ChannelData dirOrFile = getChanelData(file);

        assertTrue(dirOrFile.isDir(), "Chanel data should be a directory");

        return dirOrFile.children();
    }

    public static @NotNull RholangExpressionConstructor.ChannelData getChanelData(File file) {
        // reading data from shard directly:
        // 1. Get a data from the file. File is a chanel at specific block
        // Reducing the path. Fuse changes the path, so we need to change it too:
        // - the REAL path   is /tmp/f1r3flyfs/test.txt
        // - the FUSE's path is /test.txt
        File fusePath = new File(file.getAbsolutePath().replace(MOUNT_POINT_FILE.getAbsolutePath(), "")); // /tmp/f1r3flyfs/test.txt -> /test.txt

        String fileNameAtShard = fusePath.getPath();

        // wait on background deployments
        waitOnBackgroundDeployments();

        List<RhoTypes.Par> fileData = null;
        try {
            fileData = f1R3FlyApi.findDataByName(fileNameAtShard);
        } catch (NoDataByPath e) {
            throw new RuntimeException(e);
        }

        assertFalse(fileData.isEmpty(), "Chanel data %s should be not empty".formatted(fileNameAtShard));

        // 2. Parse Chanel value as a map
        return RholangExpressionConstructor.parseChannelData(fileData);
    }

    public static void assertWrittenData(File file, byte[] inputDataAsBinary, boolean assertDataFromShard, String message) throws IOException {
        byte[] readDataAsBinary = Files.readAllBytes(file.toPath());
        assertArrayEquals(inputDataAsBinary, readDataAsBinary, message);
        if (assertDataFromShard) {
            assertFileContentFromShard(inputDataAsBinary, file);
        }
    }

    public static void assertFileContentFromShard(byte[] expectedData, File file) {
        byte[] readData = getFileContentFromShardDirectly(file);
        assertArrayEquals(expectedData, readData, "Read data should be equal to written data");
    }

    public static void assertContainChilds(File dir, File... expectedChilds) {
        File[] childs = dir.listFiles();

        File[] localFiles;

        if (dir.equals(MOUNT_POINT_FILE)) {
            File tokensDirectory = new File(MOUNT_POINT_FILE, ".tokens");
            // expected + tokensDirectory
            localFiles = Stream.concat(Arrays.stream(expectedChilds), Stream.of(tokensDirectory)).toArray(File[]::new);
        } else {
            localFiles = expectedChilds;
        }


        assertNotNull(childs, "Can't get list of files in %s".formatted(dir.getAbsolutePath()));
        assertEquals(localFiles.length, childs.length, "Should be only %s file(s) but found %s in %s".formatted(Arrays.toString(localFiles), Arrays.toString(childs), dir.getAbsolutePath()));

        for (File expectedChild : localFiles) {
            assertTrue(
                Arrays.stream(childs).anyMatch(file -> file.getName().equals(expectedChild.getName())),
                "Expected file %s not found in list of childs (%s)".formatted(expectedChild, Arrays.toString(childs))
            );
        }

        // and check the deployed data:

        Set<String> children = getFolderChildrenFromShardDirectly(dir);
        assertEquals(expectedChilds.length, children.size(), "Should be only %s file(s) in %s".formatted(expectedChilds.length, dir.getAbsolutePath()));

        for (File expectedChild : expectedChilds) {
            assertTrue(
                children.contains(expectedChild.getName()),
                "Expected file %s not found in list of childs (%s)".formatted(expectedChild, children)
            );
        }
    }

    public static void assertDirIsEmpty(File dir) {
        File[] files = dir.listFiles();
        assertNotNull(files, "Dir %s listFiles returned null".formatted(dir));
        assertEquals(0, files.length, "Dir %s should be empty, but %s got".formatted(dir, Arrays.toString(files)));

        Set<String> folderChildrenFromShardDirectly = getFolderChildrenFromShardDirectly(dir);
        assertEquals(0, folderChildrenFromShardDirectly.size(), "Dir %s should be empty, but %s got".formatted(dir, folderChildrenFromShardDirectly));
    }

    public static void testIsNotEncrypted(File notEncrypted, String fileContent) throws IOException {
        String readData2 = Files.readString(notEncrypted.toPath());
        assertEquals(fileContent, readData2, "Read data should be equal to written data");

        String decodedFileData2 = new String(getFileContentFromShardDirectly(notEncrypted));
        assertEquals(fileContent, decodedFileData2, "Decoded data should be equal to the original data");
    }

    public static void testIsEncrypted(File encrypted, String expectedFileData) throws IOException, NoDataByPath {
        String readData = Files.readString(encrypted.toPath());
        assertEquals(expectedFileData, readData, "Read data should be equal to written data");

        String decodedFileData = new String(getFileContentFromShardDirectly(encrypted));
        // Actual data is encrypted. It should be different from the original data
        assertNotEquals(expectedFileData, decodedFileData, "Decoded data should be different from the original data");
    }

    public static void testToRenameTxtToDeployableExtension(String newExtension) throws IOException, NoDataByPath {
        File file = new File(MOUNT_POINT_FILE, "test.text");

        String newRhoChanel = "public";
        int chanelValue = new Random().nextInt();
        String rhoCode = """
            @"%s"!(%s)
            """.formatted(newRhoChanel, chanelValue);

        Files.writeString(file.toPath(), rhoCode, StandardCharsets.UTF_8);

        assertTrue(file.renameTo(new File(file.getParent(), "test." + newExtension)), "Failed to rename file");

        waitOnBackgroundDeployments();

        List<RhoTypes.Par> pars = f1R3FlyApi.findDataByName(newRhoChanel);
        RhoTypes.Par first = pars.get(0);
        assertEquals(first.getExprsList().get(0).getGInt(), chanelValue, "Deployed data should be equal to written data");
    }

    public static void testToCreateDeployableFile(String extension) throws IOException, NoDataByPath {
        File file = new File(MOUNT_POINT_FILE, "test." + extension);

        // For .metta or .rho extension
        String newRhoChanel = "public";
        int chanelValue = new Random().nextInt();
        String rhoCode = """
            @"%s"!(%s)
            """.formatted(newRhoChanel, chanelValue);

        Files.writeString(file.toPath(), rhoCode, StandardCharsets.UTF_8);

        waitOnBackgroundDeployments();
        List<RhoTypes.Par> pars = f1R3FlyApi.findDataByName(newRhoChanel);
        RhoTypes.Par first = pars.get(0);
        assertEquals(first.getExprsList().get(0).getGInt(), chanelValue, "Deployed data should be equal to written data");
    }
} 