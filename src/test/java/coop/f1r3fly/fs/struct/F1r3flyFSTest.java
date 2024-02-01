package coop.f1r3fly.fs.struct;

import java.io.File;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class F1r3flyFSTest {
  @Container
  public static ComposeContainer environment =
      new ComposeContainer(new File("src/test/resources/compose-test.yml"));
  
  @Test
  void shouldBlah() {
    assertTrue(false);
  }
}
