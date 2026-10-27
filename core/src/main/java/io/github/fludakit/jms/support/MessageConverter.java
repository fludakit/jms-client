package io.github.fludakit.jms.support;

import io.github.fludakit.jms.JmsClientException;
import jakarta.jms.Message;
import jakarta.jms.JMSContext;

/**
 * Converts payloads to {@link jakarta.jms.Message}s and back - the SPI users implement to plug in
 * their own translation (Spring JMS parity).
 *
 * <p>Built-ins: {@link SimpleMessageConverter} (String / byte[] / Map / Serializable) and
 * {@link JsonbMessageConverter} (JSON, when a Jakarta JSON Binding implementation is deployed).</p>
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
     * @return the extracted payload
     * @throws JmsClientException with {@link JmsClientException.Code#CONVERSION} if the message
     *                            type is not supported
     */
    Object fromMessage(Message message);

    /**
     * Extracts the payload from a received {@link Message} as the requested type - the form the
     * client's {@code receive} operations call. Converters that can only produce a type when told
     * which one to produce (JSON, for instance) override this; the default converts and then
     * checks the result against the requested type.
     *
     * @param message     the received message
     * @param payloadType the payload type the caller expects
     * @param <T>         the payload type
     * @return the extracted payload, never {@code null} for a non-null message
     * @throws JmsClientException with {@link JmsClientException.Code#CONVERSION} when the payload
     *                            cannot be produced as {@code payloadType}
     */
    default <T> T fromMessage(Message message, Class<T> payloadType) {
        Object payload = fromMessage(message);
        if (!payloadType.isInstance(payload)) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Converted payload of type " + payload.getClass().getName()
                            + " is not assignable to " + payloadType.getName());
        }
        return payloadType.cast(payload);
    }
}
