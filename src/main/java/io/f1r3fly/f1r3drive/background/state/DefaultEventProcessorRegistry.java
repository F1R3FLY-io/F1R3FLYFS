package io.f1r3fly.f1r3drive.background.state;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Thread-safe implementation of EventProcessorRegistry.
 * Follows Single Responsibility Principle by only managing processor registration.
 */
public class DefaultEventProcessorRegistry implements EventProcessorRegistry {
    
    private final Map<Class<? extends StateChangeEvents>, Set<StateChangeEventProcessor>> eventProcessors;
    
    public DefaultEventProcessorRegistry() {
        // Use thread-safe collections for concurrent access
        this.eventProcessors = new ConcurrentHashMap<>();
    }
    
    @Override
    public void registerProcessor(Class<? extends StateChangeEvents> eventClass, 
                                StateChangeEventProcessor processor) {
        if (eventClass == null || processor == null) {
            throw new IllegalArgumentException("Event class and processor cannot be null");
        }
        
        eventProcessors.computeIfAbsent(eventClass, k -> new CopyOnWriteArraySet<>())
                      .add(processor);
    }
    
    @Override
    public Set<StateChangeEventProcessor> getProcessors(Class<? extends StateChangeEvents> eventClass) {
        if (eventClass == null) {
            return Set.of();
        }
        
        Set<StateChangeEventProcessor> processors = eventProcessors.get(eventClass);
        return processors != null ? Set.copyOf(processors) : Set.of();
    }
    
    @Override
    public void unregisterProcessor(Class<? extends StateChangeEvents> eventClass, 
                                  StateChangeEventProcessor processor) {
        if (eventClass == null || processor == null) {
            return;
        }
        
        Set<StateChangeEventProcessor> processors = eventProcessors.get(eventClass);
        if (processors != null) {
            processors.remove(processor);
            // Clean up empty sets to prevent memory leaks
            if (processors.isEmpty()) {
                eventProcessors.remove(eventClass);
            }
        }
    }
} 