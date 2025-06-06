package io.f1r3fly.f1r3drive.background.state;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Blocking queue implementation for events.
 * Follows Single Responsibility Principle by only managing queue operations.
 */
public class BlockingEventQueue implements EventQueue {
    
    private final BlockingQueue<StateChangeEvents> queue;
    
    public BlockingEventQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }
    
    @Override
    public boolean offer(StateChangeEvents event) {
        if (event == null) {
            return false;
        }
        return queue.offer(event);
    }
    
    @Override
    public StateChangeEvents take() throws InterruptedException {
        return queue.take();
    }
    
    @Override
    public void clear() {
        queue.clear();
    }
    
    @Override
    public int size() {
        return queue.size();
    }
} 