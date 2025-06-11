package io.f1r3fly.f1r3drive.background.state;

/**
 * Configuration for StateChangeEventsManager.
 * Follows Single Responsibility Principle by only holding configuration data.
 */
public class StateChangeEventsManagerConfig {
    
    private final int queueCapacity;
    private final int threadPoolSize;
    private final long shutdownTimeoutMs;
    private final boolean registerShutdownHook;
    
    private StateChangeEventsManagerConfig(Builder builder) {
        this.queueCapacity = builder.queueCapacity;
        this.threadPoolSize = builder.threadPoolSize;
        this.shutdownTimeoutMs = builder.shutdownTimeoutMs;
        this.registerShutdownHook = builder.registerShutdownHook;
    }
    
    public int getQueueCapacity() {
        return queueCapacity;
    }
    
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
    
    public long getShutdownTimeoutMs() {
        return shutdownTimeoutMs;
    }
    
    public boolean shouldRegisterShutdownHook() {
        return registerShutdownHook;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static StateChangeEventsManagerConfig defaultConfig() {
        return builder().build();
    }
    
    public static class Builder {
        private int queueCapacity = 1000;
        private int threadPoolSize = Runtime.getRuntime().availableProcessors();
        private long shutdownTimeoutMs = 5000;
        private boolean registerShutdownHook = true;
        
        public Builder queueCapacity(int queueCapacity) {
            if (queueCapacity <= 0) {
                throw new IllegalArgumentException("Queue capacity must be positive");
            }
            this.queueCapacity = queueCapacity;
            return this;
        }
        
        public Builder threadPoolSize(int threadPoolSize) {
            if (threadPoolSize <= 0) {
                throw new IllegalArgumentException("Thread pool size must be positive");
            }
            this.threadPoolSize = threadPoolSize;
            return this;
        }
        
        public Builder shutdownTimeoutMs(long shutdownTimeoutMs) {
            if (shutdownTimeoutMs < 0) {
                throw new IllegalArgumentException("Shutdown timeout cannot be negative");
            }
            this.shutdownTimeoutMs = shutdownTimeoutMs;
            return this;
        }
        
        public Builder registerShutdownHook(boolean registerShutdownHook) {
            this.registerShutdownHook = registerShutdownHook;
            return this;
        }
        
        public StateChangeEventsManagerConfig build() {
            return new StateChangeEventsManagerConfig(this);
        }
    }
} 