package coop.f1r3fly.fs.struct;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import casper.v1.DeployServiceGrpc;
import casper.v1.ProposeServiceGrpc;
import casper.v1.DeployServiceGrpc.DeployServiceFutureStub;
import casper.v1.ProposeServiceGrpc.ProposeServiceFutureStub;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.acinq.secp256k1.Hex;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import coop.f1r3fly.fs.examples.F1r3flyFS;

@Testcontainers
public class F1r3flyFSTest {
  private static final int      GRPC_PORT           = 40402;
  private static final String   MOUNT_POINT         = "/tmp/f1r3fly-fs";
  private static final Duration STARTUP_TIMEOUT     = Duration.ofMinutes(1);
  private static final String   validatorPrivateKey = "f9854c5199bc86237206c75b25c6aeca024dccc0f55df3a553131111fd25dd85";

  public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse("ghcr.io/f1r3fly-io/rnode:latest");

  @Container
  static GenericContainer<?> f1r3fly = new GenericContainer<>(F1R3FLY_IMAGE)
      .withFileSystemBind("data/", "/var/lib/rnode/", BindMode.READ_WRITE)
      .withExposedPorts(GRPC_PORT)
      .withCommand("run -s --no-upnp --allow-private-addresses --synchrony-constraint-threshold=0.0 --validator-private-key \"" + validatorPrivateKey + "\"")
      .waitingFor(Wait.forHealthcheck())
      .withStartupTimeout(STARTUP_TIMEOUT);

  private static F1r3flyFS f1r3flyFS;

  @BeforeAll
  static void setUp() throws IOException, NoSuchAlgorithmException {
    ManagedChannel           channel        = ManagedChannelBuilder.forAddress(f1r3fly.getHost(), f1r3fly.getMappedPort(GRPC_PORT)).usePlaintext().build();
    DeployServiceFutureStub  deployService  = DeployServiceGrpc.newFutureStub(channel);
    ProposeServiceFutureStub proposeService = ProposeServiceGrpc.newFutureStub(channel);
                             f1r3flyFS      = new F1r3flyFS(Hex.decode(validatorPrivateKey), deployService, proposeService, "onchain-volume.rho");
                             f1r3flyFS.mount(Paths.get(MOUNT_POINT), false);
  }

  @AfterAll
  static void tearDown() {
    f1r3flyFS.umount();    
  }

  @Test
  void shouldRunF1r3fly() {
    assertTrue(f1r3fly.isRunning());
  }
}
