package io.f1r3fly.f1r3drive.background.state;

/**
 * Interface for event queue operations.
 * Follows Dependency Inversion Principle by providing abstraction.
 */
public interface EventQueue {
    
    /**
     * Add an event to the queue.
     * 
     * @param event the event to add
     * @return true if event was added successfully, false otherwise
     */
    boolean offer(StateChangeEvents event);
    
    /**
     * Take an event from the queue, blocking if necessary.
     * 
     * @return the next event
     * @throws InterruptedException if interrupted while waiting
     */
    StateChangeEvents take() throws InterruptedException;
    
    /**
     * Clear all events from the queue.
     */
    void clear();
    
    /**
     * Get the current size of the queue.
     * 
     * @return number of events in queue
     */
    int size();
} 