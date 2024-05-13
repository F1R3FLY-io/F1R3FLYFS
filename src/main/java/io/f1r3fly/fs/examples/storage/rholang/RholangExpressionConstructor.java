package io.f1r3fly.fs.examples.storage.rholang;

import org.jetbrains.annotations.NotNull;
import rhoapi.RhoTypes;

import java.util.*;
import java.util.stream.Collectors;

public class RholangExpressionConstructor {

    public static final String EmptyList = "[]"; // as a string
    public static final String Nil = "Nil";
    private static final String LIST_DELIMITER = ",";

    //** Creates a chanel with a value */
    public static String sendValueIntoNewChanel(String channelName, Map<String, Object> chanelValueAsMap) {
        // output looks like '@"/tmp/file"!({"type": "f", "value": "rolangValueAsString"})'


        // same as "@\"" + channelName + "\"!(" + map2String(chanelValueAsMap) + ")"
        return new StringBuilder()
            .append("@\"")
            .append(channelName)
            .append("\"!(")
            .append(map2String(chanelValueAsMap))
            .append(")")
            .toString();
    }

    //** Consumes a value from a chanel */
    public static String readAndForget(String chanel, String currentTime) {
        // output looks like `for(_ <- @"path/to/something"){ Nil }`
        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(chanel)
            .append("\"){")
            .append("v.set(\"lastUpdated\",") //TODO: this is needed to prevent 'NoNewDeploy' error (updates a map and forgets)
            .append(string2RholngString(currentTime)) // use Nil when fixed
            .append(")}")
            .toString();
    }

    //** Replaces a value inside a chanel */
    public static String replaceValue(String chanel, Map<String, String> chanelValueAsMap) {
        // output looks like:
        // for(_ <- @"path/to/something"){
        //      @"path/to/something"!({"type": "f", "value": "rolangValueAsString"})
        // }

        return new StringBuilder()
            .append("for(_ <- @\"")
            .append(chanel)
            .append("\"){")
            .append(sendValueIntoNewChanel(chanel, new HashMap<>(chanelValueAsMap))) // TODO: avoid copying
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

    //** Consume a value from a chanel and send to an appended value */
    public static String appendValue(String chanel, String lastUpdated, String newChunk, long size) {
        // output looks like:
        // for(@v <- @"path"){
        //      @"path"!(v.set("lastUpdated","123").set("value", v.get("value) ++ "newChunk").set("size", v.get("size) + size))
        // }

        return new StringBuilder()
            .append("for(@v <- @\"")
            .append(chanel)
            .append("\"){")
            .append("@\"")
            .append(chanel)
            .append("\"!(v.set(\"lastUpdated\",")
            .append(string2RholngString(lastUpdated))
            .append(").set(\"value\",v.get(\"value\") ++ ")
            .append(string2RholngString(newChunk))
            .append(").set(\"size\",v.get(\"size\") + ")
            .append(size)
            .append("))")
            .append("}")
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
            .append(String.join(LIST_DELIMITER, values))
            .append("]")
            .toString();
    }

    public static String map2String(Map<String, Object> emap) {
        return new StringBuilder()
            .append("{")
            .append(
                emap.entrySet().stream()
                    .map(e -> {
                        String value =
                            e.getValue() instanceof Long ?
                                e.getValue().toString() : // number without quotes
                                string2RholngString(e.getValue().toString()); // string with quotes
                        return string2RholngString(e.getKey()) + ": " + value;
                    })
                    .collect(Collectors.joining(LIST_DELIMITER))
            )
            .append("}")
            .toString();
    }

    public static HashMap<String, String> parseEMapFromLastExpr(@NotNull List<RhoTypes.Par> pars) {
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
            // this expression could be a string or a number
            String value = valueExpr.getGString().isBlank() ? // get string if it's not empty string otherwise get it as int
                String.valueOf(valueExpr.getGInt()) : valueExpr.getGString();

            result.put(key, value);
        }

        return result;
    }

    public static Set<String> parseList(String value) {
        // Expects input like `["a", "b", "c"]` and returns Array of string.
        // Fails on empty string
        return Set.of(value.substring(1, value.length() - 1).split(LIST_DELIMITER)).stream().filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
}
