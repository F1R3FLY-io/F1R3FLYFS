package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.F1r3flyDeployError;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.MemoryDirectory;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

import java.nio.file.Path;
import java.util.List;

public class F1r3flyFSTokenization {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFSTokenization.class);

    public static void initializeTokenDirectory(Path mountPoint, MemoryDirectory rootDirectory) {
        String newDirPath = mountPoint.toString() + "/tokens";
        rootDirectory.mkdir(getLastComponent(newDirPath), true);
        LOGGER.debug("Created tokens folder");
    }

    public static void initializeBalance(F1r3flyApi f1R3FlyApi, DeployDispatcher deployDispatcher) {
        // using hardcoded address for testing
        String rholangExpression = RholangExpressionConstructor.checkBalanceRho("11112ZM9yrfaTrzCCbKjPbxBncjNCkMFsPqtcLFvhBf4Kqx6rpir2w");

        // define rho code
        DeployDispatcher.Deployment deployment = new DeployDispatcher.Deployment(
            rholangExpression,
            true,
            F1r3flyApi.RHOLANG
        );

        try {
            deployDispatcher.waitOnEmptyQueue();
            deployDispatcher.enqueueDeploy(deployment);
            LOGGER.debug("Deployment successful.");
        } catch (Exception e) {
            LOGGER.error("Deployment failed with an exception.");
        }

        // retrieve balance data from the block if deployment was successful
        try {
            deployDispatcher.waitOnEmptyQueue();

            List<RhoTypes.Par> balanceData = f1R3FlyApi.findDataByName("balance");
            LOGGER.debug("Balance data retrieved for address: {}", balanceData);

            String balance = extractBalanceFromData(balanceData);
            LOGGER.debug("Balance: {}", balance);

        } catch (NoDataByPath e) {
            LOGGER.error("No balance data found in block for address: {}", e.getMessage());
        }

        // initialize the previously created tokens folder with token files depending on the
    }

    public static String extractBalanceFromData(List<RhoTypes.Par> balanceData) {
        // Assuming the balance is stored as a GInt in the response
        if (balanceData != null && !balanceData.isEmpty()) {
            RhoTypes.Par par = balanceData.get(0);
            if (par.getExprsCount() > 0 && par.getExprs(0).hasGInt()) {
                return String.valueOf(par.getExprs(0).getGInt());
            }
        }
        return "Balance data not found.";
    }

    private static String getLastComponent(String path) {
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf("/") + 1);
    }
}
