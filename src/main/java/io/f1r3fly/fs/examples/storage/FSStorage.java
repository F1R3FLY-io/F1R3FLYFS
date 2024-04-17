package io.f1r3fly.fs.examples.storage;

import io.f1r3fly.fs.examples.storage.errors.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public interface FSStorage {

    /** type as a string and a size. Size could be 0 if type is dir */
    record TypeAndSize(String type, long size){} ;

    record OperationResult<Payload>(Payload payload, @NotNull String blockHash){} ;

    // Size could be zeo if type is dir
    OperationResult<TypeAndSize> getTypeAndSize(@NotNull String path, @NotNull String blockHash) throws NoDataByPath;

    OperationResult<Void> saveFile(String path, String content, String blockHash) throws F1r3flyDeployError, NoDataByPath, InvalidFSStructure, DirectoryNotFound;
    OperationResult<String> readFile(String path, String blockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile;
    OperationResult<Void> deleteFile(String path, String blockHash) throws NoDataByPath, F1r3flyDeployError, PathIsNotAFile;

    OperationResult<Void> createDir(String path, String blockHash) throws NoDataByPath, F1r3flyDeployError;
    OperationResult<Set<String>> readDir(String path, String blockHash) throws NoDataByPath, DirectoryNotFound, PathIsNotADirectory;
    OperationResult<Void> deleteDir(String path, String blockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError, DirectoryIsNotEmpty;

    OperationResult<Void> addToParent(String path, String blockHash) throws PathIsNotADirectory, F1r3flyDeployError, DirectoryNotFound;
    OperationResult<Void> removeFromParent(String path, String blockHash) throws PathIsNotADirectory, DirectoryNotFound, F1r3flyDeployError;

}
