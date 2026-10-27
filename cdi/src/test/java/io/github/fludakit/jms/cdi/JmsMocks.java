package io.github.fludakit.jms.cdi;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Stubbed JMS objects shared by the {@link JmsClientProducer} tests.
 *
 * <p>{@code destination("orders")} resolves to {@code queue}, {@code send("hello")} converts to
 * {@code message} and goes out through {@code producer} - so a test proves which context the client
 * used by verifying the producer call on these mocks.</p>
 */
final class JmsMocks {

    final JMSContext context = mock(JMSContext.class);
    final JMSProducer producer = mock(JMSProducer.class);
    final Queue queue = mock(Queue.class);
    final TextMessage message = mock(TextMessage.class);
    final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

    JmsMocks() {
        when(context.createQueue(anyString())).thenReturn(queue);
        when(context.createTextMessage(anyString())).thenReturn(message);
        when(context.createProducer()).thenReturn(producer);
    }

    /** Asserts the payload was sent to the stubbed destination through the stubbed context. */
    void verifySent() {
        verify(producer).send(queue, message);
    }
}
