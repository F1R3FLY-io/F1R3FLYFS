package coop.f1r3fly.fs.examples;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import fr.acinq.secp256k1.Hex;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import casper.v1.DeployServiceGrpc;
import casper.v1.ProposeServiceGrpc;
import casper.v1.DeployServiceGrpc.DeployServiceFutureStub;
import casper.v1.ProposeServiceGrpc.ProposeServiceFutureStub;

@Command(name = "f1r3FUSE", mixinStandardHelpOptions = true, version = "f1r3FUSE 1.0",
         description = "A FUSE filesystem based on the F1r3fly blockchain.")
class F1r3flyFSMain implements Callable<Integer> {

    @Option(names = {"-h", "--host"}, description = "Host of the F1r3fly blockchain internal gRPC API to connect to. Defaults to localhost.")
    private String host = "localhost";

    @Option(names = {"-p", "--port"}, description = "Port of the F1r3fly blockchain internal gRPC API to connect to. Defaults to 40402.")
    private int    port = 40402;

    @Option(names = {"-k", "--signing-key"}, description = "'Private key, in hexadecimal, to sign Rholang deployments with.")
    private String signingKey;

    @Parameters(index = "0", description = "The path at which to mount the filesystem.")
    private Path mountPoint;

    private F1r3flyFS f1r3flyFS;

    @Override
    public Integer call() throws Exception { // your business logic goes here...
                               F1r3flyDeployer deployer = new F1r3flyDeployer(Hex.decode(signingKey), host, port);
                               f1r3flyFS       = new F1r3flyFS(deployer);
                               f1r3flyFS.mkdir("abc", 0);
                               return 0;
    }

    // this example implements Callable, so parsing, error handling and handling user
    // requests for usage help or version help can be done with one line of code.
    public static void main(String... args) {
        int exitCode = new CommandLine(new F1r3flyFSMain()).execute(args);
        System.exit(exitCode);
    }
}
