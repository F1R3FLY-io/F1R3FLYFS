package coop.f1r3fly.fs.examples;

import jnr.ffi.Platform;
import jnr.ffi.Pointer;

import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import coop.f1r3fly.fs.struct.FileStat;
import coop.f1r3fly.fs.struct.FuseFileInfo;
import coop.f1r3fly.fs.struct.Statvfs;

import coop.f1r3fly.fs.ErrorCodes;
import coop.f1r3fly.fs.FuseFillDir;
import coop.f1r3fly.fs.FuseStubFS;

import com.google.protobuf.ProtocolStringList;

import casper.CasperMessage.DeployDataProto;
import casper.DeployServiceCommon.FindDeployQuery;
import casper.DeployServiceCommon.IsFinalizedQuery;
import casper.ProposeServiceCommon.ProposeQuery;
import casper.v1.DeployServiceGrpc.DeployServiceFutureStub;
import casper.v1.ProposeServiceGrpc.ProposeServiceFutureStub;
import servicemodelapi.ServiceErrorOuterClass.ServiceError;

import fr.acinq.secp256k1.Secp256k1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Objects;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.List;

import com.rfksystems.blake2b.Blake2b;
import com.rfksystems.blake2b.security.Blake2bProvider;

import com.google.protobuf.ByteString;

import io.smallrye.mutiny.Uni;

/**
 * @author Sergey Tselovalnikov
 * @see <a href="http://fuse.sourceforge.net/helloworld.html">helloworld</a>
 * @since 31.05.15
 */
public class F1r3flyFS extends FuseStubFS {
    private byte[]                   signingKey;
    private DeployServiceFutureStub  deployService;
    private ProposeServiceFutureStub proposeService;

    // TODO: Add private `String` variables for Rholang code here

    public static final String HELLO_PATH = "/hello";
    public static final String HELLO_STR = "Hello World!";
    private static final Duration INIT_DELAY          = Duration.ofMillis(100);
    private static final Duration MAX_DELAY           = Duration.ofSeconds(5);
    private static final int      RETRIES             = 10;

    private F1r3flyFS() {}                // Disable nullary constructor

    public F1r3flyFS(
        byte[]                   signingKey,
        DeployServiceFutureStub  deployService,
        ProposeServiceFutureStub proposeService,
        String                   onChainVolumeCode
    ) throws IOException, NoSuchAlgorithmException {
        super();

        Security.addProvider(new Blake2bProvider());
        this.signingKey     = signingKey;
        this.deployService  = deployService;
        this.proposeService = proposeService;

      	// Initialize private `String` variables with `loadStringResource()` here
      	String rhoCode = loadStringResource( onChainVolumeCode );
      	// Make deployment
      	DeployDataProto deployment = DeployDataProto.newBuilder()
              .setTerm(rhoCode)
              .setTimestamp(0)
              .setPhloPrice(1)
              .setPhloLimit(1000000)
              .setShardId("root")
              .build();
      
      	// Sign deployment
      	DeployDataProto signed = signDeploy(deployment);
      
      	// Deploy
      	Uni<Void> deployVolumeContract =
        Uni.createFrom().future(deployService.doDeploy(signed))
        .flatMap(deployResponse -> {
            if (deployResponse.hasError()) {
                return this.<String>fail(deployResponse.getError());
            } else {
                return succeed(deployResponse.getResult());
            }
        })
        
        .flatMap(deployResult -> {
            String      deployId   = deployResult.substring(deployResult.indexOf("DeployId is: ") + 13, deployResult.length());
            return Uni.createFrom().future(proposeService.propose(ProposeQuery.newBuilder().setIsAsync(false).build()))
            .flatMap(proposeResponse -> {
                if (proposeResponse.hasError()) {
                    return this.<String>fail(proposeResponse.getError());
                } else {
                    return succeed(deployId);
                }
            });
        })
        .flatMap(deployId -> {
            ByteString  b64        = ByteString.copyFrom(decodeHex(deployId.toCharArray()));
            return Uni.createFrom().future(deployService.findDeploy(FindDeployQuery.newBuilder().setDeployId(b64).build()))
            .flatMap(findResponse -> {
                if (findResponse.hasError()) {
                    return this.<String>fail(findResponse.getError());
                } else {
                    return succeed(findResponse.getBlockInfo().getBlockHash());
                }
            });
        })
        .flatMap(blockHash -> {
            return Uni.createFrom().future(deployService.isFinalized(IsFinalizedQuery.newBuilder().setHash(blockHash).build()))
            .flatMap(isFinalizedResponse -> {
                if (isFinalizedResponse.hasError()) {
                    return fail(isFinalizedResponse.getError());
                } else {
                    return Uni.createFrom().voidItem();
                }
            })
            .onFailure().retry()
            .withBackOff(INIT_DELAY, MAX_DELAY)
            .atMost(RETRIES);
        });

        // Drummer Hoff Fired It Off
        deployVolumeContract.await().indefinitely();
    }

    // Cut down on verbosity of surfacing successes
    <T> Uni<T> succeed(T t) {
        return Uni.createFrom().item(t);
    }

    // Cut down on verbosity of surfacing errors
    <T> Uni<T> fail(ServiceError error) {
        return Uni.createFrom().failure(new RuntimeException(gatherErrors(error)));
    }

    private String gatherErrors(ServiceError error) {
        ProtocolStringList messages = error.getMessagesList();
        return messages.stream().collect(Collectors.joining("\n"));
    }

    // public for use by clients of the filesystem, e.g. tests
    public DeployDataProto signDeploy(DeployDataProto deploy) throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance(Blake2b.BLAKE2_B_256);
        final Secp256k1 secp256k1 = Secp256k1.get();

        DeployDataProto.Builder builder = DeployDataProto.newBuilder();

        builder
            .setTerm(deploy.getTerm())
            .setTimestamp(deploy.getTimestamp())
            .setPhloPrice(deploy.getPhloPrice())
            .setPhloLimit(deploy.getPhloLimit())
            .setValidAfterBlockNumber(deploy.getValidAfterBlockNumber())
            .setShardId(deploy.getShardId());

        DeployDataProto signed = builder.build();

        byte[] serial = signed.toByteArray();
        digest.update(serial);
        byte[] hashed = digest.digest();
        byte[] signature = secp256k1.compact2der(secp256k1.sign(hashed, signingKey));
        byte[] pubKey = secp256k1.pubKeyCompress(secp256k1.pubkeyCreate(signingKey));

        DeployDataProto.Builder outbound = signed.toBuilder();
        outbound
            .setSigAlgorithm("secp256k1")
            .setSig(ByteString.copyFrom(signature))
            .setDeployer(ByteString.copyFrom(pubKey));

        return outbound.build();
    }

    public String loadStringResource(String path) throws IOException {
        byte[] bytes;

        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);

        try {
            bytes = stream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            stream.close();
        }
    }

  /**
   * Converts an array of characters representing hexidecimal values into an
   * array of bytes of those same values. The returned array will be half the
   * length of the passed array, as it takes two characters to represent any
   * given byte. An exception is thrown if the passed char array has an odd
   * number of elements.
   * 
   * @param data
   *          An array of characters containing hexidecimal digits
   * @return A byte array containing binary data decoded from the supplied char
   *         array.
   * @throws DecoderException
   *           Thrown if an odd number or illegal of characters is supplied
   */
  public static byte[] decodeHex(char[] data) throws RuntimeException {

    int len = data.length;

    if ((len & 0x01) != 0) {
      throw new RuntimeException("Odd number of characters.");
    }

    byte[] out = new byte[len >> 1];

    // two characters form the hex value.
    for (int i = 0, j = 0; j < len; i++) {
      int f = toDigit(data[j], j) << 4;
      j++;
      f = f | toDigit(data[j], j);
      j++;
      out[i] = (byte) (f & 0xFF);
    }

    return out;
  }

  /**
   * Converts a hexadecimal character to an integer.
   * 
   * @param ch
   *          A character to convert to an integer digit
   * @param index
   *          The index of the character in the source
   * @return An integer
   * @throws DecoderException
   *           Thrown if ch is an illegal hex character
   */
  protected static int toDigit(char ch, int index) throws RuntimeException {
    int digit = Character.digit(ch, 16);
    if (digit == -1) {
      throw new RuntimeException("Illegal hexadecimal charcter " + ch + " at index " + index);
    }
    return digit;
  }

// TODO: Implement `@Override`n FS methods with `signDeploy()` and `deployService` calls
/*

    @Override
    public int getattr(String path, FileStat stat) {
        int res = 0;
        if (Objects.equals(path, "/")) {
            stat.st_mode.set(FileStat.S_IFDIR | 0755);
            stat.st_nlink.set(2);
        } else if (HELLO_PATH.equals(path)) {
            stat.st_mode.set(FileStat.S_IFREG | 0444);
            stat.st_nlink.set(1);
            stat.st_size.set(HELLO_STR.getBytes().length);
        } else {
            res = -ErrorCodes.ENOENT();
        }
        return res;
    }

    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, @off_t long offset, FuseFileInfo fi) {
        if (!"/".equals(path)) {
            return -ErrorCodes.ENOENT();
        }

        filter.apply(buf, ".", null, 0);
        filter.apply(buf, "..", null, 0);
        filter.apply(buf, HELLO_PATH.substring(1), null, 0);
        return 0;
    }

    @Override
    public int open(String path, FuseFileInfo fi) {
        if (!HELLO_PATH.equals(path)) {
            return -ErrorCodes.ENOENT();
        }
        return 0;
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        if (!HELLO_PATH.equals(path)) {
            return -ErrorCodes.ENOENT();
        }

        byte[] bytes = HELLO_STR.getBytes();
        int length = bytes.length;
        if (offset < length) {
            if (offset + size > length) {
                size = length - offset;
            }
            buf.put(0, bytes, 0, bytes.length);
        } else {
            size = 0;
        }
        return (int) size;
    }

    @Override
    public int mkdir(String path, @mode_t long mode) {
        return 0;
    }

    @Override
    public int create(String path, @mode_t long mode, FuseFileInfo fi) {
        //return -ErrorCodes.ENOSYS();
        List<String>   segments  = Arrays.asList(path.split("\\/"));
        int            partsSize = segments.size();
        
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
	
      return 0;
    }

    @Override
    public int statfs(String path, Statvfs stbuf) {
    }

    @Override
    public int rename(String path, String newName) {
    }

    @Override
    public int rmdir(String path) {
    }

    @Override
    public int truncate(String path, long offset) {
    }

    @Override
    public int unlink(String path) {
    }

    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
    }

*/
    
    public static void main(String[] args) {
        // TODO: Revise this to take signing key and F1r3fly node host and port from args
        F1r3flyFS stub = new F1r3flyFS();
        try {
            String path;
            switch (Platform.getNativePlatform().getOS()) {
                case WINDOWS:
                    path = "J:\\";
                    break;
                default:
                    path = "/tmp/mnth";
            }
            stub.mount(Paths.get(path), true, true);
        } finally {
            stub.umount();
        }
    }
}
