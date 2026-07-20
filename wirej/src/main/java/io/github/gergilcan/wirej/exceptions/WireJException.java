package io.github.gergilcan.wirej.exceptions;

public class WireJException extends RuntimeException {

    public WireJException(String message) {
        super(message);
    }

    public WireJException(String message, Throwable cause) {
        super(message, cause);
    }
}
