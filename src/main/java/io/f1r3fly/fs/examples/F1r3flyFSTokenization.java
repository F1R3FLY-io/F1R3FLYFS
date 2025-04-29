package io.f1r3fly.fs.examples;

import io.f1r3fly.fs.SuccessCodes;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.MemoryDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.MemoryPath;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FuseFileInfo;
import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class F1r3flyFSTokenization {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFSTokenization.class);

    private static final List<Long> denominations = Arrays.asList(
        1_000_000_000_000_000_000L, // 1 quintillion
        100_000_000_000_000_000L,   // 100 quadrillion
        10_000_000_000_000_000L,    // 10 quadrillion
        1_000_000_000_000_000L,     // 1 quadrillion
        100_000_000_000_000L,       // 100 trillion
        10_000_000_000_000L,        // 10 trillion
        1_000_000_000_000L,         // 1 trillion
        100_000_000_000L,           // 100 billion
        10_000_000_000L,            // 10 billion
        1_000_000_000L,             // 1 billion
        100_000_000L,               // 100 million
        10_000_000L,                // 10 million
        1_000_000L,                 // 1 million
        100_000L,                   // 100K
        10_000L,                    // 10K
        1_000L,                      // 1K
        100L,                        // 100
        10L,                         // 10
        1L                           // 1
    );

    private final Path mountPoint;
    private final MemoryDirectory rootDirectory;
    private final F1r3flyFS f1r3flyFS;
    private final DeployDispatcher deployDispatcher;

    public F1r3flyFSTokenization(Path mountPoint, MemoryDirectory rootDirectory, F1r3flyFS f1r3flyFS, DeployDispatcher deployDispatcher) {
        this.mountPoint = mountPoint;
        this.rootDirectory = rootDirectory;
        this.f1r3flyFS = f1r3flyFS;
        this.deployDispatcher = deployDispatcher;
    }

    public void initializeTokenDirectory() {
        String newDirPath = mountPoint.toString() + "/tokens";
        rootDirectory.mkdir(getLastComponent(newDirPath), true);
        LOGGER.debug("Created tokens folder");
    }

    public void initializeBalance(F1r3flyApi f1R3FlyApi, DeployDispatcher deployDispatcher) {
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

        // retrieve balance data from the block if deployment was successful\
        String balance = null;
        try {
            deployDispatcher.waitOnEmptyQueue();
            List<RhoTypes.Par> balanceData = f1R3FlyApi.findDataByName("balance");

            balance = extractBalanceFromData(balanceData);
            LOGGER.debug("Balance: {}", balance);
        } catch (NoDataByPath e) {
            LOGGER.error("No balance data found in block for address: {}", e.getMessage());
        }

        // calculate tokens and initialise them inside the tokens folder
        if(balance == null) {
            LOGGER.error("Balance is null");
            return;
        }

        long numberBalance = Long.parseLong(balance);
        Map<Long, Integer> tokenMap = splitBalance(numberBalance);

        LOGGER.debug("Token Breakdown:");
        for (Map.Entry<Long, Integer> entry : tokenMap.entrySet()) {
            LOGGER.debug("{} tokens of {}", entry.getValue(), entry.getKey());
            int amount = entry.getValue();

            createTokens(amount, entry.getKey().toString());
        }

        LOGGER.debug("Created tokens successfully! (initialized tokens folder)");
    }

    public String extractBalanceFromData(List<RhoTypes.Par> balanceData) {
        if (balanceData != null && !balanceData.isEmpty()) {
            RhoTypes.Par par = balanceData.get(0);
            if (par.getExprsCount() > 0 && par.getExprs(0).hasGInt()) {
                return String.valueOf(par.getExprs(0).getGInt());
            }
        }
        return null;
    }

    private String getLastComponent(String path) {
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            return "";
        }
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private Map<Long, Integer> splitBalance(long balance) {
        Map<Long, Integer> tokenMap = new LinkedHashMap<>();

        for (long denom : denominations) {
            int count = (int) (balance / denom);
            if (count > 0) {
                tokenMap.put(denom, count);
                balance %= denom;
            }
        }
        return tokenMap;
    }

    private void createTokens(int amount, String value) {
        String fileName = amount + "x_token" + "_" + value;
        String path = "/tokens" + "/" + fileName + ".f1r3flyToken";
        f1r3flyFS.create(path,  0666, null);

        LOGGER.debug("Created token files amount: {} with name: {}", amount, value);
    }
}
