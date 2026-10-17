package io.github.elderpath_crusade.net.protocol;

/** A received line that isn't a valid protocol message. */
public class ProtocolException extends RuntimeException {
    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
