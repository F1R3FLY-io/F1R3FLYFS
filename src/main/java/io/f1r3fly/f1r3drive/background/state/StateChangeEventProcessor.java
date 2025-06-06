package io.f1r3fly.f1r3drive.background.state;

/**
 * Interface for processing state change events.
 * Follows Single Responsibility Principle by focusing only on event processing.
 */
public interface StateChangeEventProcessor {
    
    /**
     * Process a state change event.
     * 
     * @param event the event to process
     */
    void processEvent(StateChangeEvents event);
} 