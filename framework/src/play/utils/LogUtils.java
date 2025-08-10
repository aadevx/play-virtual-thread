package play.utils;

import play.Logger;

/**
 * custom Logger ERROR with tag
 * @author arief ardiyansah
 */


public class LogUtils {

    static final String SEPARATOR = " - ";

    public static void error(String tagName, String message, Object... args) {
        Logger.error(tagName+ SEPARATOR +message, args);
    }

    public static void error(String tagName, Throwable e, String message, Object... args) {
        Logger.error(e, tagName+ SEPARATOR +message, args);
    }

    // custom Logger INFO with tag
    public static void info(String tagName, String message, Object... args) {
        Logger.info(tagName+ SEPARATOR +message, args);
    }

    public static void info(String tagName, Throwable e, String message, Object... args) {
        Logger.info(e, tagName+ SEPARATOR +message, args);
    }

    // custom Logger DEBUG with tag
    public static void debug(String tagName, String message, Object... args) {
        Logger.debug(tagName+ SEPARATOR +message, args);
    }

    public static void debug(String tagName, Throwable e, String message, Object... args) {
        Logger.debug(e, tagName+ SEPARATOR +message, args);
    }

    // custom Logger WARN with tag
    public static void warn(String tagName, String message, Object... args) {
        Logger.warn(tagName+ SEPARATOR +message, args);
    }

    public static void warn(String tagName, Throwable e, String message, Object... args) {
        Logger.warn(e, tagName+ SEPARATOR +message, args);
    }

}
