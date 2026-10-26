package io.github.fludakit.jms;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Builder and lifecycle behavior without a broker: when the context is created, which side closes
 * it, how credentials/session mode/JmsConfig reach the ConnectionFactory - through Mockito mocks
 * of the ConnectionFactory and JMSContext contracts.
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
    void factoryBasedBuilderCreatesTheContextOnBuildAndClosesIt() {
        when(connectionFactory.createContext(anyInt())).thenReturn(context);

        JmsClient.Builder builder = JmsClient.builder(connectionFactory);
        // building the client is what opens the connection, so an abandoned builder leaks nothing
        verifyNoInteractions(connectionFactory);

        JmsClient client = builder.build();
        verify(connectionFactory, times(1)).createContext(Session.AUTO_ACKNOWLEDGE);

        client.close();
        verify(context, times(1)).close();
    }

    @Test
    void contextBasedClientLeavesTheInjectedContextOpen() {
        JmsClient client = JmsClient.builder(context).build();

        verifyNoInteractions(context);
        client.close();
        // the context belongs to the container/application that created it
        verify(context, never()).close();
    }

    @Test
    void contextIsCreatedWithAutoAcknowledgeByDefault() {
        when(connectionFactory.createContext(anyInt())).thenReturn(context);

        JmsClient client = JmsClient.builder(connectionFactory).build();

        ArgumentCaptor<Integer> sessionMode = ArgumentCaptor.forClass(Integer.class);
        verify(connectionFactory, times(1)).createContext(sessionMode.capture());
        assertEquals(Session.AUTO_ACKNOWLEDGE, sessionMode.getValue());
        client.close();
    }

    @Test
    void sessionModeIsPassedToTheConnectionFactory() {
        when(connectionFactory.createContext(anyInt())).thenReturn(context);

        JmsClient client = JmsClient.builder(connectionFactory)
                .sessionMode(Session.CLIENT_ACKNOWLEDGE)
                .build();

        verify(connectionFactory, times(1)).createContext(Session.CLIENT_ACKNOWLEDGE);
        client.close();
    }

    @Test
    void transactedSessionModeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> JmsClient.builder(connectionFactory).sessionMode(Session.SESSION_TRANSACTED));
        verifyNoInteractions(connectionFactory);
    }

    @Test
    void usernameAndPasswordCreateAnAuthenticatedContext() {
        when(connectionFactory.createContext(anyString(), anyString(), anyInt())).thenReturn(context);

        JmsClient client = JmsClient.builder(connectionFactory)
                .username("artemis")
                .password("secret")
                .build();

        verify(connectionFactory, times(1)).createContext("artemis", "secret", Session.AUTO_ACKNOWLEDGE);
        verify(connectionFactory, never()).createContext(anyInt());
        client.close();
    }

    @Test
    void missingPasswordIsSentAsAnEmptyOne() {
        when(connectionFactory.createContext(anyString(), anyString(), anyInt())).thenReturn(context);

        JmsClient client = JmsClient.builder(connectionFactory).username("artemis").build();

        verify(connectionFactory, times(1)).createContext("artemis", "", Session.AUTO_ACKNOWLEDGE);
        client.close();
    }

    @Test
    void configIsAppliedToAFactoryBasedBuilder() {
        when(connectionFactory.createContext(anyString(), anyString(), anyInt())).thenReturn(context);
        JmsConfig config = JmsConfig.builder()
                .username("artemis")
                .password("secret")
                .sessionMode(Session.DUPS_OK_ACKNOWLEDGE)
                .defaultDestination("orders")
                .build();

        JmsClient client = JmsClient.builder(connectionFactory).config(config).build();

        verify(connectionFactory, times(1)).createContext("artemis", "secret", Session.DUPS_OK_ACKNOWLEDGE);
        client.close();
    }

    @Test
    void configCredentialsAreRejectedForAContextBasedBuilder() {
        JmsConfig config = JmsConfig.builder().username("artemis").build();

        assertThrows(IllegalStateException.class, () -> JmsClient.builder(context).config(config));
        verifyNoInteractions(context);
    }

    @Test
    void configWithOnlyADefaultDestinationIsAcceptedForAContextBasedBuilder() {
        Queue queue = mock(Queue.class);
        when(context.createQueue("orders")).thenReturn(queue);
        JmsConfig config = JmsConfig.builder().defaultDestination("orders").build();

        JmsClient client = JmsClient.builder(context).config(config).build();

        assertNotNull(client.destination());
        verify(context, times(1)).createQueue("orders");
    }

    @Test
    void destinationByNameResolvesThroughTheContext() {
        Queue queue = mock(Queue.class);
        when(context.createQueue("orders")).thenReturn(queue);

        JmsClient client = JmsClient.builder(context).build();

        assertNotNull(client.destination("orders"));
        verify(context, times(1)).createQueue("orders");
    }

    @Test
    void defaultDestinationIsUsedByNoArgDestination() {
        Queue queue = mock(Queue.class);
        when(context.createQueue("orders")).thenReturn(queue);

        JmsClient withDefault = JmsClient.builder(context)
                .defaultDestination("orders")
                .build();
        assertNotNull(withDefault.destination());

        JmsClient withoutDefault = JmsClient.builder(context).build();
        assertThrows(IllegalStateException.class, withoutDefault::destination);
        verify(context, times(1)).createQueue("orders");
    }

    @Test
    void failedContextCreationIsWrappedWithTheConnectionCode() {
        JMSRuntimeException boom = new JMSRuntimeException("boom");
        when(connectionFactory.createContext(anyInt())).thenThrow(boom);

        JmsClientException exception = assertThrows(JmsClientException.class,
                () -> JmsClient.builder(connectionFactory).build());

        assertEquals(JmsClientException.Code.CONNECTION, exception.getCode());
        assertInstanceOf(JMSRuntimeException.class, exception.getCause());
        verifyNoInteractions(context);
    }
}
