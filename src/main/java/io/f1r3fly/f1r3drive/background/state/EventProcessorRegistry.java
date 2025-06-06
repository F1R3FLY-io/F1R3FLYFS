package io.f1r3fly.f1r3drive.background.state;

import java.util.Set;

/**
 * Registry for managing event processors.
 * Follows Interface Segregation Principle by providing focused interface.
 */
public interface EventProcessorRegistry {
    
    /**
     * Register a processor for a specific event type.
     */
    void registerProcessor(Class<? extends StateChangeEvents> eventClass, 
                          StateChangeEventProcessor processor);
    
    /**
     * Get all processors for a specific event type.
     */
    Set<StateChangeEventProcessor> getProcessors(Class<? extends StateChangeEvents> eventClass);
    
    /**
     * Remove a processor for a specific event type.
     */
    void unregisterProcessor(Class<? extends StateChangeEvents> eventClass, 
                           StateChangeEventProcessor processor);
} 