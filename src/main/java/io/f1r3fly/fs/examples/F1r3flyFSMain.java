package io.f1r3fly.fs.examples;

import java.util.concurrent.Callable;

import fr.acinq.secp256k1.Hex;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "f1r3FUSE", mixinStandardHelpOptions = true, version = "f1r3FUSE 1.0", description = "A FUSE filesystem based on the F1r3fly blockchain.")
class F1r3flyFSMain implements Callable<Integer> {

	@Option(names = { "-h",
			"--host" }, description = "Host of the F1r3fly blockchain internal gRPC API to connect to. Defaults to localhost.")
	private String host = "localhost";

	@Option(names = { "-p",
			"--port" }, description = "Port of the F1r3fly blockchain internal gRPC API to connect to. Defaults to 40402.")
	private int port = 40402;

	@Option(names = { "-sk",
			"--signing-key" }, description = "Private key, in hexadecimal, to sign Rholang deployments with.")
	private String signingKey;

	@Option(names = { "-rho",
			"--rholang" }, description = "Rholang expression to deploy.")
	private String rholangExpression;

	// @Option(names = { "-ck",
	// "--cipher-key-path" }, required = true, description = "Cipher key path. If
	// file not found, a new key will be generated.")
	// private String cipherKeyPath;

	// @Parameters(index = "0", description = "The path at which to mount the
	// filesystem.")
	// private Path mountPoint;

	private F1r3flyFS f1r3flyFS;

	@Override
	public Integer call() throws Exception {
		// AESCipher aesCipher = new AESCipher(cipherKeyPath);
		F1r3flyApi f1R3FlyApi = new F1r3flyApi(Hex.decode(signingKey), host, port);
		// f1r3flyFS = new F1r3flyFS(f1R3FlyApi, aesCipher);
		// try {
		// f1r3flyFS.mount(mountPoint, true);
		// } finally {
		// f1r3flyFS.umount();
		// }

		String rholangExpression = """
								// Expected output
//
// "REGISTRY_SIMPLE_INSERT_TEST: create arbitrary process X to store in the registry"
// Unforgeable(0xd3f4cbdcc634e7d6f8edb05689395fef7e190f68fe3a2712e2a9bbe21eb6dd10)
// "REGISTRY_SIMPLE_INSERT_TEST: adding X to the registry and getting back a new identifier"
// `rho:id:pnrunpy1yntnsi63hm9pmbg8m1h1h9spyn7zrbh1mcf6pcsdunxcci`
// "REGISTRY_SIMPLE_INSERT_TEST: got an identifier for X from the registry"
// "REGISTRY_SIMPLE_LOOKUP_TEST: looking up X in the registry using identifier"
// "REGISTRY_SIMPLE_LOOKUP_TEST: got X from the registry using identifier"
// Unforgeable(0xd3f4cbdcc634e7d6f8edb05689395fef7e190f68fe3a2712e2a9bbe21eb6dd10)

new simpleInsertTest, simpleInsertTestReturnID, simpleLookupTest,
    signedInsertTest, signedInsertTestReturnID, signedLookupTest, 
    ri(`rho:registry:insertArbitrary`), 
    rl(`rho:registry:lookup`),
    stdout(`rho:io:stdout`),
    stdoutAck(`rho:io:stdoutAck`), ack in { 
        simpleInsertTest!(*simpleInsertTestReturnID) |
        for(@idFromTest1 <- simpleInsertTestReturnID) {
            simpleLookupTest!(idFromTest1, *ack)
        } |

        contract simpleInsertTest(registryIdentifier) = {
            stdout!("REGISTRY_SIMPLE_INSERT_TEST: create arbitrary process X to store in the registry") |
            new X, Y, innerAck in {
                stdoutAck!(*X, *innerAck) |
                for(_ <- innerAck){
                    stdout!("REGISTRY_SIMPLE_INSERT_TEST: adding X to the registry and getting back a new identifier") |
                    ri!(*X, *Y) |
                    for(@uri <- Y) {
                        stdout!("@uri <- Y hit") |
                        stdout!("REGISTRY_SIMPLE_INSERT_TEST: got an identifier for X from the registry") |
                        stdout!(uri) |
                        registryIdentifier!(uri)
                    }
                }
            }
        } |

        contract simpleLookupTest(@uri, result) = {
            stdout!("uri= " ++ uri) |
            stdout!("REGISTRY_SIMPLE_LOOKUP_TEST: looking up X in the registry using identifier") |
            new lookupResponse in {
                rl!(uri, *lookupResponse) |
                for(@val <- lookupResponse) {
                    stdout!("ajdksfhasdkfhjkasdfhkjasdfdskjh") |
                    stdout!("REGISTRY_SIMPLE_LOOKUP_TEST: got X from the registry using identifier") |
                    stdoutAck!(val, *result)
                }
            }
        }
    }



									""";

		String newBlockHash = f1R3FlyApi.deploy(rholangExpression, true,
				F1r3flyApi.RHOLANG);
		System.out.println("\nnewBlockHash: " + newBlockHash);

		return 0;
	}

	// this example implements Callable, so parsing, error handling and handling
	// user
	// requests for usage help or version help can be done with one line of code.
	public static void main(String... args) {
		int exitCode = new CommandLine(new F1r3flyFSMain()).execute(args);
		System.exit(exitCode);
	}
}
