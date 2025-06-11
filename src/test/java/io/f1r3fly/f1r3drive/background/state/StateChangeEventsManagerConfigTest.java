package io.f1r3fly.f1r3drive.background.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StateChangeEventsManagerConfig.
 */
class StateChangeEventsManagerConfigTest {

    @Test
    void shouldCreateConfigWithDefaultValues() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.defaultConfig();
        
        // Then
        assertEquals(1000, config.getQueueCapacity());
        assertEquals(Runtime.getRuntime().availableProcessors(), config.getThreadPoolSize());
        assertEquals(5000, config.getShutdownTimeoutMs());
        assertTrue(config.shouldRegisterShutdownHook());
    }

    @Test
    void shouldCreateConfigWithBuilderDefaults() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.builder().build();
        
        // Then
        assertEquals(1000, config.getQueueCapacity());
        assertEquals(Runtime.getRuntime().availableProcessors(), config.getThreadPoolSize());
        assertEquals(5000, config.getShutdownTimeoutMs());
        assertTrue(config.shouldRegisterShutdownHook());
    }

    @Test
    void shouldCreateConfigWithCustomQueueCapacity() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.builder()
            .queueCapacity(500)
            .build();
        
        // Then
        assertEquals(500, config.getQueueCapacity());
        assertEquals(Runtime.getRuntime().availableProcessors(), config.getThreadPoolSize());
        assertEquals(5000, config.getShutdownTimeoutMs());
        assertTrue(config.shouldRegisterShutdownHook());
    }

    @Test
    void shouldCreateConfigWithCustomThreadPoolSize() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.builder()
            .threadPoolSize(8)
            .build();
        
        // Then
        assertEquals(1000, config.getQueueCapacity());
        assertEquals(8, config.getThreadPoolSize());
        assertEquals(5000, config.getShutdownTimeoutMs());
        assertTrue(config.shouldRegisterShutdownHook());
    }

    @Test
    void shouldCreateConfigWithCustomShutdownTimeout() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.builder()
            .shutdownTimeoutMs(10000)
            .build();
        
        // Then
        assertEquals(1000, config.getQueueCapacity());
        assertEquals(Runtime.getRuntime().availableProcessors(), config.getThreadPoolSize());
        assertEquals(10000, config.getShutdownTimeoutMs());
        assertTrue(config.shouldRegisterShutdownHook());
    }

    @Test
    void shouldCreateConfigWithCustomShutdownHookFlag() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.builder()
            .registerShutdownHook(false)
            .build();
        
        // Then
        assertEquals(1000, config.getQueueCapacity());
        assertEquals(Runtime.getRuntime().availableProcessors(), config.getThreadPoolSize());
        assertEquals(5000, config.getShutdownTimeoutMs());
        assertFalse(config.shouldRegisterShutdownHook());
    }

    @Test
    void shouldCreateConfigWithAllCustomValues() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.builder()
            .queueCapacity(750)
            .threadPoolSize(4)
            .shutdownTimeoutMs(3000)
            .registerShutdownHook(false)
            .build();
        
        // Then
        assertEquals(750, config.getQueueCapacity());
        assertEquals(4, config.getThreadPoolSize());
        assertEquals(3000, config.getShutdownTimeoutMs());
        assertFalse(config.shouldRegisterShutdownHook());
    }

    @Test
    void shouldValidateQueueCapacity() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> 
            StateChangeEventsManagerConfig.builder().queueCapacity(0));
            
        assertThrows(IllegalArgumentException.class, () -> 
            StateChangeEventsManagerConfig.builder().queueCapacity(-1));
            
        assertThrows(IllegalArgumentException.class, () -> 
            StateChangeEventsManagerConfig.builder().queueCapacity(-100));
    }

    @Test
    void shouldValidateThreadPoolSize() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> 
            StateChangeEventsManagerConfig.builder().threadPoolSize(0));
            
        assertThrows(IllegalArgumentException.class, () -> 
            StateChangeEventsManagerConfig.builder().threadPoolSize(-1));
            
        assertThrows(IllegalArgumentException.class, () -> 
            StateChangeEventsManagerConfig.builder().threadPoolSize(-5));
    }

    @Test
    void shouldValidateShutdownTimeout() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> 
            StateChangeEventsManagerConfig.builder().shutdownTimeoutMs(-1));
            
        assertThrows(IllegalArgumentException.class, () -> 
            StateChangeEventsManagerConfig.builder().shutdownTimeoutMs(-1000));
    }

    @Test
    void shouldAllowZeroShutdownTimeout() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.builder()
            .shutdownTimeoutMs(0)
            .build();
        
        // Then
        assertEquals(0, config.getShutdownTimeoutMs());
    }

    @Test
    void shouldAllowMinimumValidValues() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.builder()
            .queueCapacity(1)
            .threadPoolSize(1)
            .shutdownTimeoutMs(0)
            .build();
        
        // Then
        assertEquals(1, config.getQueueCapacity());
        assertEquals(1, config.getThreadPoolSize());
        assertEquals(0, config.getShutdownTimeoutMs());
    }

    @Test
    void shouldAllowLargeValidValues() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.builder()
            .queueCapacity(Integer.MAX_VALUE)
            .threadPoolSize(1000)
            .shutdownTimeoutMs(Long.MAX_VALUE)
            .build();
        
        // Then
        assertEquals(Integer.MAX_VALUE, config.getQueueCapacity());
        assertEquals(1000, config.getThreadPoolSize());
        assertEquals(Long.MAX_VALUE, config.getShutdownTimeoutMs());
    }

    @Test
    void shouldSupportMethodChaining() {
        // When
        StateChangeEventsManagerConfig config = StateChangeEventsManagerConfig.builder()
            .queueCapacity(500)
            .threadPoolSize(4)
            .shutdownTimeoutMs(2000)
            .registerShutdownHook(true)
            .build();
        
        // Then
        assertEquals(500, config.getQueueCapacity());
        assertEquals(4, config.getThreadPoolSize());
        assertEquals(2000, config.getShutdownTimeoutMs());
        assertTrue(config.shouldRegisterShutdownHook());
    }

    @Test
    void shouldBeImmutableAfterCreation() {
        // Given
        StateChangeEventsManagerConfig.Builder builder = StateChangeEventsManagerConfig.builder()
            .queueCapacity(500);
        
        StateChangeEventsManagerConfig config1 = builder.build();
        
        // When - modify builder after first build
        StateChangeEventsManagerConfig config2 = builder
            .queueCapacity(1000)
            .build();
        
        // Then - first config should remain unchanged
        assertEquals(500, config1.getQueueCapacity());
        assertEquals(1000, config2.getQueueCapacity());
    }

    @Test
    void shouldCreateNewBuilderInstances() {
        // When
        StateChangeEventsManagerConfig.Builder builder1 = StateChangeEventsManagerConfig.builder();
        StateChangeEventsManagerConfig.Builder builder2 = StateChangeEventsManagerConfig.builder();
        
        // Then
        assertNotSame(builder1, builder2);
    }

    @Test
    void shouldPreserveBuilderStateAcrossMethodCalls() {
        // Given
        StateChangeEventsManagerConfig.Builder builder = StateChangeEventsManagerConfig.builder();
        
        // When
        StateChangeEventsManagerConfig.Builder result1 = builder.queueCapacity(200);
        StateChangeEventsManagerConfig.Builder result2 = result1.threadPoolSize(2);
        StateChangeEventsManagerConfig.Builder result3 = result2.shutdownTimeoutMs(1500);
        StateChangeEventsManagerConfig.Builder result4 = result3.registerShutdownHook(false);
        
        // Then - all should reference the same builder instance
        assertSame(builder, result1);
        assertSame(builder, result2);
        assertSame(builder, result3);
        assertSame(builder, result4);
        
        StateChangeEventsManagerConfig config = result4.build();
        assertEquals(200, config.getQueueCapacity());
        assertEquals(2, config.getThreadPoolSize());
        assertEquals(1500, config.getShutdownTimeoutMs());
        assertFalse(config.shouldRegisterShutdownHook());
    }

    @Test
    void shouldValidateAtBuildTime() {
        // Given
        StateChangeEventsManagerConfig.Builder builder = StateChangeEventsManagerConfig.builder()
            .queueCapacity(100);  // Valid value
        
        // When/Then - validation should happen at build time
        assertDoesNotThrow(() -> builder.build());
        
        // Invalid value should be caught when set
        assertThrows(IllegalArgumentException.class, () -> 
            builder.queueCapacity(-1));
    }

    @Test
    void shouldHandleEdgeCaseValues() {
        // When/Then - Test boundary values
        assertDoesNotThrow(() -> 
            StateChangeEventsManagerConfig.builder()
                .queueCapacity(1)
                .threadPoolSize(1)
                .shutdownTimeoutMs(0)
                .build());
                
        assertDoesNotThrow(() -> 
            StateChangeEventsManagerConfig.builder()
                .queueCapacity(Integer.MAX_VALUE)
                .threadPoolSize(Integer.MAX_VALUE)
                .shutdownTimeoutMs(Long.MAX_VALUE)
                .build());
    }
} 