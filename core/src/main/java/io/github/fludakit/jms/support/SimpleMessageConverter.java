package io.github.fludakit.jms.support;

import io.github.fludakit.jms.JmsClientException;
import jakarta.jms.BytesMessage;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.ObjectMessage;
import jakarta.jms.TextMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The default {@link MessageConverter}: String, byte[], Map, and Serializable payloads - the same
 * shape as Spring's SimpleMessageConverter.
 */
public class SimpleMessageConverter implements MessageConverter {

    @Override
    public Message toMessage(Object payload, JMSContext context) {
        try {
            if (payload instanceof String s) {
                return context.createTextMessage(s);
            }
            if (payload instanceof byte[] bytes) {
                BytesMessage message = context.createBytesMessage();
                message.writeBytes(bytes);
                return message;
            }
            if (payload instanceof Map<?, ?> map) {
                MapMessage message = context.createMapMessage();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    message.setObject(String.valueOf(entry.getKey()), entry.getValue());
                }
                return message;
            }
            if (payload instanceof java.io.Serializable serializable) {
                return context.createObjectMessage(serializable);
            }
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Unsupported payload type: " + payload.getClass().getName()
                            + "; register a MessageConverter for it");
        } catch (JMSException e) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Failed to create JMS message from payload", e);
        }
    }

    @Override
    public Object fromMessage(Message message) {
        try {
            if (message instanceof TextMessage text) {
                return text.getText();
            }
            if (message instanceof BytesMessage bytes) {
                byte[] data = new byte[(int) bytes.getBodyLength()];
                bytes.readBytes(data);
                return data;
            }
            if (message instanceof MapMessage map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (var names = map.getMapNames(); names.hasMoreElements(); ) {
                    String name = (String) names.nextElement();
                    result.put(name, map.getObject(name));
                }
                return result;
            }
            if (message instanceof ObjectMessage object) {
                return object.getObject();
            }
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Unsupported message type: " + message.getClass().getName());
        } catch (JMSException e) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Failed to extract payload from JMS message", e);
        }
    }
}
