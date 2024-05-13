package io.f1r3fly.fs.examples.storage;

import io.f1r3fly.fs.examples.storage.errors.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface FSStorage {

    /**
     * type as a string and a size. Size could be 0 if type is dir
     */
    record TypeAndSize(String type, long size) {
    }

    record OperationResult<Payload>(Payload payload, @NotNull String blockHash) {
    }

    // COMMON OPERATIONS

    // Size could be zero if type is dir
    OperationResult<TypeAndSize> getTypeAndSize(
        @NotNull String path,
        @NotNull String blockHash) throws NoDataByPath;

    OperationResult<Void> rename(
        @NotNull String oldPath,
        @NotNull String newPath,
        @NotNull String blockHash) throws NoDataByPath, F1r3flyDeployError, DirectoryNotFound, PathIsNotADirectory, DirectoryIsNotEmpty, AlreadyExists;

    // FILE OPERATIONS

    OperationResult<Void> createFile(
        @NotNull String path,
        @NotNull String content,
        @NotNull long size,
        @NotNull String blockHash) throws F1r3flyDeployError;

    OperationResult<Void> appendFile(
        @NotNull String path,
        @NotNull String content,
        @NotNull long size,
        @NotNull String blockHash) throws F1r3flyDeployError;

    OperationResult<String> readFile(
        @NotNull String path,
        @NotNull String blockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile;

    OperationResult<String> executeFile(
        @NotNull String path,
        @NotNull String blockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile;

    OperationResult<Void> deleteFile(
        @NotNull String path,
        @NotNull String blockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile;

    // DIRECTORY OPERATIONS

    OperationResult<Void> createDir(
        @NotNull String path,
        @NotNull String blockHash) throws NoDataByPath, F1r3flyDeployError;

    OperationResult<Set<String>> readDir(
        @NotNull String path,
        @NotNull String blockHash) throws NoDataByPath, DirectoryNotFound, PathIsNotADirectory;

    OperationResult<Void> deleteDir(
        @NotNull String path,
        @NotNull String blockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError, DirectoryIsNotEmpty;

    OperationResult<Void> addToParent(
        @NotNull String path,
        @NotNull String blockHash) throws PathIsNotADirectory, F1r3flyDeployError, DirectoryNotFound;

    OperationResult<Void> removeFromParent(
        @NotNull String path,
        @NotNull String blockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError;

}
