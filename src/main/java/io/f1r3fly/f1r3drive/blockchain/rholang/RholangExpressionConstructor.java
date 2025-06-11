package io.f1r3fly.f1r3drive.blockchain.rholang;

import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import rhoapi.RhoTypes;

import java.util.*;
import java.util.stream.Collectors;

public class RholangExpressionConstructor {

    private static final String LIST_DELIMITER = ",";


    private static final String TYPE = "type";
    private static final String DIR_TYPE = "d";
    private static final String FILE_TYPE = "f";

    private static final String FIRST_CHUNK = "firstChunk";
    private static final String CHILDREN = "children";
    private static final String LAST_UPDATED = "lastUpdated";
    private static final String OTHER_CHUNKS = "otherChunks";

    /**
     * Represents a file or a folder
     *
     * @param type        "f" for file, "d" for directory
     * @param lastUpdated timestamp of the last update
     * @param firstChunk content of the file; null for a folder
     * @param children    list of children; null for a file
     * @param otherChunks map of sub channels; null for a folder
     */
    public record ChannelData(String type, long lastUpdated, byte[] firstChunk, Set<String> children, Map<Integer, String> otherChunks) {
        public boolean isFile() {
            return type.equals(FILE_TYPE);
        }

        public boolean isDir() {
            return type.equals(DIR_TYPE);
        }
    }

    public static String checkBalanceRho(String addr) {
        return new StringBuilder()
            .append("new return, rl(`rho:registry:lookup`), RevVaultCh, vaultCh in { ")
            .append("  rl!(`rho:rchain:revVault`, *RevVaultCh) | ")
            .append("  for (@(_, RevVault) <- RevVaultCh) { ")
            .append("    @RevVault!(\"findOrCreate\", \"")
            .append(addr) // insert balance address
            .append("\", *vaultCh) | ")
            .append("    for (@maybeVault <- vaultCh) { ")
            .append("      match maybeVault { ")
            .append("        (true, vault) => @vault!(\"balance\", *return) ")
            .append("        (false, err) => return!(err) ")
            .append("      } ")
            .append("    } ")
            .append("  } ")
            .append("}")
            .toString();
    }

    //** Creates a chanel with a file */
    public static String sendEmptyFileIntoNewChanel(String channelName) {
        // output looks like: @"path"!({"type":"f","firstChunk":[]}, "otherChunks":{}, "lastUpdated":123})
        return new StringBuilder()
            .append("@\"")
            .append(channelName)
            .append("\"!({\"")
            .append(TYPE)
            .append("\":\"")
            .append(FILE_TYPE)
            .append("\",\"")
            .append(FIRST_CHUNK)
            .append("\":[],\"")
            .append(OTHER_CHUNKS)
            .append("\":{},\"")
            .append(LAST_UPDATED)
            .append("\":")
            .append(currentTime())
            .append("})")
            .toString();
    }

    public static String sendDirectoryIntoNewChannel(String channelName, Set<String> children) {
        // output looks like: @"path"!({"type":"d","children":["a","b"],"lastUpdated":123})
        return new StringBuilder()
            .append("@\"")
            .append(channelName)
            .append("\"!({\"")
            .append(TYPE)
            .append("\":\"")
            .append(DIR_TYPE)
            .append("\",\"")
            .append(CHILDREN)
            .append("\":")
            .append(set2String(children))
            .append(",\"")
            .append(LAST_UPDATED)
            .append("\":")
            .append(currentTime())
            .append("})")
            .toString();
    }

    //** Consumes a value from a chanel */
    public static String forgetChanel(String chanel) {
        // output looks like for(@v <- @"path"){v.set("lastUpdated",123)}
        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(chanel)
            .append("\"){")
            .append("v.set(\"")
            .append(LAST_UPDATED)
            .append("\",") //TODO: this is needed to prevent 'NoNewDeploy' error (updates a map and forgets)
            .append(currentTime()) // use Nil when fixed
            .append(")}")
            .toString();
    }

    //** Updates a children field a chanel data */
    public static String updateChildren(String chanel, Set<String> newChildren) {
        return new StringBuffer()
            .append("for(@v <- @\"")
            .append(chanel)
            .append("\"){")
            .append("@\"")
            .append(chanel)
            .append("\"!(v.set(\"")
            .append(LAST_UPDATED)
            .append("\",")
            .append(currentTime())
            .append(").set(\"")
            .append(CHILDREN)
            .append("\",")
            .append(set2String(newChildren))
            .append("))")
            .append("}")
            .toString();
    }

    //** Consume a value from old chanel and send to a new one */
    public static String renameChanel(String oldChanel, String newChanel) {
        // output looks like:
        // for(@v <- @"oldPath"){
        //      @"newPath"!(v)
        // }

        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(oldChanel)
            .append("\"){")
            .append("@\"")
            .append(newChanel)
            .append("\"!(v)")
            .append("}")
            .toString();
    }

    //** Consume a value from a channel and send to an appended value */
    public static String updateFileContent(String chanel, byte[] newChunk) {
        // output looks like:
        // for(@v <- @"path"){
        //      @"path"!(v.set("lastUpdated",123).set("firstChunk", "base16encodedChunk".hexToBytes()))
        // }

        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(chanel)
            .append("\"){")
            .append("@\"")
            .append(chanel)
            .append("\"!(v.set(\"")
            .append(LAST_UPDATED)
            .append("\",")
            .append(currentTime())
            .append(").set(\"")
            .append(FIRST_CHUNK)
            .append("\",\"")
            .append(Hex.encodeHexString(newChunk))
            .append("\".hexToBytes()))}")
            .toString();
    }

    public static String updateOtherChunksMap(String chanel, Map<Integer, String> otherChunks) {
        // output looks like:
        // for(@v <- @"path"){
        //      @"path"!(v.set("lastUpdated",123).set("otherChunks", {1:"subChannel"}))
        // }

        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(chanel)
            .append("\"){")
            .append("@\"")
            .append(chanel)
            .append("\"!(v.set(\"")
            .append(LAST_UPDATED)
            .append("\",")
            .append(currentTime())
            .append(").set(\"")
            .append(OTHER_CHUNKS)
            .append("\",{")
            .append(
                otherChunks.entrySet().stream()
                    .map(e -> e.getKey() + ": " + string2RholngString(e.getValue()))
                    .collect(Collectors.joining(LIST_DELIMITER))
            )
            .append("}))}")
            .toString();
    }

    public static String sendFileContentChunk(String channel, byte[] chunk) {
        // output looks like:
        // @"channel"!("base16EncodedChunk".hexToBytes())

        return new StringBuilder()
            .append("@\"")
            .append(channel)
            .append("\"!(\"")
            .append(Hex.encodeHexString(chunk))
            .append("\".hexToBytes())")
            .toString();
    }

    @NotNull
    public static String string2RholngString(String stringValue) {
        // wraps a string with quotes

        // same as "\"" + stringValue + "\""
        return new StringBuilder()
            .append("\"")
            .append(stringValue)
            .append("\"")
            .toString();
    }

    public static String set2String(Set<String> values) {
        return new StringBuilder()
            .append("[")
            .append(String.join(LIST_DELIMITER,  values.stream().map(RholangExpressionConstructor::string2RholngString).collect(Collectors.toList())))
            .append("]")
            .toString();
    }

    static String map2String(Map<String, String> emap) {
        return new StringBuilder()
            .append("{")
            .append(
                emap.entrySet().stream()
                    .map(e -> string2RholngString(e.getKey()) + ": " + string2RholngString(e.getValue()))
                    .collect(Collectors.joining(LIST_DELIMITER))
            )
            .append("}")
            .toString();
    }

    public static @NotNull byte[] parseBytes(@NotNull List<RhoTypes.Par> pars) {
        RhoTypes.Par par = pars.get(pars.size() - 1);
        int exprsCount = par.getExprsCount() - 1;
        if (exprsCount < 0) {
            throw new IllegalArgumentException("Empty channel data");
        }

        return par.getExprs(exprsCount).getGByteArray().toByteArray();
    }

    public static @NotNull ChannelData parseChannelData(@NotNull List<RhoTypes.Par> pars) throws IllegalArgumentException {
        if (pars.isEmpty()) {
            throw new IllegalArgumentException("Empty channel data");
        }
        RhoTypes.Par par = pars.get(pars.size() - 1);
        int exprsCount = par.getExprsCount() - 1;
        if (exprsCount < 0) {
            throw new IllegalArgumentException("Empty channel data");
        }

        RhoTypes.Expr expr = par.getExprs(exprsCount);
        
        if (!expr.hasEMapBody()) {
            throw new IllegalArgumentException("Expression is not an EMap");
        }

        List<RhoTypes.KeyValuePair> keyValues = expr.getEMapBody().getKvsList();

        return buildChannelDataFromKeyValues(keyValues);
    }

    /**
     * Processes a list of key-value pairs and converts them into ChannelData
     * 
     * @param keyValues List of key-value pairs from RhoTypes
     * @return ChannelData extracted from the key-value pairs
     * @throws IllegalArgumentException if required data is missing or invalid
     */
    private static @NotNull ChannelData buildChannelDataFromKeyValues(@NotNull List<RhoTypes.KeyValuePair> keyValues) 
            throws IllegalArgumentException {
        String type = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(TYPE))
            .findFirst()
            .map(kv -> kv.getValue().getExprs(0).getGString())
            .orElseThrow(() -> new IllegalArgumentException("No type in channel data"));

        long lastUpdated = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(LAST_UPDATED))
            .findFirst()
            .map(kv -> kv.getValue().getExprs(0).getGInt())
            .orElseThrow(() -> new IllegalArgumentException("No lastUpdated in channel data"));

        byte[] content = null;
        Set<String> children = null;
        Map<Integer, String> otherChunks = null;

        if (type.equals(FILE_TYPE)) {

            content = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(FIRST_CHUNK))
                .findFirst()
                .map(kv -> kv.getValue().getExprs(0).getGByteArray().toByteArray())
                .orElseThrow(() -> new IllegalArgumentException("No value in file data"));

            otherChunks = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(OTHER_CHUNKS))
                .findFirst()
                .map(kv -> kv.getValue().getExprs(0).getEMapBody().getKvsList().stream()
                    .collect(Collectors.toMap(
                        k -> (int) k.getKey().getExprs(0).getGInt(),
                        v -> v.getValue().getExprs(0).getGString()
                    )))
                .orElseThrow(() -> new IllegalArgumentException("No otherChunks in file data"));

        } else if (type.equals(DIR_TYPE)) {

            children = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(CHILDREN))
                .findFirst()
                .map(kv ->
                    kv.getValue().getExprs(0).getEListBody().getPsList().stream()
                        .map(p -> p.getExprs(p.getExprsCount() - 1).getGString())
                        .collect(Collectors.toSet()))
                .orElseThrow(() -> new IllegalArgumentException("No children in channel data"));

        } else {

            throw new IllegalArgumentException("Unknown type: " + type);

        }

        return new ChannelData(type, lastUpdated, content, children, otherChunks);
    }

    protected static @NotNull long currentTime() {
        return System.currentTimeMillis();
    }
    
    /**
     * Constructs a Rholang expression for transferring REV tokens between addresses
     * 
     * @param revAddrFrom source REV address
     * @param revAddrTo destination REV address
     * @param amount amount to transfer
     * @return Rholang expression for REV transfer
     * 
     * Output looks like:
     * new rl(`rho:registry:lookup`), RevVaultCh in {
     *   rl!(`rho:rchain:revVault`, *RevVaultCh) |
     *   for (@(_, RevVault) <- RevVaultCh) {
     *     new vaultCh, vaultTo, revVaultkeyCh, deployerId(`rho:rchain:deployerId`), deployId(`rho:rchain:deployId`) in {
     *       match ("1111uwQS3sRCQm36VkJPFQVVNWYbXQmp2EfP3c3JvcQkKJK6QNqZh", "1111uwRc5pUBYUT4ERVsmpPj1TD1cpQvQpSCVJwAhzp1Cpt8hXuVR", 100) {
     *         (revAddrFrom, revAddrTo, amount) => {
     *           @RevVault!("findOrCreate", revAddrFrom, *vaultCh) |
     *           @RevVault!("findOrCreate", revAddrTo, *vaultTo) |
     *           @RevVault!("deployerAuthKey", *deployerId, *revVaultkeyCh) |
     *           for (@vault <- vaultCh; key <- revVaultkeyCh; _ <- vaultTo) {
     *             match vault {
     *               (true, vault) => {
     *                 new resultCh in {
     *                   @vault!("transfer", revAddrTo, amount, *key, *resultCh) |
     *                   for (@result <- resultCh) {
     *                     match result {
     *                       (true , _  ) => deployId!((true, "Transfer successful (not yet finalized)."))
     *                       (false, err) => deployId!((false, err))
     *                     }
     *                   }
     *                 }
     *               }
     *               err => {
     *                 deployId!((false, "REV vault cannot be found or created."))
     *               }
     *             }
     *           }
     *         }
     *       }
     *     }
     *   }
     * }
     */
    public static String transfer(String revAddrFrom, String revAddrTo, long amount) {
        return new StringBuilder()
            .append("new rl(`rho:registry:lookup`), RevVaultCh in {")
            .append("rl!(`rho:rchain:revVault`, *RevVaultCh) | ")
            .append("for (@(_, RevVault) <- RevVaultCh) {")
            .append("new vaultCh, vaultTo, revVaultkeyCh, ")
            .append("deployerId(`rho:rchain:deployerId`), ")
            .append("deployId(`rho:rchain:deployId`) ")
            .append("in {")
            .append("match (\"")
            .append(revAddrFrom)
            .append("\", \"")
            .append(revAddrTo)
            .append("\", ")
            .append(amount)
            .append(") {")
            .append("(revAddrFrom, revAddrTo, amount) => {")
            .append("@RevVault!(\"findOrCreate\", revAddrFrom, *vaultCh) | ")
            .append("@RevVault!(\"findOrCreate\", revAddrTo, *vaultTo) | ")
            .append("@RevVault!(\"deployerAuthKey\", *deployerId, *revVaultkeyCh) | ")
            .append("for (@vault <- vaultCh; key <- revVaultkeyCh; _ <- vaultTo) {")
            .append("match vault {")
            .append("(true, vault) => {")
            .append("new resultCh in {")
            .append("@vault!(\"transfer\", revAddrTo, amount, *key, *resultCh) | ")
            .append("for (@result <- resultCh) {")
            .append("match result {")
            .append("(true, _) => deployId!((true, \"Transfer successful (not yet finalized).\"))")
            .append("(false, err) => deployId!((false, err))")
            .append("}")
            .append("}")
            .append("}")
            .append("}")
            .append("err => {")
            .append("deployId!((false, \"REV vault cannot be found or created.\"))")
            .append("}")
            .append("}")
            .append("}")
            .append("}")
            .append("}")
            .append("}")
            .append("}}")
            .append("|")
            .append(currentTime())
            .toString();
    }

    public static String readFromChannel(String channelName) {
        // output looks like: new return in { for (@v <<- @"path"){ return!(v) } }
        return new StringBuilder()
            .append("new return in {")
            .append("for (@v <<- @\"")
            .append(channelName)
            .append("\"){")
            .append("return!(v)")
            .append("}")
            .append("}")
            .toString();
    }
    
    /**
     * Parse the result of an exploratory deploy directly into ChannelData
     * 
     * @param expr The result of an exploratory deploy
     * @return ChannelData parsed from the exploratory deploy result
     */
    public static @NotNull ChannelData parseExploratoryDeployResult(@NotNull RhoTypes.Expr expr) throws IllegalArgumentException {
        if (expr == null || !expr.hasEMapBody()) {
            throw new IllegalArgumentException("Invalid exploratory deploy result: not an EMap");
        }
        
        List<RhoTypes.KeyValuePair> keyValues = expr.getEMapBody().getKvsList();
        
        return buildChannelDataFromKeyValues(keyValues);
    }
}
