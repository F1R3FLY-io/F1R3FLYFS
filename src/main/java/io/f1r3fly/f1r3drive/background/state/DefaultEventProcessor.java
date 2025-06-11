package io.f1r3fly.f1r3drive.background.state;

import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Default implementation of EventProcessor.
 * Follows Single Responsibility Principle by only handling event processing logic.
 */
public class DefaultEventProcessor implements EventProcessor {
    
    private static final Logger logger = Logger.getLogger(DefaultEventProcessor.class.getName());
    
    private final EventProcessorRegistry registry;
    
    public DefaultEventProcessor(EventProcessorRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("Registry cannot be null");
        }
        this.registry = registry;
    }
    
    @Override
    public void processEvent(StateChangeEvents event) {
        if (event == null) {
            return;
        }
        
        Set<StateChangeEventProcessor> processors = registry.getProcessors(event.getClass());
        
        for (StateChangeEventProcessor processor : processors) {
            try {
                processor.processEvent(event);
            } catch (Exception e) {
                // Log the error but continue processing with other processors
                logger.log(Level.SEVERE, 
                    "Failed to process event " + event.getClass().getSimpleName() + 
                    " with processor " + processor.getClass().getSimpleName(), e);
            }
        }
    }
} 