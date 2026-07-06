package io.f1r3fly.f1r3drive.filesystem.deployable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import io.f1r3fly.f1r3drive.blockchain.BlockchainContext;
import io.f1r3fly.f1r3drive.filesystem.common.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockchainDirectoryTest {

    @Test
    void getChildrenReturnsImmutableSet() {
        BlockchainDirectory directory = new BlockchainDirectory(
                mock(BlockchainContext.class),
                "directory",
                null,
                false);
        Path child = mock(Path.class);
        directory.children.add(child);

        Set<Path> children = directory.getChildren();

        assertThrows(UnsupportedOperationException.class, () -> children.add(mock(Path.class)));
        assertThrows(UnsupportedOperationException.class, () -> children.remove(child));
        assertThrows(UnsupportedOperationException.class, children::clear);
    }
}
