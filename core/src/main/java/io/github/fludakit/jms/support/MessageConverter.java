package io.github.fludakit.jms.support;

import io.github.fludakit.jms.JmsClientException;
import jakarta.jms.Message;
import jakarta.jms.JMSContext;

/**
 * Converts payloads to {@link jakarta.jms.Message}s and back - the SPI users implement to plug in
 * their own translation (Spring JMS parity).
 *
 * <p>Built-ins: {@link SimpleMessageConverter} (String / byte[] / Map / Serializable) and, when a
 * JSON-B implementation is on the classpath, JSON conversion (Phase 2).</p>
 */
public interface MessageConverter {

    /**
     * Creates a {@link Message} carrying the payload, using the supplied context to construct it.
     *
     * @param payload the payload to convert
     * @param context the client's JMS context (message factory)
     * @return the JMS message ready to send
     * @throws JmsClientException with {@link JmsClientException.Code#CONVERSION} if the payload
     *                            type is not supported
     */
    Message toMessage(Object payload, JMSContext context);

    /**
     * Extracts the payload from a received {@link Message}.
     *
     * @param message the received message
     * @return the extracted payload (its type is validated against the caller's requested type)
     * @throws JmsClientException with {@link JmsClientException.Code#CONVERSION} if the message
     *                            type is not supported
     */
    Object fromMessage(Message message);
}
