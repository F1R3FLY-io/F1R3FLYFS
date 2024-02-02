package coop.f1r3fly.fs.struct;

import java.time.Duration;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.images.builder.Transferable;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class F1r3flyFSTest {
  public static final DockerImageName F1R3FLY_IMAGE = DockerImageName.parse("ghcr.io/f1r3fly-io/rnode:latest");
  private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(1);

  @Container
  static GenericContainer<?> f1r3fly = new GenericContainer<>(F1R3FLY_IMAGE)
      .withCopyFileToContainer(MountableFile.forHostPath("data/", 0766), "/var/lib/rnode/")
      .withExposedPorts(40401)
      .withCommand("run -s --no-upnp --allow-private-addresses --synchrony-constraint-threshold=0.0 --validator-private-key \"aebb63dc0d50e4dd29ddd94fb52103bfe0dc4941fa0c2c8a9082a191af35ffa1\"")
      .waitingFor(Wait.forListeningPort())
      .withStartupTimeout(STARTUP_TIMEOUT);

  @Test
  void shouldRunF1r3fly() {
    assertTrue(f1r3fly.isRunning());
  }
}
