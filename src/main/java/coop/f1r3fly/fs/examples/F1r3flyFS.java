package coop.f1r3fly.fs.examples;

import coop.f1r3fly.fs.FuseStubFS;

import jnr.ffi.Pointer;

import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import coop.f1r3fly.fs.struct.FileStat;
import coop.f1r3fly.fs.struct.FuseFileInfo;
import coop.f1r3fly.fs.struct.Statvfs;

import coop.f1r3fly.fs.ErrorCodes;
import coop.f1r3fly.fs.FuseFillDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.List;

/**
 * @author Sergey Tselovalnikov
 * @see <a href="http://fuse.sourceforge.net/helloworld.html">helloworld</a>
 * @since 31.05.15
 */
public class F1r3flyFS extends FuseStubFS {
    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFS.class);

    private final F1r3flyDeployer deployer;

    public F1r3flyFS(F1r3flyDeployer deployer) {
        super();

        this.deployer = deployer;
    }


// TODO: Implement `@Override`n FS methods with `signDeploy()` and `deployService` calls

    @Override
    public int getattr(String path, FileStat stat) {
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        if (path.endsWith("onchain-volume.rho")) {
            //String rhoCode = "new helloworld, stdout(`rho:io:stdout`) in { contract helloworld( world ) = { for( @msg <- world ) { stdout!(msg) } } | new world, world2 in { helloworld!(*world) | world!(\"Hello World\") | helloworld!(*world2) | world2!(\"Hello World again\") } }";
            try {
                String rhoCode = loadStringResource(path);
//                    .trim() //
//                    .replaceAll("\\s+", " ");

                LOGGER.debug("Updated rhoCode \n{}", rhoCode);

                // Using deployer to deploy code on the RChain blockchain
                this.deployer.executeAndGet(rhoCode);

                return 0;
            } catch (Exception e) {
                e.printStackTrace();

                // Повертаємо помилку, якщо щось не так
                return -ErrorCodes.EIO();
            }
        }

        return -ErrorCodes.ENOENT();
    }


    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        //return -ErrorCodes.ENOSYS();
        List<String> segments = Arrays.asList(path.split("\\/"));
        int partsSize = segments.size();

        // "/mumble/frotz/fu/bar" -> [ "/mumble" "/frotz" "/fu" "/bar" ]
        String rhoPath = "[ " + segments.stream().map(element -> "\"/" + element + "\"").collect(Collectors.joining(" ")) + " ]";

        // "/mumble/frotz/fu/bar" -> [ "/mumble" "/frotz" "/fu" "/bar" ]
        // Java variables:
        // <path> = path from the client split as above
        // <uri> = URI generated from registry insert
        // <dirPath> = path minus the file name (end of the path)
        // <fileName> = the last element in the path
        // <parent> = the directory immediately above file
        String toDeploy = "makeNodeFromVolume!(<uri>, " + rhoPath + ", " + segments.subList(0, partsSize - 2) + segments.subList(partsSize - 1, partsSize - 1) + ", \"\")";

        this.deployer.executeAndGet(toDeploy);

        return 0;
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int rename(String path, String newName) {
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int rmdir(String path) {
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int truncate(String path, long offset) {
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int unlink(String path) {
        return -ErrorCodes.ENOENT();
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        return -ErrorCodes.ENOENT();
    }


    public String loadStringResource(String path) throws IOException {
        byte[] bytes;

        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);

        try {
            bytes = stream.readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);

            // Логування змісту файлу
            LOGGER.debug("Loaded Rholang code: \n{}", content);

            return content;
        } finally {
            stream.close();
        }
    }

    @Override
    public void mount(Path mountPoint, boolean blocking, boolean debug, String[] fuseOpts) {
//        super.mount(mountPoint, blocking, debug, fuseOpts);
//        System.out.println("mount did nothing");

        try {
            this.deployer.executeAndGet(loadStringResource("onchain-volume.rho"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void umount() {
//        super.umount();
        System.out.println("umount did nothing");
    }
}
