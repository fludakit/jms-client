package io.github.fludakit.jms.support;

import io.github.fludakit.jms.JmsClientException;

import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JSON conversion without a broker: the JSON text and {@code _type} property the converter
 * writes, the type it rebuilds on receive, and how it degrades when no JSON-B provider is
 * deployed. Runs against Yasson, the reference implementation.
 */
class JsonbMessageConverterTest {

    private final Jsonb jsonb = JsonbBuilder.create();
    private final JsonbMessageConverter converter = new JsonbMessageConverter();

    public static class Order {

        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Order order && Objects.equals(id, order.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }

    public static class PriorityOrder extends Order {

        private int priority;

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }
    }

    @Test
    void sendingWritesJsonAndTheTypeId() {
        JMSContext context = mock(JMSContext.class);
        TextMessage message = mock(TextMessage.class);
        when(context.createTextMessage(anyString())).thenReturn(message);

        Order order = order("order-1");

        assertEquals(message, converter.toMessage(order, context));

        verify(context).createTextMessage(jsonb.toJson(order));
        try {
            verify(message).setStringProperty(JsonbMessageConverter.TYPE_ID_PROPERTY, Order.class.getName());
        } catch (JMSException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void typedReceiveRebuildsTheRequestedType() {
        Order received = converter.fromMessage(textMessage(json(order("order-1")), null), Order.class);

        assertEquals(order("order-1"), received);
    }

    @Test
    void typedReceiveUsesATypeIdNamingASubtype() {
        Message message = textMessage(json(priorityOrder()), PriorityOrder.class.getName());

        assertInstanceOf(PriorityOrder.class, converter.fromMessage(message, Order.class));
    }

    @Test
    void typedReceiveIgnoresATypeIdThatIsNotAssignable() {
        // a producer that publishes a type id the caller did not ask for cannot widen the result
        Order received = converter.fromMessage(textMessage(json(order("order-1")), String.class.getName()),
                Order.class);

        assertEquals(order("order-1"), received);
    }

    @Test
    void untypedReceiveNeedsTheTypeId() {
        JmsClientException exception = assertThrows(JmsClientException.class,
                () -> converter.fromMessage(textMessage(json(order("order-1")), null)));

        assertEquals(JmsClientException.Code.CONVERSION, exception.getCode());
        assertTrue(exception.getMessage().contains(JsonbMessageConverter.TYPE_ID_PROPERTY));
    }

    @Test
    void untypedReceiveRebuildsThePublishedType() {
        Message message = textMessage(json(order("order-1")), Order.class.getName());

        assertEquals(order("order-1"), converter.fromMessage(message));
    }

    @Test
    void unknownTypeIdIsReported() {
        JmsClientException exception = assertThrows(JmsClientException.class,
                () -> converter.fromMessage(textMessage(json(order("order-1")), "com.example.Missing")));

        assertEquals(JmsClientException.Code.CONVERSION, exception.getCode());
        assertInstanceOf(ClassNotFoundException.class, exception.getCause());
    }

    @Test
    void nonTextMessageIsRejected() {
        JmsClientException exception = assertThrows(JmsClientException.class,
                () -> converter.fromMessage(mock(Message.class), Order.class));

        assertEquals(JmsClientException.Code.CONVERSION, exception.getCode());
    }

    @Test
    void nullPayloadIsRejected() {
        JmsClientException exception = assertThrows(JmsClientException.class,
                () -> converter.toMessage(null, mock(JMSContext.class)));

        assertEquals(JmsClientException.Code.CONVERSION, exception.getCode());
    }

    @Test
    void bodyThatIsNotJsonForTheTypeIsReported() {
        JmsClientException exception = assertThrows(JmsClientException.class,
                () -> converter.fromMessage(textMessage("not json", null), Order.class));

        assertEquals(JmsClientException.Code.CONVERSION, exception.getCode());
    }

    @Test
    void loadsSafelyWithoutAnyJsonbAtAll() throws Exception {
        // a class loader that sees neither the JSON-B API nor an implementation: the facade has
        // no JSON-B imports, so it LOADS and isAvailable-style probing is unnecessary -
        // construction fails fast with a JmsClientException instead of a NoClassDefFoundError
        try (URLClassLoader noJsonb = new URLClassLoader(
                new URL[]{codeSource(JMSContext.class), codeSource(JmsClientException.class)},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> isolated = Class.forName(JsonbMessageConverter.class.getName(), false, noJsonb);

            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                    () -> isolated.getConstructor().newInstance());
            assertInstanceOf(JmsClientException.class, thrown.getCause());
            assertTrue(thrown.getCause().getMessage().contains("JSON Binding provider"),
                    thrown.getCause().getMessage());
        }
    }

    private static Order order(String id) {
        Order order = new Order();
        order.setId(id);
        return order;
    }

    private static PriorityOrder priorityOrder() {
        PriorityOrder order = new PriorityOrder();
        order.setId("order-1");
        order.setPriority(1);
        return order;
    }

    private String json(Object payload) {
        return jsonb.toJson(payload);
    }

    private static Message textMessage(String body, String typeId) {
        Message message = mock(Message.class, org.mockito.Mockito.withSettings().extraInterfaces(TextMessage.class));
        try {
            when(((TextMessage) message).getText()).thenReturn(body);
            when(message.getStringProperty(JsonbMessageConverter.TYPE_ID_PROPERTY)).thenReturn(typeId);
        } catch (JMSException e) {
            throw new IllegalStateException(e);
        }
        return message;
    }

    private static URL codeSource(Class<?> type) {
        URL location = type.getProtectionDomain().getCodeSource().getLocation();
        assertNotNull(location, "code source of " + type.getName());
        return location;
    }
}
