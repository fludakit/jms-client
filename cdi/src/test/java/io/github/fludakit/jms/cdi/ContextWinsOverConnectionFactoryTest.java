package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.JmsClient;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.verifyNoInteractions;

/**
 * When both resources exist the injected context wins: on Jakarta EE it is container-managed and
 * already configured, so the client must not open a second context (and a second connection)
 * through the factory.
 */
@ExtendWith(WeldJunit5Extension.class)
class ContextWinsOverConnectionFactoryTest {

    static final JmsMocks MOCKS = new JmsMocks();

    @WeldSetup
    WeldInitiator setup = WeldInitiator
            .from(JmsClientProducer.class, CachingConnectionFactoryProducer.class,
                    JmsContextBean.class, ConnectionFactoryBean.class)
            .build();

    @Inject
    Instance<JmsClient> clients;

    @Test
    void neverTouchesTheConnectionFactory() {
        clients.get().destination("orders").send("hello");

        MOCKS.verifySent();
        verifyNoInteractions(MOCKS.connectionFactory);
    }

    public static class JmsContextBean {

        @Produces
        public JMSContext jmsContext() {
            return MOCKS.context;
        }
    }

    public static class ConnectionFactoryBean {

        @Produces
        public ConnectionFactory connectionFactory() {
            return MOCKS.connectionFactory;
        }
    }
}
