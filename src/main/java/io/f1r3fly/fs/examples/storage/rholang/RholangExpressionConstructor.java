package io.f1r3fly.fs.examples.storage.rholang;

import io.f1r3fly.fs.examples.datatransformer.Base16Coder;
import org.jetbrains.annotations.NotNull;
import rhoapi.RhoTypes;

import java.util.*;
import java.util.stream.Collectors;

public class RholangExpressionConstructor {

    public static final String EmptyList = "[]"; // as a string
    public static final String Nil = "Nil";
    private static final String LIST_DELIMITER = ",";


    private static final String TYPE = "type";
    private static final String DIR_TYPE = "d";
    private static final String FILE_TYPE = "f";

    private static final String FILE_CONTENT = "fileContent";
    private static final String CHILDREN = "children";
    private static final String SIZE = "size";
    private static final String LAST_UPDATED = "lastUpdated";

    /**
     * Represents a file or a folder
     *
     * @param type        "f" for file, "d" for directory
     * @param lastUpdated timestamp of the last update
     * @param size        size of the file; -1 for a folder
     * @param fileContent content of the file; null for a folder
     * @param children    list of children; null for a file
     */
    public record ChannelData(String type, long lastUpdated, long size, byte[] fileContent, Set<String> children) {
        public boolean isFile() {
            return type.equals(FILE_TYPE);
        }

        public boolean isDir() {
            return type.equals(DIR_TYPE);
        }
    }

    //** Creates a chanel with a file */
    public static String sendFileIntoNewChanel(String channelName, long size, byte[] content) {
        // output looks like: @"path"!({"type":"f","size":123,"fileContent":[0x01,0x02]})
        return new StringBuilder()
            .append("@\"")
            .append(channelName)
            .append("\"!({\"")
            .append(TYPE)
            .append("\":\"")
            .append(FILE_TYPE)
            .append("\",\"")
            .append(FILE_CONTENT)
            .append("\":\"")
            .append(Base16Coder.bytesToHex(content))
            .append("\".hexToBytes(),\"")
            .append(SIZE)
            .append("\":")
            .append(size)
            .append(",\"")
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
    public static String appendValue(String chanel, byte[] newChunk, long size) {
        // output looks like:
        // for(@v <- @"path"){
        //      @"path"!(v.set("lastUpdated",123).set("size", v.get("size) + size).set("fileContent", v.get("fileContent) ++ []))
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
            .append(SIZE)
            .append("\",v.get(\"")
            .append(SIZE)
            .append("\") + ")
            .append(size)
            .append(").set(\"")
            .append(FILE_CONTENT)
            .append("\",v.get(\"")
            .append(FILE_CONTENT)
            .append("\") ++ \"")
            .append(Base16Coder.bytesToHex(newChunk))
            .append("\".hexToBytes()))}")
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

    public static String replaceChannelValue(String channel, Map<String, String> newChannelValue) {
        // output looks like: for(_ <- @"path"){ @"path"!(newChannelValue) }
        return new StringBuilder()
            .append("for(_ <- @\"")
            .append(channel)
            .append("\"){@\"")
            .append(channel)
            .append("\"!(")
            .append(map2String(newChannelValue))
            .append(")}")
            .toString();
    }

    public static HashMap<String, String> parseMap(@NotNull List<RhoTypes.Par> pars) {
        RhoTypes.Par par = pars.get(pars.size() - 1);
        int exprsCount = par.getExprsCount() - 1;
        if (exprsCount < 0) {
            return new HashMap<>();
        }

        List<rhoapi.RhoTypes.KeyValuePair> keyValues =
            par.getExprs(exprsCount).getEMapBody().getKvsList();

        HashMap<String, String> result = new HashMap<>();

        for (rhoapi.RhoTypes.KeyValuePair kv : keyValues) {
            String key = kv.getKey().getExprs(0).getGString();

            RhoTypes.Expr valueExpr = kv.getValue().getExprs(0);
            String value = valueExpr.getGString();

            result.put(key, value);
        }

        return result;
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

        List<RhoTypes.KeyValuePair> keyValues =
            par.getExprs(exprsCount).getEMapBody().getKvsList();

        String type = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(TYPE))
            .findFirst()
            .map(kv -> kv.getValue().getExprs(0).getGString())
            .orElseThrow(() -> new IllegalArgumentException("No type in channel data"));

        long lastUpdated = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(LAST_UPDATED))
            .findFirst()
            .map(kv -> kv.getValue().getExprs(0).getGInt())
            .orElseThrow(() -> new IllegalArgumentException("No lastUpdated in channel data"));

        long size = -1;
        byte[] content = null;
        Set<String> children = null;

        if (type.equals(FILE_TYPE)) {

            size = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(SIZE))
                .findFirst()
                .map(kv -> kv.getValue().getExprs(0).getGInt())
                .orElseThrow(() -> new IllegalArgumentException("No size in file data"));

            content = keyValues.stream().filter(kv -> kv.getKey().getExprs(0).getGString().equals(FILE_CONTENT))
                .findFirst()
                .map(kv -> kv.getValue().getExprs(0).getGByteArray().toByteArray())
                .orElseThrow(() -> new IllegalArgumentException("No value in file data"));

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

        return new ChannelData(type, lastUpdated, size, content, children);
    }

    protected static @NotNull long currentTime() {
        return System.currentTimeMillis();
    }
}
