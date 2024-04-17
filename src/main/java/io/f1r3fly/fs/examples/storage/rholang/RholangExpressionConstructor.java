package io.f1r3fly.fs.examples.storage.rholang;

import org.jetbrains.annotations.NotNull;
import rhoapi.RhoTypes;

import java.util.*;
import java.util.stream.Collectors;

public class RholangExpressionConstructor {

    public static final String EmptyList = "[]"; // as a string
    public static final String Nil = "Nil";
    private static final String LIST_DELIMITER = ",";

    public static String createChanelSending(String channelName, Map<String, String> chanelValueAsMap) {
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

    public static String createConsumeFromChanel(String chanel) {
        // output looks like `for(_ <- @"path/to/something"){ Nil }`
        return new StringBuilder()
            .append("for(_ <- @\"")
            .append(chanel)
            .append("\"){ Nil }")
            .toString();
    }

    public static String createResendToChanel(String chanel, Map<String, String> chanelValueAsMap) {
        return new StringBuilder()
            .append("for(_ <- @\"")
            .append(chanel)
            .append("\"){")
            .append(createChanelSending(chanel, chanelValueAsMap))
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

    public static String map2String(Map<String, String> emap) {
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

    public static HashMap<String, String> parseEMapFromLastExpr(@NotNull List<RhoTypes.Par> pars) {
        List<rhoapi.RhoTypes.KeyValuePair> keyValues =
            pars.get(pars.size() - 1).getExprs(0).getEMapBody().getKvsList();

        HashMap<String, String> result = new HashMap<>();

        for (rhoapi.RhoTypes.KeyValuePair kv : keyValues) {
            String key = kv.getKey().getExprs(0).getGString();
            String value = kv.getValue().getExprs(0).getGString();
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