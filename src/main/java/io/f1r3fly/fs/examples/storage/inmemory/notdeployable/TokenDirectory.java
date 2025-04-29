package io.f1r3fly.fs.examples.storage.inmemory.notdeployable;

import io.f1r3fly.fs.FuseFillDir;
import io.f1r3fly.fs.examples.ConfigStorage;
import io.f1r3fly.fs.examples.storage.DeployDispatcher;
import io.f1r3fly.fs.examples.storage.errors.F1r3flyFSError;
import io.f1r3fly.fs.examples.storage.errors.OperationNotPermitted;
import io.f1r3fly.fs.examples.storage.grcp.F1r3flyApi;
import io.f1r3fly.fs.examples.storage.inmemory.common.IDirectory;
import io.f1r3fly.fs.examples.storage.inmemory.common.IPath;
import io.f1r3fly.fs.examples.storage.rholang.RholangExpressionConstructor;
import io.f1r3fly.fs.struct.FileStat;
import io.f1r3fly.fs.struct.FuseContext;
import jnr.ffi.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rhoapi.RhoTypes;

import java.util.*;

public class TokenDirectory extends AbstractNotDeployablePath implements IDirectory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenDirectory.class);

    private final Set<IPath> children = new HashSet<>();

    private final F1r3flyApi f1r3flyApi;

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


    public TokenDirectory(String prefix, IDirectory parent, F1r3flyApi f1r3flyApi) {
        super(prefix, ".tokens", parent);
        this.f1r3flyApi = f1r3flyApi;
    }

    @Override
    public synchronized void addChild(IPath p) {
        children.add(p);
    }

    @Override
    public synchronized void deleteChild(IPath child) {
        children.remove(child);
    }

    @Override
    public void mkdir(String lastComponent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    public void mkfile(String lastComponent) throws OperationNotPermitted {
        throw OperationNotPermitted.instance;
    }

    @Override
    public void read(Pointer buf, FuseFillDir filler) {
        // delete all TokenFiles only
        children.removeIf(child -> child instanceof TokenFile);
        createTokenFiles(buf, filler);

        IDirectory.super.read(buf, filler);
    }

    private void createTokenFiles(Pointer buf, FuseFillDir filler) {
        long balance;
        try {
            balance = checkBalance();
        } catch (F1r3flyFSError e) {
            filler.apply(buf, "ERROR: " + e.getMessage(), null, 0);
            return;
        }

        Map<Long, Integer> tokenMap = splitBalance(balance);

       for (Map.Entry<Long, Integer> entry : tokenMap.entrySet()) {
            long denomination = entry.getKey();
            int amount = entry.getValue();

            addTokens(denomination, amount);
        }
    }

    private Map<Long, Integer> splitBalance(long balance) {
        Map<Long, Integer> tokenMap = new HashMap<>();

        for (long denom : denominations) {
            int count = (int) (balance / denom);
            if (count > 0) {
                tokenMap.put(denom, count);
                balance %= denom;
            }
        }
        return tokenMap;
    }


    private long checkBalance() throws F1r3flyFSError {
        String checkBalanceRho = RholangExpressionConstructor.checkBalanceRho(ConfigStorage.getRevAddress());
        RhoTypes.Expr expr = f1r3flyApi.exploratoryDeploy(checkBalanceRho);

        if (!expr.hasGInt()) {
            throw new F1r3flyFSError("Invalid balance data");
        }

        return expr.getGInt();
    }

    public void addTokens(long denomination, long N) {
        for (int i = 0; i < N; i++) {
            String tokenName = denomination + "-REV." + i + ".token";
            TokenFile tokenFile = new TokenFile(getPrefix(), tokenName, this, denomination);
            children.add(tokenFile);
        }
    }

    public void addWallet(String revAddress, DeployDispatcher deployDispatcher) {
        WalletDirectory walletDirectory =
            new WalletDirectory(getPrefix(),
                revAddress, // name matches revAddress
                this,
                revAddress,
                deployDispatcher); // deployDispatcher is used for transfering tokens. WalletDirectory is not deployable to the shard

        children.add(walletDirectory);
    }

    @Override
    public boolean isEmpty() {
        return children.isEmpty();
    }

    @Override
    public Set<IPath> getChildren() {
        return children;
    }

    @Override
    public void getAttr(FileStat stat, FuseContext fuseContext) {
        stat.st_mode.set(FileStat.S_IFDIR | 0777); // Read-only permissions
        stat.st_uid.set(fuseContext.uid.get());
        stat.st_gid.set(fuseContext.gid.get());
        stat.st_nlink.set(1);
    }
}
