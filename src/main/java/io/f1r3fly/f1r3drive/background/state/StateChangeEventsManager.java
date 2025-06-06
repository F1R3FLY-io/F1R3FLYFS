package io.f1r3fly.f1r3drive.background.state;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Refactored StateChangeEventsManager following SOLID principles.
 * 
 * Now follows:
 * - Single Responsibility: Only coordinates between components
 * - Open/Closed: Extensible through interfaces
 * - Dependency Inversion: Depends on abstractions, not concretions
 * - Interface Segregation: Uses focused interfaces
 */
public class StateChangeEventsManager {

    private static final Logger logger = Logger.getLogger(StateChangeEventsManager.class.getName());

    private final EventQueue eventQueue;
    private final EventProcessorRegistry processorRegistry;
    private final EventProcessor eventProcessor;
    private final ExecutorService eventProcessingThreadPool;
    private final Thread eventDispatcherThread;
    private final StateChangeEventsManagerConfig config;
    private volatile boolean shutdown = false;

    /**
     * Create with default configuration.
     */
    public StateChangeEventsManager() {
        this(StateChangeEventsManagerConfig.defaultConfig());
    }

    /**
     * Create with custom configuration.
     * Follows Dependency Inversion by accepting configuration.
     */
    public StateChangeEventsManager(StateChangeEventsManagerConfig config) {
        this(config, 
             new BlockingEventQueue(config.getQueueCapacity()),
             new DefaultEventProcessorRegistry());
    }
    
    /**
     * Create with custom dependencies (for testing and flexibility).
     * Follows Dependency Injection pattern.
     */
    public StateChangeEventsManager(StateChangeEventsManagerConfig config,
                                  EventQueue eventQueue,
                                  EventProcessorRegistry processorRegistry) {
        if (config == null || eventQueue == null || processorRegistry == null) {
            throw new IllegalArgumentException("All dependencies must be non-null");
        }
        
        this.config = config;
        this.eventQueue = eventQueue;
        this.processorRegistry = processorRegistry;
        this.eventProcessor = new DefaultEventProcessor(processorRegistry);
        
        // Create a thread pool for processing events
        this.eventProcessingThreadPool = Executors.newFixedThreadPool(
            config.getThreadPoolSize(), r -> {
                Thread t = new Thread(r, "StateChangeEventProcessor");
                t.setDaemon(true); // Run as daemon thread in background
                return t;
            });

        // Create a dispatcher thread that takes events from queue and submits them to thread pool
        this.eventDispatcherThread = new Thread(this::dispatchEvents, "StateChangeEventDispatcher");
        eventDispatcherThread.setDaemon(true); // Run as daemon thread in background

        // Register shutdown hook if configured
        if (config.shouldRegisterShutdownHook()) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        }
    }
    
    /**
     * Dispatcher loop - extracted for better readability.
     */
    private void dispatchEvents() {
        while (!Thread.currentThread().isInterrupted() && !shutdown) {
            try {
                StateChangeEvents event = eventQueue.take();
                // Submit event processing to thread pool
                eventProcessingThreadPool.submit(() -> {
                    try {
                        eventProcessor.processEvent(event);
                    } catch (Throwable e) {
                        // Log the error and continue processing next event
                        logger.log(Level.SEVERE, "Failed to process event: " + event.getClass().getSimpleName(), e);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void start() {
        eventDispatcherThread.start();
    }

    public void shutdown() {
        this.shutdown = true;
        this.eventDispatcherThread.interrupt();
        
        // Shutdown thread pool
        eventProcessingThreadPool.shutdown();
        
        try {
            // Wait for dispatcher thread to finish
            this.eventDispatcherThread.join(2000);
            
            // Wait for thread pool to terminate
            long timeoutMs = config.getShutdownTimeoutMs();
            if (!eventProcessingThreadPool.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                eventProcessingThreadPool.shutdownNow();
                // Wait a bit more for tasks to respond to being cancelled
                if (!eventProcessingThreadPool.awaitTermination(2000, TimeUnit.MILLISECONDS)) {
                    logger.warning("Thread pool did not terminate gracefully");
                }
            }
        } catch (InterruptedException e) {
            eventProcessingThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        this.eventQueue.clear();
    }

    /**
     * Add an event to be processed.
     * 
     * @param event the event to add
     * @return true if event was added successfully, false if shutdown or queue full
     */
    public boolean addEvent(StateChangeEvents event) {
        if (shutdown || event == null) {
            return false;
        }
        return eventQueue.offer(event);
    }

    /**
     * Register a processor for a specific event type.
     * Delegates to the processor registry.
     */
    public void registerEventProcessor(Class<? extends StateChangeEvents> eventClass, 
                                     StateChangeEventProcessor processor) {
        processorRegistry.registerProcessor(eventClass, processor);
    }
    
    /**
     * Unregister a processor for a specific event type.
     * Delegates to the processor registry.
     */
    public void unregisterEventProcessor(Class<? extends StateChangeEvents> eventClass, 
                                       StateChangeEventProcessor processor) {
        processorRegistry.unregisterProcessor(eventClass, processor);
    }
    
    /**
     * Get the current size of the event queue.
     */
    public int getQueueSize() {
        return eventQueue.size();
    }
    
    /**
     * Check if the manager is shutdown.
     */
    public boolean isShutdown() {
        return shutdown;
    }
}