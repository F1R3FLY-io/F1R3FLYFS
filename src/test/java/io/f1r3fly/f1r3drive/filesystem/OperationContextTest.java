package io.f1r3fly.f1r3drive.filesystem;

import io.f1r3fly.f1r3drive.filesystem.FileSystemAction;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

public class OperationContextTest {

    private static final Logger logger = LoggerFactory.getLogger(OperationContextTest.class);

    @Test
    public void testOperationContextTracking() {
        // Test that operation context is properly set and cleared
        assertNull(OperationContext.current());
        assertNull(MDC.get("operationId"));
        assertNull(MDC.get("action"));
        assertNull(MDC.get("path"));

        OperationContext.withContext(FileSystemAction.FUSE_CREATE, "/test/file.txt", () -> {
            OperationContext context = OperationContext.current();
            assertNotNull(context);
            assertEquals(FileSystemAction.FUSE_CREATE, context.getAction());
            assertEquals("/test/file.txt", context.getPath());
            
            // Test MDC values are set
            assertNotNull(MDC.get("operationId"));
            assertEquals("FUSE_CREATE", MDC.get("action"));
            assertEquals("/test/file.txt", MDC.get("path"));
            
            logger.info("This log message should contain operation context in MDC");
        });

        // Test that context is cleared after operation
        assertNull(OperationContext.current());
        assertNull(MDC.get("operationId"));
        assertNull(MDC.get("action"));
        assertNull(MDC.get("path"));
    }

    @Test
    public void testNestedOperations() {
        OperationContext.withContext(FileSystemAction.FUSE_WRITE, "/file1.txt", () -> {
            OperationContext outerContext = OperationContext.current();
            assertEquals(FileSystemAction.FUSE_WRITE, outerContext.getAction());
            assertEquals("/file1.txt", outerContext.getPath());
            
            logger.info("Outer operation log");

            // Nested operation (should overwrite the outer context)
            OperationContext.withContext(FileSystemAction.FILE_CREATE, "/file2.txt", () -> {
                OperationContext innerContext = OperationContext.current();
                assertEquals(FileSystemAction.FILE_CREATE, innerContext.getAction());
                assertEquals("/file2.txt", innerContext.getPath());
                
                logger.info("Inner operation log");
            });

            // Back to outer context
            OperationContext restoredContext = OperationContext.current();
            assertNull(restoredContext); // Note: Current implementation doesn't restore previous context
            
            logger.info("Back to outer operation log");
        });
    }

    @Test 
    public void testOperationWithReturnValue() {
        String result = OperationContext.withContext(FileSystemAction.FUSE_READ, "/data.txt", () -> {
            logger.info("Reading file operation");
            return "file_content";
        });
        
        assertEquals("file_content", result);
        assertNull(OperationContext.current());
    }
} 