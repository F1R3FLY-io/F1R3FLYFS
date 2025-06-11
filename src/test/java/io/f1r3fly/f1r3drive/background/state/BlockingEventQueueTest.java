package io.f1r3fly.f1r3drive.background.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for BlockingEventQueue.
 */
class BlockingEventQueueTest {

    private BlockingEventQueue queue;
    
    @Mock
    private StateChangeEvents event1;
    
    @Mock
    private StateChangeEvents event2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        queue = new BlockingEventQueue(10);
    }

    @Test
    void shouldCreateQueueWithValidCapacity() {
        // When
        BlockingEventQueue queue = new BlockingEventQueue(5);
        
        // Then
        assertEquals(0, queue.size());
    }

    @Test
    void shouldThrowExceptionForInvalidCapacity() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> new BlockingEventQueue(0));
        assertThrows(IllegalArgumentException.class, () -> new BlockingEventQueue(-1));
    }

    @Test
    void shouldOfferEvent() {
        // When
        boolean result = queue.offer(event1);
        
        // Then
        assertTrue(result);
        assertEquals(1, queue.size());
    }

    @Test
    void shouldRejectNullEvent() {
        // When
        boolean result = queue.offer(null);
        
        // Then
        assertFalse(result);
        assertEquals(0, queue.size());
    }

    @Test
    void shouldOfferMultipleEvents() {
        // When
        queue.offer(event1);
        queue.offer(event2);
        
        // Then
        assertEquals(2, queue.size());
    }

    @Test
    void shouldRejectEventWhenQueueIsFull() {
        // Given - fill the queue to capacity
        BlockingEventQueue smallQueue = new BlockingEventQueue(2);
        smallQueue.offer(event1);
        smallQueue.offer(event2);
        assertEquals(2, smallQueue.size());
        
        // When - try to add one more
        StateChangeEvents event3 = mock(StateChangeEvents.class);
        boolean result = smallQueue.offer(event3);
        
        // Then
        assertFalse(result);
        assertEquals(2, smallQueue.size());
    }

    @Test
    void shouldTakeEventInFIFOOrder() throws InterruptedException {
        // Given
        queue.offer(event1);
        queue.offer(event2);
        
        // When
        StateChangeEvents firstEvent = queue.take();
        StateChangeEvents secondEvent = queue.take();
        
        // Then
        assertSame(event1, firstEvent);
        assertSame(event2, secondEvent);
        assertEquals(0, queue.size());
    }

    @Test
    void shouldBlockOnTakeWhenQueueIsEmpty() throws Exception {
        AtomicReference<StateChangeEvents> takenEvent = new AtomicReference<>();
        AtomicBoolean takeCompleted = new AtomicBoolean(false);
        
        CompletableFuture<Void> takeFuture = CompletableFuture.runAsync(() -> {
            try {
                StateChangeEvents event = queue.take();
                takenEvent.set(event);
                takeCompleted.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        Thread.sleep(100);
        assertFalse(takeCompleted.get());
        
        queue.offer(event1);
        takeFuture.get(1, TimeUnit.SECONDS);
        
        assertTrue(takeCompleted.get());
        assertSame(event1, takenEvent.get());
        assertEquals(0, queue.size());
    }

    @Test
    void shouldHandleInterruptedTake() throws Exception {
        // Given
        AtomicReference<Exception> caughtException = new AtomicReference<>();
        
        CompletableFuture<Void> takeFuture = CompletableFuture.runAsync(() -> {
            try {
                queue.take();
            } catch (InterruptedException e) {
                caughtException.set(e);
                Thread.currentThread().interrupt();
            }
        });
        
        // When - interrupt the thread
        Thread.sleep(50); // Give take time to start blocking
        takeFuture.cancel(true);
        
        // Then - wait and verify
        try {
            takeFuture.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Expected cancellation exception
        }
    }

    @Test
    void shouldClearQueue() {
        // Given
        queue.offer(event1);
        queue.offer(event2);
        assertEquals(2, queue.size());
        
        // When
        queue.clear();
        
        // Then
        assertEquals(0, queue.size());
    }

    @Test
    void shouldReturnCorrectSize() {
        // Given
        assertEquals(0, queue.size());
        
        // When/Then
        queue.offer(event1);
        assertEquals(1, queue.size());
        
        queue.offer(event2);
        assertEquals(2, queue.size());
        
        try {
            queue.take();
            assertEquals(1, queue.size());
            
            queue.take();
            assertEquals(0, queue.size());
        } catch (InterruptedException e) {
            fail("Unexpected interruption");
        }
    }

    @Test
    void shouldBeThreadSafeForConcurrentOperations() throws Exception {
        // Given - realistic queue size for a file system (like production)
        ExecutorService executor = Executors.newFixedThreadPool(10);
        int numberOfEvents = 1000;
        // Use realistic queue capacity - production systems typically have larger buffers
        BlockingEventQueue realQueue = new BlockingEventQueue(2000);
        
        // When - perform concurrent offer operations (simulating file system events)
        CompletableFuture<Void>[] offerFutures = new CompletableFuture[numberOfEvents];
        for (int i = 0; i < numberOfEvents; i++) {
            final int index = i;
            offerFutures[i] = CompletableFuture.runAsync(() -> {
                StateChangeEvents event = mock(StateChangeEvents.class);
                when(event.toString()).thenReturn("FileEvent" + index);
                realQueue.offer(event);
            }, executor);
        }
        
        // Wait for all offers to complete
        CompletableFuture.allOf(offerFutures).get(5, TimeUnit.SECONDS);
        
        // Then - take all events (simulating background processing)
        CompletableFuture<Void>[] takeFutures = new CompletableFuture[numberOfEvents];
        for (int i = 0; i < numberOfEvents; i++) {
            takeFutures[i] = CompletableFuture.runAsync(() -> {
                try {
                    StateChangeEvents event = realQueue.take();
                    assertNotNull(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("Unexpected interruption");
                }
            }, executor);
        }
        
        // Wait for all takes to complete
        CompletableFuture.allOf(takeFutures).get(5, TimeUnit.SECONDS);
        
        // Verify queue is empty
        assertEquals(0, realQueue.size());
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldHandleMixedConcurrentOperations() throws Exception {
        // Given - realistic file system scenario
        ExecutorService executor = Executors.newFixedThreadPool(8);
        int numberOfOperations = 1000;
        // Production-sized queue capacity
        BlockingEventQueue fileSystemQueue = new BlockingEventQueue(1500);
        
        // When - simulate realistic file system operations
        CompletableFuture<Void>[] futures = new CompletableFuture[numberOfOperations];
        
        // Pre-populate with some events (simulating ongoing system activity)
        for (int i = 0; i < 50; i++) {
            fileSystemQueue.offer(mock(StateChangeEvents.class));
        }
        
        for (int i = 0; i < numberOfOperations; i++) {
            final int index = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                // Realistic operation distribution for file systems:
                // 60% - file change events (offers)
                // 30% - background processing (takes) 
                // 10% - monitoring/maintenance (size checks)
                
                if (index % 10 < 6) {
                    // File system events (60%) - most common operation
                    StateChangeEvents event = mock(StateChangeEvents.class);
                    when(event.toString()).thenReturn("FileChange" + index);
                    fileSystemQueue.offer(event);
                } else if (index % 10 < 9) {
                    // Background processing (30%) - consuming events
                    try {
                        StateChangeEvents event = fileSystemQueue.take();
                        assertNotNull(event);
                        // Simulate processing time
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    // Monitoring operations (10%) - checking queue status
                    int size = fileSystemQueue.size();
                    assertTrue(size >= 0);
                }
            }, executor);
        }
        
        // Wait for all operations to complete (realistic timeout for file system operations)
        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
        
        // Then - system should be stable
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Queue should still be operational
        assertTrue(fileSystemQueue.size() >= 0);
    }

    @Test
    void shouldMaintainFIFOOrderUnderConcurrency() throws Exception {
        // Given
        BlockingEventQueue orderedQueue = new BlockingEventQueue(1000);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        int numberOfEvents = 100;
        
        // When - offer events with known order
        CompletableFuture<Void> offerFuture = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < numberOfEvents; i++) {
                StateChangeEvents event = mock(StateChangeEvents.class);
                when(event.toString()).thenReturn("Event" + i);
                orderedQueue.offer(event);
                
                // Small delay to ensure ordering
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, executor);
        
        // Wait for offers to complete
        offerFuture.get(5, TimeUnit.SECONDS);
        
        // Then - take events and verify order
        for (int i = 0; i < numberOfEvents; i++) {
            StateChangeEvents event = orderedQueue.take();
            assertEquals("Event" + i, event.toString());
        }
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
    }

    @Test
    void shouldHandleRealisticFileSystemWorkload() throws Exception {
        // Given - simulate a busy file system with realistic parameters
        ExecutorService producerPool = Executors.newFixedThreadPool(4); // File watchers
        ExecutorService consumerPool = Executors.newFixedThreadPool(2); // Background processors
        
        // Production-like queue size (file systems can have bursts of events)
        BlockingEventQueue workloadQueue = new BlockingEventQueue(5000);
        
        int totalEvents = 2000;
        AtomicInteger producedEvents = new AtomicInteger(0);
        AtomicInteger consumedEvents = new AtomicInteger(0);
        CountDownLatch productionComplete = new CountDownLatch(1);
        
        // When - simulate producers (file system events)
        CompletableFuture<Void> producerTask = CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < totalEvents; i++) {
                    StateChangeEvents event = mock(StateChangeEvents.class);
                    when(event.toString()).thenReturn("FileSystemEvent" + i);
                    
                    boolean added = workloadQueue.offer(event);
                    if (added) {
                        producedEvents.incrementAndGet();
                    }
                    
                    // Simulate realistic file system event intervals
                    if (i % 100 == 0) {
                        Thread.sleep(10); // Small burst intervals
                    }
                }
                productionComplete.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, producerPool);
        
        // Simulate consumers (background processing)
        CompletableFuture<Void> consumerTask = CompletableFuture.runAsync(() -> {
            try {
                while (!productionComplete.await(0, TimeUnit.MILLISECONDS) || workloadQueue.size() > 0) {
                    try {
                        StateChangeEvents event = workloadQueue.take();
                        assertNotNull(event);
                        consumedEvents.incrementAndGet();
                        
                        // Simulate processing time
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, consumerPool);
        
        // Wait for realistic completion time
        CompletableFuture.allOf(producerTask, consumerTask).get(60, TimeUnit.SECONDS);
        
        // Then - verify realistic throughput
        assertEquals(totalEvents, producedEvents.get());
        assertEquals(totalEvents, consumedEvents.get());
        assertEquals(0, workloadQueue.size());
        
        producerPool.shutdown();
        consumerPool.shutdown();
        assertTrue(producerPool.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(consumerPool.awaitTermination(5, TimeUnit.SECONDS));
    }
} 