package net.uvavru.maven.plugin.jacocogerrit;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.slf4j.Logger;

/**
 * The Utils.
 */
public class Utils {

    public static <T extends Throwable> void logErrorAndThrow(Logger logger, Function<String, T> exceptionThrow, String message)
            throws T {
        logErrorAndThrow(logger, (s, throwable) -> exceptionThrow.apply(s), message, null);
    }

    public static <T extends Throwable> void logErrorAndThrow(Logger logger,
                                                              BiFunction<String, Throwable, T> exceptionThrow,
                                                              String message,
                                                              Throwable throwable) throws T {
        if (throwable != null) {
            logger.error(message, throwable);
        } else {
            logger.error(message);
        }
        throw exceptionThrow.apply(message, throwable);
    }
}
