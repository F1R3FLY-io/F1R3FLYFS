package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.examples.datatransformer.AESCipher;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import fr.acinq.secp256k1.Hex;

@Command(name = "f1r3FUSE", mixinStandardHelpOptions = true, version = "f1r3FUSE 1.0",
    description = "A FUSE filesystem based on the F1r3fly blockchain.")
class F1r3flyFSMain implements Callable<Integer> {

    @Option(names = {"-h", "--host"}, description = "Host of the F1r3fly blockchain internal gRPC API to connect to. Defaults to localhost.")
    private String host = "localhost";

    @Option(names = {"-p", "--port"}, description = "Port of the F1r3fly blockchain internal gRPC API to connect to. Defaults to 40402.")
    private int port = 40402;

    @Option(names = {"-sk", "--signing-key"}, description = "Private key, in hexadecimal, to sign Rholang deployments with.")
    private String signingKey;

    @Option(names = {"-ck", "--cipher-key-path"}, required = true, description = "Cipher key path. If file not found, a new key will be generated.")
    private String cipherKeyPath;

    @Option(names = {"-mn", "--mount-name"}, description = "Mount name of the previous mounted filesystem. If specified, the filesystem will be restored from F1r3fly.")
    private String mountName;

    @Parameters(index = "0", description = "The path at which to mount the filesystem.")
    private Path mountPoint;

    private F1r3flyFS f1r3flyFS;

    @Override
    public Integer call() throws Exception {

        AESCipher.init(cipherKeyPath); // init singleton instance

        F1r3flyApi f1R3FlyApi = new F1r3flyApi(Hex.decode(signingKey), host, port);
        f1r3flyFS = new F1r3flyFS(f1R3FlyApi);
        try {
            if (mountName != null) {
                f1r3flyFS.remount(mountName, mountPoint, true, false, new String[]{});
            } else {
                f1r3flyFS.mount(mountPoint, true);
            }
        } finally {
            f1r3flyFS.umount();
        }
        return 0;
    }

    // this example implements Callable, so parsing, error handling and handling user
    // requests for usage help or version help can be done with one line of code.
    public static void main(String... args) {
        int exitCode = new CommandLine(new F1r3flyFSMain()).execute(args);
        System.exit(exitCode);
    }
}
