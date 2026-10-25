package io.github.fludakit.jms;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

/**
 * Static helpers that flatten the checked {@code JMSException} of common {@link Message}
 * accessors into {@link JmsClientException} - the lambda-friendly forms for browse mappers,
 * post-processors, and assertions:
 *
 * <pre>{@code
 * client.destination("test.queue")
 *         .browse(JmsMessages::textOf);
 * }</pre>
 */
public final class JmsMessages {

    private JmsMessages() {
        throw new AssertionError("No instances");
    }

    /**
     * Reads the text of a {@link TextMessage}.
     *
     * @throws JmsClientException with {@link JmsClientException.Code#CONVERSION} if the message
     *                            is not a text message, and {@link JmsClientException.Code#RECEIVE}
     *                            if the read fails
     */
    public static String textOf(Message message) {
        if (!(message instanceof TextMessage text)) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Not a text message: " + message.getClass().getName());
        }
        try {
            return text.getText();
        } catch (JMSException e) {
            throw new JmsClientException(JmsClientException.Code.RECEIVE,
                    "Failed to read the message text", e);
        }
    }

    /**
     * Reads a String message property.
     *
     * @throws JmsClientException with {@link JmsClientException.Code#RECEIVE} if the read fails
     */
    public static String stringProperty(Message message, String name) {
        try {
            return message.getStringProperty(name);
        } catch (JMSException e) {
            throw new JmsClientException(JmsClientException.Code.RECEIVE,
                    "Failed to read property: " + name, e);
        }
    }

    /**
     * Sets a String message property.
     *
     * @throws JmsClientException with {@link JmsClientException.Code#SEND} if the write fails
     */
    public static void setStringProperty(Message message, String name, String value) {
        try {
            message.setStringProperty(name, value);
        } catch (JMSException e) {
            throw new JmsClientException(JmsClientException.Code.SEND,
                    "Failed to set property: " + name, e);
        }
    }
}
