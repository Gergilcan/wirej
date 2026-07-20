package io.github.gergilcan.wirej.exceptions;

/**
 * Unchecked exception used throughout WireJ for framework/infrastructure
 * failures (query execution, service wiring). Being unchecked, it crosses a
 * java.lang.reflect.Proxy boundary unwrapped, unlike checked exceptions
 * which the JDK hides inside a message-less UndeclaredThrowableException.
 */
public class WireJException extends RuntimeException {

    public WireJException(String message) {
        super(message);
    }

    public WireJException(String message, Throwable cause) {
        super(message, cause);
    }
}
