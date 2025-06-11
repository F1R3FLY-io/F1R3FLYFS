package io.f1r3fly.f1r3drive.filesystem;

/**
 * Enumeration of all filesystem and blockchain operation types that can be traced.
 * This enum is used for operation context tracking and logging across the entire system.
 */
public enum FileSystemAction {
    
    // FUSE Operations - Operations initiated by the FUSE filesystem layer
    /** File creation via FUSE */
    FUSE_CREATE,
    /** Getting file/directory attributes */
    FUSE_GETATTR,
    /** Directory creation via FUSE */
    FUSE_MKDIR,
    /** File reading operations */
    FUSE_READ,
    /** Directory listing operations */
    FUSE_READDIR,
    /** File/directory renaming via FUSE */
    FUSE_RENAME,
    /** Directory removal via FUSE */
    FUSE_RMDIR,
    /** File truncation operations */
    FUSE_TRUNCATE,
    /** File deletion via FUSE */
    FUSE_UNLINK,
    /** File opening operations */
    FUSE_OPEN,
    /** File writing operations */
    FUSE_WRITE,
    /** File flushing operations */
    FUSE_FLUSH,
    
    // Blockchain Operations - Operations that result in blockchain transactions
    /** Token transfer between wallets */
    TOKEN_TRANSFER,
    /** File creation on blockchain */
    FILE_CREATE,
    /** Directory creation on blockchain */
    DIRECTORY_CREATE,
    /** File content updates on blockchain */
    FILE_UPDATE,
    /** Directory structure updates on blockchain */
    DIRECTORY_UPDATE,
    /** File deletion from blockchain */
    FILE_DELETE,
    /** File truncation operations on blockchain */
    FILE_TRUNCATE,
    /** Directory deletion from blockchain */
    DIRECTORY_DELETE,
    /** File renaming on blockchain */
    FILE_RENAME,
    /** Directory renaming on blockchain */
    DIRECTORY_RENAME,
    
    // Miscellaneous Operations
    /** Other operations not specifically categorized */
    OTHER;
    
    /**
     * Returns true if this action represents a FUSE-level operation.
     */
    public boolean isFuseOperation() {
        return name().startsWith("FUSE_");
    }
    
    /**
     * Returns true if this action represents a blockchain operation.
     */
    public boolean isBlockchainOperation() {
        return !isFuseOperation() && this != OTHER;
    }
    
    /**
     * Returns true if this action represents a file operation (as opposed to directory).
     */
    public boolean isFileOperation() {
        return name().contains("FILE") || 
               this == FUSE_CREATE || this == FUSE_READ || this == FUSE_WRITE || 
               this == FUSE_OPEN || this == FUSE_FLUSH || this == FUSE_UNLINK || 
               this == FUSE_TRUNCATE;
    }
    
    /**
     * Returns true if this action represents a directory operation.
     */
    public boolean isDirectoryOperation() {
        return name().contains("DIRECTORY") || 
               this == FUSE_MKDIR || this == FUSE_READDIR || this == FUSE_RMDIR;
    }
    
    /**
     * Returns true if this action represents a write/modify operation.
     */
    public boolean isWriteOperation() {
        return name().contains("CREATE") || name().contains("UPDATE") || 
               name().contains("DELETE") || name().contains("RENAME") || 
               name().contains("TRUNCATE") || name().contains("WRITE") ||
               this == TOKEN_TRANSFER;
    }
} 