package coop.f1r3fly.fs.struct;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Iterator;

import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.ExposedPort;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.google.protobuf.ProtocolStringList;

import casper.CasperMessage.DeployDataProto;
import casper.v1.DeployServiceGrpc;
import casper.v1.DeployServiceGrpc.DeployServiceBlockingStub;
import casper.v1.DeployServiceV1.DeployResponse;
import servicemodelapi.ServiceErrorOuterClass.ServiceError;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.acinq.secp256k1.Hex;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import jnr.ffi.LibraryLoader;

import coop.f1r3fly.fs.examples.F1r3flyFS;
import coop.f1r3fly.fs.LibFuse;
import coop.f1r3fly.fs.utils.WinPathUtils;

@Testcontainers
public class F1r3flyFSTest {
  public static final int GRPC_PORT = 40401;

  public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse("ghcr.io/f1r3fly-io/rnode:latest");

  private static final Duration STARTUP_TIMEOUT      = Duration.ofMinutes(1);
  private static final String[] osxFuseLibraries     = {"fuse4x", "osxfuse", "macfuse", "fuse"};
  private static final String   PROPERTY_WINLIB_PATH = "jnr-fuse.windows.libpath";
  private static final String   validatorPrivateKey  = "aebb63dc0d50e4dd29ddd94fb52103bfe0dc4941fa0c2c8a9082a191af35ffa1";

  @Container
  static GenericContainer<?> f1r3fly = new GenericContainer<>(F1R3FLY_IMAGE)
      .withFileSystemBind("data/", "/var/lib/rnode/", BindMode.READ_WRITE)
      .withExposedPorts(GRPC_PORT)
      .withCommand("run -s --no-upnp --allow-private-addresses --synchrony-constraint-threshold=0.0 --validator-private-key \"" + validatorPrivateKey + "\"")
      .waitingFor(Wait.forHealthcheck())
      .withStartupTimeout(STARTUP_TIMEOUT);

  private static F1r3flyFS             f1r3flyFS;
  private static DeployServiceBlockingStub stub;

  @BeforeAll
  static void setUp() {
    ManagedChannel            channel   = ManagedChannelBuilder.forAddress(f1r3fly.getHost(), f1r3fly.getMappedPort(GRPC_PORT)).usePlaintext().build();
    stub                                = DeployServiceGrpc.newBlockingStub(channel);
                              f1r3flyFS = new F1r3flyFS(Hex.decode(validatorPrivateKey), stub);
  }

  // Copied from AbstractFuseFS constructor. Ugh.
  private Boolean libFUSEAvailable() {
    jnr.ffi.Platform p = jnr.ffi.Platform.getNativePlatform();
    LibFuse libFuse = null;
    switch (p.getOS()) {
        case DARWIN:
            for (String library : osxFuseLibraries) {
                try {
                    // Regular FUSE-compatible fuse library
                    libFuse = LibraryLoader.create(LibFuse.class)
                        .failImmediately()
                        // adds user library search path which is not included by default
                        .search("/usr/local/lib/")
                        .load(library);
                    break;
                } catch (Throwable e) {
                    // Carry on
                }
            }
            if (libFuse == null) {
                // Everything failed. Do a last-ditch attempt.
                // Worst-case scenario, this causes an exception
                // which will be more meaningful to the user than a NullPointerException on libFuse.
                libFuse = LibraryLoader.create(LibFuse.class).failImmediately().load("fuse");
            }
            break;
        case WINDOWS:
            //see if the property is set, otherwise use winfsp
            String windowsLibPath = System.getProperty(PROPERTY_WINLIB_PATH, WinPathUtils.getWinFspPath());
            libFuse = LibraryLoader.create(LibFuse.class).failImmediately().load(windowsLibPath);
            break;
        case LINUX:
        default: // Assume linux since we have no further OS evidence
            try {
                // Try loading library by going through the library mapping process, see j.l.System.mapLibraryName
                libFuse = LibraryLoader.create(LibFuse.class).failImmediately().load("fuse");
            } catch (Throwable e) {
                // Try loading the dynamic library directly which will end up calling dlopen directly, see
                // com.kenai.jffi.Foreign.dlopen
                libFuse = LibraryLoader.create(LibFuse.class).failImmediately().load("libfuse.so.2");
            }
    }
    return libFuse != null;
  }

  @Test
  void shouldHaveLibFuse() {
    assertTrue(libFUSEAvailable());
  }

  @Test
  void shouldRunF1r3fly() {
    assertTrue(f1r3fly.isRunning());
  }

  @Test
  void shouldDeployRholang() throws IOException, NoSuchAlgorithmException {
    String rhoCode = f1r3flyFS.loadStringResource("tut-registry.rho");

    DeployDataProto deployment = DeployDataProto.newBuilder()
        .setTerm(rhoCode)
        .setTimestamp(12345)
        .setPhloPrice(56789)
        .setPhloLimit(98765)
        .setShardId("root")
        .build();

    DeployDataProto signed = f1r3flyFS.signDeploy(deployment);

    DeployResponse response = stub.doDeploy(signed);

    String result = response.getResult();
        
    assertTrue(!result.isBlank());
  }
}
