package io.f1r3fly.fs.utils;

public class PathUtils {

    public static String getPathDelimiterBasedOnOS() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "\\" : "/";
    }

    public static String getParentPath(String path) {
        return path.substring(0, path.lastIndexOf(getPathDelimiterBasedOnOS()));
    }

    public static String getFileName(String path) {
        return path.substring(path.lastIndexOf(getPathDelimiterBasedOnOS()) + 1);
    }

}
