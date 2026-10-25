package io.github.fludakit.jms;

/**
 * Unchecked exception for JMS client failures, with a stable {@link Code} for programmatic
 * handling - the same pattern the JdbcClient uses for SQL failures.
 */
public class JmsClientException extends RuntimeException {

    /** The category of the failure. */
    public enum Code {
        /** The broker connection or context could not be created. */
        CONNECTION,
        /** Sending a message failed. */
        SEND,
        /** Receiving (or browsing) a message failed. */
        RECEIVE,
        /** Payload-to-message or message-to-payload conversion failed. */
        CONVERSION,
        /** A receive timed out before a message arrived. */
        TIMEOUT
    }

    private final Code code;

    public JmsClientException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public JmsClientException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** The category of the failure. */
    public Code getCode() {
        return code;
    }
}
