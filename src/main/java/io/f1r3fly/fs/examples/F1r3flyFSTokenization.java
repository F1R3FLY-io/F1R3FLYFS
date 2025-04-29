package io.f1r3fly.fs.examples;

import casper.DeployServiceCommon;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.deployable.InMemoryDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.notdeployable.TokenDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class F1r3flyFSTokenization {

    private static final Logger LOGGER = LoggerFactory.getLogger(F1r3flyFSTokenization.class);

    public static void initializeTokenDirectory(InMemoryDirectory rootDirectory, DeployDispatcher deployDispatcher) {

        TokenDirectory tokenDirectory = new TokenDirectory(rootDirectory.getPrefix(), rootDirectory, deployDispatcher.getF1R3FlyApi());
        rootDirectory.addChild(tokenDirectory);
        LOGGER.debug("Created tokens folder");

        createRavAddressDirectories(ConfigStorage.getRevAddress(), tokenDirectory, deployDispatcher);
        LOGGER.debug("Created Rav address folder");
    }

    private static List<String> parseRavAddressesFromGenesisBlock(F1r3flyApi f1R3FlyApi) {
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

    private static void createRavAddressDirectories(String itselfAddress, TokenDirectory tokenDirectory, DeployDispatcher deployDispatcher) {
        List<String> ravAddresses = parseRavAddressesFromGenesisBlock(deployDispatcher.getF1R3FlyApi());

        // remove itself address from the list
        ravAddresses.remove(itselfAddress);

        LOGGER.debug("Addresses found in genesis block: {}", ravAddresses);

        for (String address : ravAddresses) {
            tokenDirectory.addWallet(address, deployDispatcher);
        }
    }
}
