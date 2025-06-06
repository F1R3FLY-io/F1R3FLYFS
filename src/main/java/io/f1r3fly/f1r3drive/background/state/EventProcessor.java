package io.f1r3fly.f1r3drive.background.state;

/**
 * Interface for processing events using registered processors.
 * Follows Single Responsibility Principle by focusing only on event processing logic.
 */
public interface EventProcessor {
    
    /**
     * Process an event by delegating to registered processors.
     * 
     * @param event the event to process
     */
    void processEvent(StateChangeEvents event);
} 