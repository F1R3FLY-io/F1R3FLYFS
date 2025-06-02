package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.SuccessCodes;
import io.f1r3fly.fs.examples.datatransformer.AESCipher;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.F1r3flyDeployError;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import fr.acinq.secp256k1.Hex;
import rhoapi.RhoTypes;

@Command(name = "f1r3FUSE", mixinStandardHelpOptions = true, version = "f1r3FUSE 1.0",
    description = "A FUSE filesystem based on the F1r3fly blockchain.")
class F1r3flyFSMain implements Callable<Integer> {

    @Option(names = {"-h", "--validator-host"}, description = "Host of the F1r3fly blockchain internal gRPC API to connect to. Defaults to localhost.")
    private String validatorHost = "localhost";

    @Option(names = {"-p", "--validator-port"}, description = "Port of the F1r3fly blockchain internal gRPC API to connect to. Defaults to 40402.")
    private int validatorPort = 40402;

    @Option(names = {"-oh", "--observer-host"}, description = "Host of the F1r3fly blockchain observer gRPC API to connect to. Defaults to localhost.")
    private String observerHost = "localhost";

    @Option(names = {"-op", "--observer-port"}, description = "Port of the F1r3fly blockchain observer gRPC API to connect to. Defaults to 40403.")
    private int observerPort = 40403;

    @Option(names = {"-ck", "--cipher-key-path"}, required = true, description = "Cipher key path. If file not found, a new key will be generated.")
    private String cipherKeyPath;

    @Parameters(index = "0", description = "The path at which to mount the filesystem.")
    private Path mountPoint;

    private F1r3flyFS f1r3flyFS;


    @Override
    public Integer call() throws Exception {
        AESCipher.init(cipherKeyPath); // init singleton instance

        F1r3flyApi f1r3flyApi = new F1r3flyApi(
            validatorHost,
            validatorPort,
            observerHost,
            observerPort
        );

        f1r3flyFS = new F1r3flyFS(
            f1r3flyApi
        );

        try {
            f1r3flyFS.mount(mountPoint, true);
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
