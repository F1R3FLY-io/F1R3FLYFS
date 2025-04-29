package io.f1r3fly.fs.examples;

import casper.DeployServiceCommon;
import io.f1r3fly.fs.SuccessCodes;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.NoDataByPath;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.*;
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

    private MemoryDirectory tokenDirectory;
    private final F1r3flyFS f1r3flyFS;
    private final DeployDispatcher deployDispatcher;
    private final F1r3flyApi f1R3FlyApi;

    public F1r3flyFSTokenization(F1r3flyFS f1r3flyFS, F1r3flyApi f1R3FlyApi, DeployDispatcher deployDispatcher) {
        this.f1r3flyFS = f1r3flyFS;
        this.deployDispatcher = deployDispatcher;
        this.f1R3FlyApi = f1R3FlyApi;
    }

    public void initializeTokenDirectory(MemoryDirectory rootDirectory) {
        tokenDirectory = rootDirectory.mkdir(".tokens", false);
        LOGGER.debug("Created tokens folder");
    }

    public void initializeBalance(String itselfAddress) {
        String rholangExpression = RholangExpressionConstructor.checkBalanceRho(itselfAddress);

        RhoTypes.Expr balanceData = f1R3FlyApi.exploratoryDeploy(rholangExpression);

        if (balanceData == null) {
            LOGGER.error("No balance data found in block");
            return;
        }

        if (!balanceData.hasGInt()) {
            LOGGER.error("Wrong data type: {}", balanceData);
            return;
        }

        long balance = balanceData.getGInt();
        LOGGER.debug("Balance: {}", balance);

        Map<Long, Integer> tokenMap = splitBalance(balance);

        LOGGER.info("Token Breakdown:");
        for (Map.Entry<Long, Integer> entry : tokenMap.entrySet()) {
            LOGGER.debug("{} tokens of {}", entry.getValue(), entry.getKey());
            int amount = entry.getValue();

            createTokens(amount, entry.getKey());
        }

        LOGGER.debug("Created tokens successfully! (initialized tokens folder)");

        // create directories for other addresses
        createRavAddressDirectories(itselfAddress);
        LOGGER.debug("Created directories for other addresses");
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

    private void createTokens(int amount, long value) {
        for (int i = 0; i < amount; i++) {
            String fileName = value + "-REV." + i + ".token";

            TokenFile tokenFile = new TokenFile(f1r3flyFS.getMountName(), fileName, tokenDirectory, deployDispatcher, false, value);

            tokenDirectory.add(tokenFile, false);
            LOGGER.debug("Created token files amount: {} with name: {}", amount, value);
        }

    }

    private List<String> parseRavAddressesFromGenesisBlock() {
        List<DeployServiceCommon.DeployInfo> deploys = f1R3FlyApi.getGenesisBlock().getDeploysList();

        DeployServiceCommon.DeployInfo tokenInitializeDeploy =
            deploys.stream().filter((deployInfo1 ->
                deployInfo1.getTerm().contains("revVaultInitCh"))).findFirst().orElseThrow();

        String regex = "\\\"(1111[A-Za-z0-9]+)\\\"";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(tokenInitializeDeploy.getTerm());

        List<String> ravAddresses = new java.util.ArrayList<>();
        while (matcher.find()) {
            ravAddresses.add(matcher.group(1));
        }

        return ravAddresses;
    }

    private void createRavAddressDirectories(String itselfAddress) {
        List<String> ravAddresses = parseRavAddressesFromGenesisBlock();

        // remove itself address from the list
        ravAddresses.remove(itselfAddress);

        for (String address : ravAddresses) {
            WalletDirectory directory = new WalletDirectory(f1r3flyFS.getMountName(), address, tokenDirectory, deployDispatcher, address);
            tokenDirectory.add(directory, false);
        }
    }
}
