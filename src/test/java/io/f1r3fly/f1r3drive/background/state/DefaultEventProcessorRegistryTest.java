package io.f1r3fly.f1r3drive.background.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DefaultEventProcessorRegistry.
 */
class DefaultEventProcessorRegistryTest {

    private DefaultEventProcessorRegistry registry;
    
    @Mock
    private StateChangeEventProcessor processor1;
    
    @Mock
    private StateChangeEventProcessor processor2;
    
    private static class TestEvent implements StateChangeEvents {}
    private static class AnotherTestEvent implements StateChangeEvents {}

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registry = new DefaultEventProcessorRegistry();
    }

    @Test
    void shouldRegisterProcessor() {
        // When
        registry.registerProcessor(TestEvent.class, processor1);
        
        // Then
        Set<StateChangeEventProcessor> processors = registry.getProcessors(TestEvent.class);
        assertEquals(1, processors.size());
        assertTrue(processors.contains(processor1));
    }

    @Test
    void shouldRegisterMultipleProcessorsForSameEventType() {
        // When
        registry.registerProcessor(TestEvent.class, processor1);
        registry.registerProcessor(TestEvent.class, processor2);
        
        // Then
        Set<StateChangeEventProcessor> processors = registry.getProcessors(TestEvent.class);
        assertEquals(2, processors.size());
        assertTrue(processors.contains(processor1));
        assertTrue(processors.contains(processor2));
    }

    @Test
    void shouldRegisterProcessorsForDifferentEventTypes() {
        // When
        registry.registerProcessor(TestEvent.class, processor1);
        registry.registerProcessor(AnotherTestEvent.class, processor2);
        
        // Then
        Set<StateChangeEventProcessor> testEventProcessors = registry.getProcessors(TestEvent.class);
        Set<StateChangeEventProcessor> anotherTestEventProcessors = registry.getProcessors(AnotherTestEvent.class);
        
        assertEquals(1, testEventProcessors.size());
        assertEquals(1, anotherTestEventProcessors.size());
        assertTrue(testEventProcessors.contains(processor1));
        assertTrue(anotherTestEventProcessors.contains(processor2));
    }

    @Test
    void shouldReturnEmptySetForUnregisteredEventType() {
        // When
        Set<StateChangeEventProcessor> processors = registry.getProcessors(TestEvent.class);
        
        // Then
        assertTrue(processors.isEmpty());
    }

    @Test
    void shouldReturnEmptySetForNullEventType() {
        // When
        Set<StateChangeEventProcessor> processors = registry.getProcessors(null);
        
        // Then
        assertTrue(processors.isEmpty());
    }

    @Test
    void shouldUnregisterProcessor() {
        // Given
        registry.registerProcessor(TestEvent.class, processor1);
        registry.registerProcessor(TestEvent.class, processor2);
        
        // When
        registry.unregisterProcessor(TestEvent.class, processor1);
        
        // Then
        Set<StateChangeEventProcessor> processors = registry.getProcessors(TestEvent.class);
        assertEquals(1, processors.size());
        assertFalse(processors.contains(processor1));
        assertTrue(processors.contains(processor2));
    }

    @Test
    void shouldCleanUpEmptySetAfterUnregisteringLastProcessor() {
        // Given
        registry.registerProcessor(TestEvent.class, processor1);
        
        // When
        registry.unregisterProcessor(TestEvent.class, processor1);
        
        // Then
        Set<StateChangeEventProcessor> processors = registry.getProcessors(TestEvent.class);
        assertTrue(processors.isEmpty());
        
        // Verify memory cleanup by registering again
        registry.registerProcessor(TestEvent.class, processor2);
        processors = registry.getProcessors(TestEvent.class);
        assertEquals(1, processors.size());
        assertTrue(processors.contains(processor2));
    }

    @Test
    void shouldHandleUnregisteringNonExistentProcessor() {
        // Given
        registry.registerProcessor(TestEvent.class, processor1);
        
        // When - no exception should be thrown
        registry.unregisterProcessor(TestEvent.class, processor2);
        
        // Then
        Set<StateChangeEventProcessor> processors = registry.getProcessors(TestEvent.class);
        assertEquals(1, processors.size());
        assertTrue(processors.contains(processor1));
    }

    @Test
    void shouldHandleUnregisteringFromNonExistentEventType() {
        // When - no exception should be thrown
        registry.unregisterProcessor(TestEvent.class, processor1);
        
        // Then
        Set<StateChangeEventProcessor> processors = registry.getProcessors(TestEvent.class);
        assertTrue(processors.isEmpty());
    }

    @Test
    void shouldHandleNullArgumentsInRegister() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> 
            registry.registerProcessor(null, processor1));
            
        assertThrows(IllegalArgumentException.class, () -> 
            registry.registerProcessor(TestEvent.class, null));
            
        assertThrows(IllegalArgumentException.class, () -> 
            registry.registerProcessor(null, null));
    }

    @Test
    void shouldHandleNullArgumentsInUnregister() {
        assertDoesNotThrow(() -> registry.unregisterProcessor(null, processor1));
        assertDoesNotThrow(() -> registry.unregisterProcessor(TestEvent.class, null));
        assertDoesNotThrow(() -> registry.unregisterProcessor(null, null));
    }

    @Test
    void shouldReturnImmutableSetFromGetProcessors() {
        registry.registerProcessor(TestEvent.class, processor1);
        
        Set<StateChangeEventProcessor> processors = registry.getProcessors(TestEvent.class);
        
        assertThrows(UnsupportedOperationException.class, () -> 
            processors.add(processor2));
    }

    @Test
    void shouldBeThreadSafe() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        int numberOfOperations = 1000;
        
        CompletableFuture<Void>[] futures = new CompletableFuture[numberOfOperations];
        
        for (int i = 0; i < numberOfOperations; i++) {
            final int index = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                StateChangeEventProcessor processor = mock(StateChangeEventProcessor.class);
                
                if (index % 2 == 0) {
                    registry.registerProcessor(TestEvent.class, processor);
                } else {
                    registry.unregisterProcessor(TestEvent.class, processor);
                }
                
                registry.getProcessors(TestEvent.class);
            }, executor);
        }
        
        CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);
        
        Set<StateChangeEventProcessor> processors = registry.getProcessors(TestEvent.class);
        assertNotNull(processors);
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldPreventDuplicateProcessorRegistration() {
        registry.registerProcessor(TestEvent.class, processor1);
        registry.registerProcessor(TestEvent.class, processor1);
        
        Set<StateChangeEventProcessor> processors = registry.getProcessors(TestEvent.class);
        assertEquals(1, processors.size());
        assertTrue(processors.contains(processor1));
    }
} 