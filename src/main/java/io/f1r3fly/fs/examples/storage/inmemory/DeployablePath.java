package io.f1r3fly.fs.examples.storage.inmemory;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import rhoapi.RhoTypes;

import java.util.List;

public abstract class DeployablePath {

    protected DeployDispatcher deployDispatcher;

    protected void enqueueMutation(String rholangExpression) {
        DeployDispatcher.Deployment deployment = new DeployDispatcher.Deployment(
            rholangExpression,
            true,
            F1r3flyApi.RHOLANG
        );

        this.deployDispatcher.enqueueDeploy(deployment);
    }


}
