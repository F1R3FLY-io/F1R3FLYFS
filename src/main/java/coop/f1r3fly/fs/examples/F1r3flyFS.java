package coop.f1r3fly.fs.examples;

import jnr.ffi.Platform;
import jnr.ffi.Pointer;

import jnr.ffi.types.dev_t;
import jnr.ffi.types.gid_t;
import jnr.ffi.types.mode_t;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import jnr.ffi.types.u_int32_t;
import jnr.ffi.types.uid_t;
import coop.f1r3fly.fs.struct.FileStat;
import coop.f1r3fly.fs.struct.Flock;
import coop.f1r3fly.fs.struct.FuseBuf;
import coop.f1r3fly.fs.flags.FuseBufFlags;
import coop.f1r3fly.fs.struct.FuseBufvec;
import coop.f1r3fly.fs.struct.FuseFileInfo;
import coop.f1r3fly.fs.struct.FusePollhandle;
import coop.f1r3fly.fs.struct.Statvfs;
import coop.f1r3fly.fs.struct.Timespec;

import coop.f1r3fly.fs.ErrorCodes;
import coop.f1r3fly.fs.FuseFillDir;
import coop.f1r3fly.fs.FuseStubFS;
import coop.f1r3fly.fs.struct.FileStat;
import coop.f1r3fly.fs.struct.FuseFileInfo;

import com.google.protobuf.ProtocolStringList;

import casper.CasperMessage.DeployDataProto;
import casper.DeployServiceCommon.DataAtNameQuery;
import casper.v1.DeployServiceGrpc;
import casper.v1.DeployServiceGrpc.DeployServiceBlockingStub;
import casper.v1.DeployServiceV1.DeployResponse;
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
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.List;

import com.rfksystems.blake2b.Blake2b;
import com.rfksystems.blake2b.security.Blake2bProvider;

import com.google.protobuf.ByteString;

import casper.CasperMessage.DeployDataProto;
import casper.v1.DeployServiceGrpc.DeployServiceBlockingStub;

/**
 * @author Sergey Tselovalnikov
 * @see <a href="http://fuse.sourceforge.net/helloworld.html">helloworld</a>
 * @since 31.05.15
 */
public class F1r3flyFS extends FuseStubFS {
    private byte[] signingKey;
    private DeployServiceBlockingStub deployService;

    // Add private `String` variables for Rholang code here

    public static final String HELLO_PATH = "/hello";
    public static final String HELLO_STR = "Hello World!";

    private F1r3flyFS() {}                // Disable nullary constructor

    public F1r3flyFS(byte[] signingKey, DeployServiceBlockingStub deployService, String onChainVolumeCode) throws IOException, NoSuchAlgorithmException {
        super();

        Security.addProvider(new Blake2bProvider());
        this.signingKey = signingKey;
        this.deployService = deployService;

      	// Initialize private `String` variables with `loadStringResource()` here
      	String rhoCode = loadStringResource( onChainVolumeCode );
      	// Make deployment
      	DeployDataProto deployment = DeployDataProto.newBuilder()
              .setTerm(rhoCode)
              .setTimestamp(12345)
              .setPhloPrice(56789)
              .setPhloLimit(98765)
              .setShardId("root")
              .build();
      
      	// Sign deployment
      	DeployDataProto signed = signDeploy(deployment);
      
      	// Deploy
      	DeployResponse response = deployService.doDeploy(signed);
      
      	// Check response
      
      	if (response.hasError()) {
            ServiceError error = response.getError();
            ProtocolStringList messages = error.getMessagesList();
            String message = messages.stream().collect(Collectors.joining("\n"));
      
      	    throw new RuntimeException(message);
      	} else {
/*
            DataAtNameQuery query = DataAtNameQuery.newBuilder()
            .build();

            deployService.getDataAtName(query)
*/
            // ListenForDataAtName
            // Using well-known name
            // To get some URI to use throughout, i.e. declare a new private variable for it
        }
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

// Implement `@Override`n FS methods with `signDeploy()` and `deployService` calls

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

    public static void main(String[] args) {
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
