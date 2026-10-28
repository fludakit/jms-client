package io.github.fludakit.jms.support;

import io.github.fludakit.jms.JmsClientException;
import jakarta.jms.Destination;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JndiDestinationResolverTest {

    private final Context namingContext = mock(Context.class);
    private final JMSContext jmsContext = mock(JMSContext.class);
    private final Queue queue = mock(Queue.class);

    private final JndiDestinationResolver resolver = new JndiDestinationResolver(namingContext);

    @Test
    void returnsTheDestinationLookedUpByName() throws NamingException {
        when(namingContext.lookup("java:comp/jms/orders")).thenReturn(queue);

        Destination resolved = resolver.resolveDestination(jmsContext, "java:comp/jms/orders");

        assertSame(queue, resolved);
    }

    @Test
    void wrapsNamingExceptionInJmsClientException() throws NamingException {
        when(namingContext.lookup("missing")).thenThrow(new NameNotFoundException("missing"));

        JmsClientException thrown = assertThrows(JmsClientException.class,
                () -> resolver.resolveDestination(jmsContext, "missing"));

        assertEquals(JmsClientException.Code.CONNECTION, thrown.getCode());
        assertEquals("Failed to look up JMS destination: missing", thrown.getMessage());
        assertInstanceOf(NameNotFoundException.class, thrown.getCause());
    }

    @Test
    void rejectsNonDestinationLookupResults() throws NamingException {
        when(namingContext.lookup("java:comp/env/something")).thenReturn("not a destination");

        JmsClientException thrown = assertThrows(JmsClientException.class,
                () -> resolver.resolveDestination(jmsContext, "java:comp/env/something"));

        assertEquals(JmsClientException.Code.CONNECTION, thrown.getCode());
        assertEquals("JNDI lookup for 'java:comp/env/something' did not return a Destination: java.lang.String",
                thrown.getMessage());
    }

    @Test
    void rejectsNullLookupResults() throws NamingException {
        when(namingContext.lookup("null-name")).thenReturn(null);

        JmsClientException thrown = assertThrows(JmsClientException.class,
                () -> resolver.resolveDestination(jmsContext, "null-name"));

        assertEquals(JmsClientException.Code.CONNECTION, thrown.getCode());
        assertEquals("JNDI lookup for 'null-name' did not return a Destination: null",
                thrown.getMessage());
    }

    @Test
    void noArgConstructorCreatesAnInitialContext() {
        // no JNDI provider in the test classpath, but the constructor must not throw:
        // InitialContext creation succeeds with an empty environment
        JndiDestinationResolver defaultResolver = new JndiDestinationResolver();
        // the resolver is usable; lookups will fail without a provider, which is expected
        assertThrows(JmsClientException.class,
                () -> defaultResolver.resolveDestination(jmsContext, "any-name"));
    }
}
