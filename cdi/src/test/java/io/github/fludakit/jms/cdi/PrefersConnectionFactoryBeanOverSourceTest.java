package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.JmsClient;
import io.github.fludakit.jms.support.ConnectionFactorySource;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A {@link ConnectionFactory} bean the application produces always wins over a
 * {@link ConnectionFactorySource} bean: the configured provider section is only a fallback.
 */
@ExtendWith(WeldJunit5Extension.class)
class PrefersConnectionFactoryBeanOverSourceTest {

    static final JmsMocks MOCKS = new JmsMocks();
    static final ConnectionFactory CONFIGURED = mock(ConnectionFactory.class);

    @WeldSetup
    WeldInitiator setup = WeldInitiator
            .from(JmsClientProducer.class, CachingConnectionFactoryProducer.class,
                    ApplicationFactory.class, ConfiguredSource.class)
            .build();

    @Inject
    Instance<JmsClient> clients;

    @Test
    void usesTheApplicationFactoryBean() {
        when(MOCKS.connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)).thenReturn(MOCKS.context);

        clients.get().destination("orders").send("hello");

        MOCKS.verifySent();
        verify(CONFIGURED, never()).createContext(anyInt());
    }

    public static class ApplicationFactory {

        @Produces
        public ConnectionFactory connectionFactory() {
            return MOCKS.connectionFactory;
        }
    }

    public static class ConfiguredSource implements ConnectionFactorySource {

        @Override
        public Optional<ConnectionFactory> resolve() {
            return Optional.of(CONFIGURED);
        }
    }
}
