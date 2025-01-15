package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.examples.storage.background.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.grcp.client.F1r3flyApi;
import scala.collection.Map;
import scala.collection.mutable.HashMap;

import java.nio.file.Path;

public class Config {

    public final String clientHost;
    public final int clientPort;
    public final String mountName;
    public final Path mountPoint;
    public final DeployDispatcher deployDispatcher;
    public final F1r3flyApi f1r3flyAPI;

    public Config(String clientHost, int clientPort, String mountName, Path mountPoint, DeployDispatcher deployDispatcher, F1r3flyApi f1r3flyAPI) {
        this.clientHost = clientHost;
        this.clientPort = clientPort;
        this.mountName = mountName;
        this.mountPoint = mountPoint;
        this.deployDispatcher = deployDispatcher;
        this.f1r3flyAPI = f1r3flyAPI;
    }

}
