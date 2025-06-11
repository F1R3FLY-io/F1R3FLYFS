package io.f1r3fly.f1r3drive.filesystem;

import io.f1r3fly.f1r3drive.filesystem.FileSystemAction;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Context for tracking filesystem operations across components
 */
public class OperationContext {
    
    private static final String OPERATION_ID_KEY = "operationId";
    private static final String ACTION_KEY = "action";
    private static final String PATH_KEY = "path";
    
    private static final ThreadLocal<OperationContext> CURRENT_CONTEXT = new ThreadLocal<>();
    
    private final String operationId;
    private final FileSystemAction action;
    private final String path;
    
    private OperationContext(FileSystemAction action, String path) {
        this.operationId = UUID.randomUUID().toString().substring(0, 8);
        this.action = action;
        this.path = path;
    }
    
    /**
     * Start a new operation context and set it in MDC for logging
     */
    public static OperationContext start(FileSystemAction action, String path) {
        OperationContext context = new OperationContext(action, path);
        CURRENT_CONTEXT.set(context);
        
        // Set MDC for logging
        MDC.put(OPERATION_ID_KEY, context.operationId);
        MDC.put(ACTION_KEY, action.name());
        MDC.put(PATH_KEY, path);
        
        return context;
    }
    
    /**
     * Get the current operation context
     */
    public static OperationContext current() {
        return CURRENT_CONTEXT.get();
    }
    
    /**
     * End the current operation context and clear MDC
     */
    public static void end() {
        CURRENT_CONTEXT.remove();
        MDC.clear();
    }
    
    /**
     * Execute an operation with context
     */
    public static <T> T withContext(FileSystemAction action, String path, java.util.function.Supplier<T> operation) {
        OperationContext context = start(action, path);
        try {
            return operation.get();
        } finally {
            end();
        }
    }
    
    /**
     * Execute an operation with context (void version)
     */
    public static void withContext(FileSystemAction action, String path, Runnable operation) {
        OperationContext context = start(action, path);
        try {
            operation.run();
        } finally {
            end();
        }
    }
    
    public String getOperationId() {
        return operationId;
    }
    
    public FileSystemAction getAction() {
        return action;
    }
    
    public String getPath() {
        return path;
    }
    
    @Override
    public String toString() {
        return String.format("OperationContext{id='%s', action=%s, path='%s'}", 
                            operationId, action, path);
    }
} 