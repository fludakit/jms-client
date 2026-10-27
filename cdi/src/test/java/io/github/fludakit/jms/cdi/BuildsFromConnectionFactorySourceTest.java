package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.CachingConnectionFactory;
import io.github.fludakit.jms.JmsClient;
import io.github.fludakit.jms.support.ConnectionFactorySource;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Without a {@link ConnectionFactory} bean, the client is built from a
 * {@link ConnectionFactorySource} bean - the stage the {@code config} module fills from the
 * {@code fluda.jms.artemis.*} / {@code fluda.jms.qpid.*} section. The source's factory is cached
 * like any other, so two clients on one thread share one context.
 */
@ExtendWith(WeldJunit5Extension.class)
class BuildsFromConnectionFactorySourceTest {

    static final JmsMocks MOCKS = new JmsMocks();

    @WeldSetup
    WeldInitiator setup = WeldInitiator
            .from(JmsClientProducer.class, CachingConnectionFactoryProducer.class, ConfiguredSource.class)
            .build();

    @Inject
    Instance<JmsClient> clients;

    @Inject
    CachingConnectionFactory cachingConnectionFactory;

    @Test
    void buildsFromTheConfiguredSource() {
        when(MOCKS.connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)).thenReturn(MOCKS.context);

        clients.get().destination("orders").send("hello");
        clients.get().destination("orders").send("hello again");

        verify(MOCKS.producer, times(2)).send(MOCKS.queue, MOCKS.message);
        verify(MOCKS.connectionFactory, times(1)).createContext(Session.AUTO_ACKNOWLEDGE);
        assertNotNull(cachingConnectionFactory);
    }

    public static class ConfiguredSource implements ConnectionFactorySource {

        @Override
        public Optional<ConnectionFactory> resolve() {
            return Optional.of(MOCKS.connectionFactory);
        }
    }
}
