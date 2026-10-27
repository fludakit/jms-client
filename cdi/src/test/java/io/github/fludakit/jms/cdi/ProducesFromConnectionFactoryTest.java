package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.CachingConnectionFactory;
import io.github.fludakit.jms.JmsClient;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Without a context bean the client is built from the {@code @Caching} factory produced by
 * {@link CachingConnectionFactoryProducer}: two clients on the same thread share one JMSContext -
 * one connection - even though every client is a fresh {@code @Dependent} instance.
 */
@ExtendWith(WeldJunit5Extension.class)
class ProducesFromConnectionFactoryTest {

    static final JmsMocks MOCKS = new JmsMocks();

    @WeldSetup
    WeldInitiator setup = WeldInitiator
            .from(JmsClientProducer.class, CachingConnectionFactoryProducer.class, ConnectionFactoryBean.class)
            .build();

    @Inject
    Instance<JmsClient> clients;

    @Inject
    @Caching
    CachingConnectionFactory cachingConnectionFactory;

    @Test
    void reusesOneContextAcrossClientsOnTheSameThread() {
        when(MOCKS.connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)).thenReturn(MOCKS.context);

        JmsClient first = clients.get();
        JmsClient second = clients.get();

        // @Dependent: one client per injection point, because JmsClient is not thread-safe
        assertNotSame(first, second);
        // but the caching factory hands this thread the same context - one connection
        verify(MOCKS.connectionFactory, times(1)).createContext(Session.AUTO_ACKNOWLEDGE);
        assertNotNull(cachingConnectionFactory);

        first.destination("orders").send("hello");
        MOCKS.verifySent();
    }

    public static class ConnectionFactoryBean {

        @Produces
        public ConnectionFactory connectionFactory() {
            return MOCKS.connectionFactory;
        }
    }
}
