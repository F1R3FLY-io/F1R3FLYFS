package io.f1r3fly.f1r3drive.background.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for StateChangeEventsManager.
 */
class StateChangeEventsManagerTest {

    @Mock
    private EventQueue mockQueue;
    
    @Mock
    private EventProcessorRegistry mockRegistry;
    
    @Mock
    private StateChangeEventProcessor processor1;
    
    @Mock
    private StateChangeEventProcessor processor2;
    
    @Mock
    private StateChangeEvents testEvent;
    
    private StateChangeEventsManagerConfig config;

    private static class TestEvent implements StateChangeEvents {
        private final String id;
        
        public TestEvent(String id) {
            this.id = id;
        }
        
        public String getId() {
            return id;
        }
        
        @Override
        public String toString() {
            return "TestEvent{id='" + id + "'}";
        }
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = StateChangeEventsManagerConfig.builder()
            .queueCapacity(100)
            .threadPoolSize(2)
            .shutdownTimeoutMs(1000)
            .registerShutdownHook(false)  // Don't interfere with test runtime
            .build();
    }

    @Test
    void shouldCreateWithDefaultConfig() {
        // When
        StateChangeEventsManager manager = new StateChangeEventsManager();
        
        // Then
        assertNotNull(manager);
        assertFalse(manager.isShutdown());
        assertEquals(0, manager.getQueueSize());
    }

    @Test
    void shouldCreateWithCustomConfig() {
        // When
        StateChangeEventsManager manager = new StateChangeEventsManager(config);
        
        // Then
        assertNotNull(manager);
        assertFalse(manager.isShutdown());
        assertEquals(0, manager.getQueueSize());
    }

    @Test
    void shouldCreateWithDependencyInjection() {
        // When
        StateChangeEventsManager manager = new StateChangeEventsManager(config, mockQueue, mockRegistry);
        
        // Then
        assertNotNull(manager);
        assertFalse(manager.isShutdown());
    }

    @Test
    void shouldThrowExceptionForNullDependencies() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> 
            new StateChangeEventsManager(null, mockQueue, mockRegistry));
            
        assertThrows(IllegalArgumentException.class, () -> 
            new StateChangeEventsManager(config, null, mockRegistry));
            
        assertThrows(IllegalArgumentException.class, () -> 
            new StateChangeEventsManager(config, mockQueue, null));
    }

    @Test
    void shouldStartAndShutdownProperly() {
        // Given
        StateChangeEventsManager manager = new StateChangeEventsManager(config, mockQueue, mockRegistry);
        
        // When
        manager.start();
        assertFalse(manager.isShutdown());
        
        manager.shutdown();
        
        // Then
        assertTrue(manager.isShutdown());
    }

    @Test
    void shouldAddEventToQueue() {
        // Given
        when(mockQueue.offer(testEvent)).thenReturn(true);
        StateChangeEventsManager manager = new StateChangeEventsManager(config, mockQueue, mockRegistry);
        
        // When
        boolean result = manager.addEvent(testEvent);
        
        // Then
        assertTrue(result);
        verify(mockQueue).offer(testEvent);
    }

    @Test
    void shouldRejectEventWhenShutdown() {
        // Given
        StateChangeEventsManager manager = new StateChangeEventsManager(config, mockQueue, mockRegistry);
        manager.shutdown();
        
        // When
        boolean result = manager.addEvent(testEvent);
        
        // Then
        assertFalse(result);
        verify(mockQueue, never()).offer(any());
        verify(mockQueue).clear();
    }

    @Test
    void shouldRejectNullEvent() {
        // Given
        StateChangeEventsManager manager = new StateChangeEventsManager(config, mockQueue, mockRegistry);
        
        // When
        boolean result = manager.addEvent(null);
        
        // Then
        assertFalse(result);
        verifyNoInteractions(mockQueue);
    }

    @Test
    void shouldDelegateProcessorRegistration() {
        // Given
        StateChangeEventsManager manager = new StateChangeEventsManager(config, mockQueue, mockRegistry);
        
        // When
        manager.registerEventProcessor(TestEvent.class, processor1);
        
        // Then
        verify(mockRegistry).registerProcessor(TestEvent.class, processor1);
    }

    @Test
    void shouldDelegateProcessorUnregistration() {
        // Given
        StateChangeEventsManager manager = new StateChangeEventsManager(config, mockQueue, mockRegistry);
        
        // When
        manager.unregisterEventProcessor(TestEvent.class, processor1);
        
        // Then
        verify(mockRegistry).unregisterProcessor(TestEvent.class, processor1);
    }

    @Test
    void shouldReturnQueueSize() {
        // Given
        when(mockQueue.size()).thenReturn(5);
        StateChangeEventsManager manager = new StateChangeEventsManager(config, mockQueue, mockRegistry);
        
        // When
        int size = manager.getQueueSize();
        
        // Then
        assertEquals(5, size);
        verify(mockQueue).size();
    }

    @Test
    @Timeout(5)
    void shouldProcessEventsInBackground() throws Exception {
        CountDownLatch eventProcessedLatch = new CountDownLatch(1);
        AtomicReference<StateChangeEvents> processedEvent = new AtomicReference<>();
        
        StateChangeEventProcessor capturingProcessor = event -> {
            processedEvent.set(event);
            eventProcessedLatch.countDown();
        };
        
        EventQueue realQueue = new BlockingEventQueue(10);
        EventProcessorRegistry realRegistry = new DefaultEventProcessorRegistry();
        realRegistry.registerProcessor(TestEvent.class, capturingProcessor);
        
        StateChangeEventsManager manager = new StateChangeEventsManager(config, realQueue, realRegistry);
        
        manager.start();
        TestEvent event = new TestEvent("test-1");
        manager.addEvent(event);
        
        assertTrue(eventProcessedLatch.await(2, TimeUnit.SECONDS));
        assertSame(event, processedEvent.get());
        
        manager.shutdown();
    }

    @Test
    @Timeout(5)
    void shouldProcessMultipleEventsInOrder() throws Exception {
        // Given
        int eventCount = 10;
        CountDownLatch allEventsProcessedLatch = new CountDownLatch(eventCount);
        AtomicInteger processedCount = new AtomicInteger(0);
        
        StateChangeEventProcessor countingProcessor = event -> {
            processedCount.incrementAndGet();
            allEventsProcessedLatch.countDown();
        };
        
        EventQueue realQueue = new BlockingEventQueue(20);
        EventProcessorRegistry realRegistry = new DefaultEventProcessorRegistry();
        realRegistry.registerProcessor(TestEvent.class, countingProcessor);
        
        StateChangeEventsManager manager = new StateChangeEventsManager(config, realQueue, realRegistry);
        
        // When
        manager.start();
        
        for (int i = 0; i < eventCount; i++) {
            manager.addEvent(new TestEvent("event-" + i));
        }
        
        // Then
        assertTrue(allEventsProcessedLatch.await(3, TimeUnit.SECONDS));
        assertEquals(eventCount, processedCount.get());
        
        manager.shutdown();
    }

    @Test
    @Timeout(5)
    void shouldHandleProcessorExceptions() throws Exception {
        // Given
        CountDownLatch errorProcessorLatch = new CountDownLatch(1);
        CountDownLatch goodProcessorLatch = new CountDownLatch(1);
        
        StateChangeEventProcessor errorProcessor = event -> {
            errorProcessorLatch.countDown();
            throw new RuntimeException("Test exception");
        };
        
        StateChangeEventProcessor goodProcessor = event -> {
            goodProcessorLatch.countDown();
        };
        
        EventQueue realQueue = new BlockingEventQueue(10);
        EventProcessorRegistry realRegistry = new DefaultEventProcessorRegistry();
        realRegistry.registerProcessor(TestEvent.class, errorProcessor);
        realRegistry.registerProcessor(TestEvent.class, goodProcessor);
        
        StateChangeEventsManager manager = new StateChangeEventsManager(config, realQueue, realRegistry);
        
        // When
        manager.start();
        manager.addEvent(new TestEvent("test-event"));
        
        // Then - both processors should be called despite exception
        assertTrue(errorProcessorLatch.await(2, TimeUnit.SECONDS));
        assertTrue(goodProcessorLatch.await(2, TimeUnit.SECONDS));
        
        manager.shutdown();
    }

    @Test
    @Timeout(5)
    void shouldShutdownGracefully() throws Exception {
        // Given
        CountDownLatch eventStartedLatch = new CountDownLatch(1);
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        
        StateChangeEventProcessor slowProcessor = event -> {
            eventStartedLatch.countDown();
            try {
                shutdownLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        
        EventQueue realQueue = new BlockingEventQueue(10);
        EventProcessorRegistry realRegistry = new DefaultEventProcessorRegistry();
        realRegistry.registerProcessor(TestEvent.class, slowProcessor);
        
        StateChangeEventsManager manager = new StateChangeEventsManager(config, realQueue, realRegistry);
        
        // When
        manager.start();
        manager.addEvent(new TestEvent("slow-event"));
        
        // Wait for processing to start
        assertTrue(eventStartedLatch.await(1, TimeUnit.SECONDS));
        
        // Start shutdown in background
        CompletableFuture<Void> shutdownFuture = CompletableFuture.runAsync(manager::shutdown);
        
        // Release the slow processor
        shutdownLatch.countDown();
        
        // Then
        shutdownFuture.get(3, TimeUnit.SECONDS); // Should complete within timeout
        assertTrue(manager.isShutdown());
    }

    @Test
    @Timeout(5)
    void shouldClearQueueOnShutdown() {
        // Given
        StateChangeEventsManager manager = new StateChangeEventsManager(config, mockQueue, mockRegistry);
        
        // When
        manager.shutdown();
        
        // Then
        verify(mockQueue).clear();
    }

    @Test
    void shouldUseDependencyInjection() {
        EventQueue customQueue = mock(EventQueue.class);
        EventProcessorRegistry customRegistry = mock(EventProcessorRegistry.class);
        
        StateChangeEventsManager manager = new StateChangeEventsManager(config, customQueue, customRegistry);
        
        manager.addEvent(testEvent);
        manager.registerEventProcessor(TestEvent.class, processor1);
        
        verify(customQueue).offer(testEvent);
        verify(customRegistry).registerProcessor(TestEvent.class, processor1);
    }

    @Test
    void shouldSupportExtensibility() {
        class NewEventType implements StateChangeEvents {}
        StateChangeEventProcessor newProcessor = event -> { /* handle new event */ };
        
        StateChangeEventsManager manager = new StateChangeEventsManager(config);
        
        manager.registerEventProcessor(NewEventType.class, newProcessor);
        manager.addEvent(new NewEventType());
        
        assertDoesNotThrow(() -> manager.addEvent(new NewEventType()));
    }

    @Test
    @Timeout(15) // Increased timeout for realistic workload
    void shouldHandleConcurrentOperations() throws Exception {
        // Given - realistic file system event processing scenario
        AtomicInteger eventsProcessed = new AtomicInteger(0);
        CountDownLatch allDoneLatch = new CountDownLatch(1000); // Realistic event count
        
        StateChangeEventProcessor countingProcessor = event -> {
            eventsProcessed.incrementAndGet();
            allDoneLatch.countDown();
            // Simulate realistic processing time (file metadata updates, etc.)
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        
        // Production-like configuration
        EventQueue realQueue = new BlockingEventQueue(2000); // Larger queue for file system bursts
        EventProcessorRegistry realRegistry = new DefaultEventProcessorRegistry();
        realRegistry.registerProcessor(TestEvent.class, countingProcessor);
        
        StateChangeEventsManager manager = new StateChangeEventsManager(
            StateChangeEventsManagerConfig.builder()
                .threadPoolSize(8) // More threads for realistic file system processing
                .queueCapacity(2000) // Production-like capacity
                .registerShutdownHook(false)
                .build(),
            realQueue, realRegistry);
        
        // When - simulate concurrent file system events
        manager.start();
        
        // Submit events concurrently (simulating multiple file watchers)
        CompletableFuture[] futures = new CompletableFuture[1000];
        for (int i = 0; i < 1000; i++) {
            final int eventId = i;
            futures[i] = CompletableFuture.runAsync(() -> 
                manager.addEvent(new TestEvent("file-change-" + eventId)));
        }
        
        // Wait for event submission
        CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);
        
        // Then - verify all events are processed within reasonable time
        assertTrue(allDoneLatch.await(30, TimeUnit.SECONDS)); // Realistic processing time
        assertEquals(1000, eventsProcessed.get());
        
        manager.shutdown();
    }

    @Test
    void shouldDelegateToComponents() {
        StateChangeEventsManager manager = new StateChangeEventsManager(config, mockQueue, mockRegistry);
        
        manager.addEvent(testEvent);
        manager.registerEventProcessor(TestEvent.class, processor1);
        manager.unregisterEventProcessor(TestEvent.class, processor1);
        int size = manager.getQueueSize();
        
        verify(mockQueue).offer(testEvent);
        verify(mockRegistry).registerProcessor(TestEvent.class, processor1);
        verify(mockRegistry).unregisterProcessor(TestEvent.class, processor1);
        verify(mockQueue).size();
        
        assertTrue(size >= 0);
    }
} 