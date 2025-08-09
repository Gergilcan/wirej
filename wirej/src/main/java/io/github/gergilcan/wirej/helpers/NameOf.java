package io.github.gergilcan.wirej.helpers;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * A functional interface that allows capturing a method reference
 * and is also serializable.
 */
@FunctionalInterface
interface MethodFinder extends Function<Object, Object>, Serializable {
}

public class NameOf {
    /**
     * Extracts the method name from a method reference (e.g., User::login).
     *
     * @param finder The method reference.
     * @return The simple name of the method.
     */
    public static String method(Object finder) {
        try {
            // This is the magic: force the lambda to reveal its serialized form
            Method writeReplace = finder.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda serializedLambda = (SerializedLambda) writeReplace.invoke(finder);

            // The method name is available in the serialized lambda
            return serializedLambda.getImplMethodName();
        } catch (Exception e) {
            throw new RuntimeException("Could not find method name", e);
        }
    }
}