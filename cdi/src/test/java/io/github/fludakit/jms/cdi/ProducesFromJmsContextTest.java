package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.JmsClient;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A {@link JMSContext} bean is the first resource {@link JmsClientProducer} uses: the client sends
 * through that context, and no connection factory is involved.
 */
@ExtendWith(WeldJunit5Extension.class)
class ProducesFromJmsContextTest {

    static final JmsMocks MOCKS = new JmsMocks();

    @WeldSetup
    WeldInitiator setup = WeldInitiator
            .from(JmsClientProducer.class, CachingConnectionFactoryProducer.class, JmsContextBean.class)
            .build();

    @Inject
    Instance<JmsClient> clients;

    @Test
    void sendsThroughTheInjectedContext() {
        clients.get().destination("orders").send("hello");

        MOCKS.verifySent();
    }

    public static class JmsContextBean {

        @Produces
        public JMSContext jmsContext() {
            return MOCKS.context;
        }
    }
}
