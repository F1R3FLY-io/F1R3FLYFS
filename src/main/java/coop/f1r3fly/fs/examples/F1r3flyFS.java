package coop.f1r3fly.fs.examples;

import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import coop.f1r3fly.fs.ErrorCodes;
import coop.f1r3fly.fs.FuseFillDir;
import coop.f1r3fly.fs.FuseStubFS;
import coop.f1r3fly.fs.struct.FileStat;
import coop.f1r3fly.fs.struct.FuseFileInfo;
import fr.acinq.secp256k1.Secp256k1;

import java.nio.file.Paths;
import java.util.Objects;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

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

    public static final String HELLO_PATH = "/hello";
    public static final String HELLO_STR = "Hello World!";

    public F1r3flyFS(byte[] signingKey, DeployServiceBlockingStub deployService) {
        Security.addProvider(new Blake2bProvider());
        this.signingKey = signingKey;
        this.deployService = deployService;
    }

    public DeployDataProto signDeploy(DeployDataProto deploy) throws NoSuchAlgorithmException {
        final MessageDigest digest = MessageDigest.getInstance(Blake2b.BLAKE2_B_256);
        Secp256k1 secp256k1 = Secp256k1.get();

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
        byte[] signature = secp256k1.sign(hashed, signingKey);

        DeployDataProto.Builder outbound = signed.toBuilder();
        outbound
            .setSigAlgorithm("secp256k1")
            .setSig(ByteString.copyFrom(signature))
            .setDeployer(ByteString.copyFrom(secp256k1.pubkeyCreate(signingKey)));

        return outbound.build();
    }

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

    public static void main(String[] args) {
        HelloFuse stub = new HelloFuse();
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
