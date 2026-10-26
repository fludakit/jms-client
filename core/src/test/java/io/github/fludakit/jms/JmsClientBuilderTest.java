package io.github.fludakit.jms;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Builder and lifecycle behavior without a broker: context creation, ownership (which side closes
 * the context), and the config-carrying path - through Mockito mocks of the ConnectionFactory and
 * JMSContext contracts.
 */
class JmsClientBuilderTest {

    private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    private final JMSContext context = mock(JMSContext.class);

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> JmsClient.builder((ConnectionFactory) null));
        assertThrows(NullPointerException.class, () -> JmsClient.builder((JMSContext) null));
        verifyNoInteractions(connectionFactory, context);
    }

    @Test
    void factoryBasedClientCreatesAndClosesItsContext() {
        when(connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)).thenReturn(context);

        JmsClient client = JmsClient.builder(connectionFactory).build();

        verify(connectionFactory, times(1)).createContext(Session.AUTO_ACKNOWLEDGE);
        verifyNoInteractions(context);
        client.close();
        verify(context, times(1)).close();
    }

    @Test
    void contextBasedClientClosesTheContextOnClose() {
        JmsClient client = JmsClient.builder(context).build();

        verifyNoInteractions(context);
        client.close();
        verify(context, times(1)).close();
    }

    @Test
    void contextIsCreatedWithAutoAcknowledgeByDefault() {
        // credentials and session settings belong on the ConnectionFactory itself;
        // builder(connectionFactory) creates the JMSContext with AUTO_ACKNOWLEDGE
        when(connectionFactory.createContext(anyInt())).thenReturn(context);

        JmsClient client = JmsClient.builder(connectionFactory).build();

        ArgumentCaptor<Integer> sessionMode = ArgumentCaptor.forClass(Integer.class);
        verify(connectionFactory, times(1)).createContext(sessionMode.capture());
        assertEquals(Session.AUTO_ACKNOWLEDGE, sessionMode.getValue());
        client.close();
    }

    @Test
    void destinationByNameResolvesThroughTheContext() {
        Queue queue = mock(Queue.class);
        when(context.createQueue("orders")).thenReturn(queue);

        JmsClient client = JmsClient.builder(context).build();

        var spec = client.destination("orders");

        assertNotNull(spec);
        verify(context, times(1)).createQueue("orders");
        client.close();
    }

    @Test
    void defaultDestinationIsUsedByNoArgDestination() {
        Queue queue = mock(Queue.class);
        when(context.createQueue("orders")).thenReturn(queue);

        JmsClient client = JmsClient.builder(context)
                .defaultDestination("orders")
                .build();

        assertNotNull(client.destination());
        verify(context, times(1)).createQueue("orders");

        JmsClient withoutDefault = JmsClient.builder(context).build();
        assertThrows(IllegalStateException.class, withoutDefault::destination);
        verify(context, times(1)).createQueue("orders");
        withoutDefault.close();
    }

    @Test
    void failedContextCreationPropagatesTheProviderException() {
        // jakarta.jms 3.1: createContext throws the unchecked JMSRuntimeException, which
        // propagates as-is - there is no client-side wrapping on this path
        when(connectionFactory.createContext(anyInt())).thenThrow(new JMSRuntimeException("boom"));

        assertThrows(JMSRuntimeException.class, () -> JmsClient.builder(connectionFactory).build());
        verifyNoInteractions(context);
    }
}
