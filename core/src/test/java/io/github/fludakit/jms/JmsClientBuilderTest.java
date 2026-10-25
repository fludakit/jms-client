package io.github.fludakit.jms;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Builder and lifecycle behavior without a broker: context creation, ownership (which side closes
 * the context), and the config-carrying path - through stub ConnectionFactory/JMSContext proxies.
 */
class JmsClientBuilderTest {

    private final AtomicInteger createContextCalls = new AtomicInteger();
    private final AtomicInteger contextCloseCalls = new AtomicInteger();

    private ConnectionFactory stubConnectionFactory() {
        return (ConnectionFactory) Proxy.newProxyInstance(
                ConnectionFactory.class.getClassLoader(),
                new Class<?>[] { ConnectionFactory.class },
                (proxy, method, args) -> {
                    if ("createContext".equals(method.getName())) {
                        createContextCalls.incrementAndGet();
                        return stubContext();
                    }
                    return null;
                });
    }

    private JMSContext stubContext() {
        return (JMSContext) Proxy.newProxyInstance(
                JMSContext.class.getClassLoader(),
                new Class<?>[] { JMSContext.class },
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        contextCloseCalls.incrementAndGet();
                        return null;
                    }
                    if ("createQueue".equals(method.getName())) {
                        return (Queue) Proxy.newProxyInstance(
                                Queue.class.getClassLoader(),
                                new Class<?>[] { Queue.class },
                                (p, m, a) -> "stub-queue");
                    }
                    return null;
                });
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> JmsClient.builder((ConnectionFactory) null));
        assertThrows(NullPointerException.class, () -> JmsClient.builder((JMSContext) null));
    }

    @Test
    void factoryBasedClientCreatesAndClosesItsContext() {
        JmsClient client = JmsClient.builder(stubConnectionFactory()).build();

        assertEquals(1, createContextCalls.get());
        client.close();
        assertEquals(1, contextCloseCalls.get(), "a client-created context is closed by the client");
    }

    @Test
    void contextBasedClientClosesTheContextOnClose() {
        JMSContext context = stubContext();
        JmsClient client = JmsClient.builder(context).build();

        client.close();
        assertEquals(1, contextCloseCalls.get());
    }

    @Test
    void contextIsCreatedWithAutoAcknowledgeByDefault() {
        // credentials and session settings belong on the ConnectionFactory itself;
        // builder(connectionFactory) creates the JMSContext with AUTO_ACKNOWLEDGE
        ConnectionFactory factory = (ConnectionFactory) Proxy.newProxyInstance(
                ConnectionFactory.class.getClassLoader(),
                new Class<?>[] { ConnectionFactory.class },
                (proxy, method, args) -> {
                    if ("createContext".equals(method.getName())) {
                        assertEquals(Session.AUTO_ACKNOWLEDGE, args[0]);
                        createContextCalls.incrementAndGet();
                        return stubContext();
                    }
                    return null;
                });

        JmsClient client = JmsClient.builder(factory).build();

        assertEquals(1, createContextCalls.get());
        client.close();
    }

    @Test
    void destinationByNameResolvesThroughTheContext() {
        JmsClient client = JmsClient.builder(stubConnectionFactory()).build();

        var spec = client.destination("orders");

        assertNotNull(spec);
        client.close();
    }

    @Test
    void defaultDestinationIsUsedByNoArgDestination() {
        JmsClient client = JmsClient.builder(stubConnectionFactory())
                .defaultDestination("orders")
                .build();

        assertNotNull(client.destination());

        JmsClient withoutDefault = JmsClient.builder(stubConnectionFactory()).build();
        assertThrows(IllegalStateException.class, withoutDefault::destination);
        withoutDefault.close();
    }

    @Test
    void failedContextCreationPropagatesTheProviderException() {
        // jakarta.jms 3.1: createContext throws the unchecked JMSRuntimeException, which
        // propagates as-is - there is no client-side wrapping on this path
        ConnectionFactory failing = (ConnectionFactory) Proxy.newProxyInstance(
                ConnectionFactory.class.getClassLoader(),
                new Class<?>[] { ConnectionFactory.class },
                (proxy, method, args) -> {
                    if ("createContext".equals(method.getName())) {
                        throw new JMSRuntimeException("boom");
                    }
                    return null;
                });

        assertThrows(JMSRuntimeException.class, () -> JmsClient.builder(failing).build());
    }
}
