package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.JmsClient;
import io.github.fludakit.jms.JmsConfig;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.verify;

/**
 * An optional {@link JmsConfig} bean (the {@code config} module produces one from
 * {@code fluda.jms.*}) is applied to the produced client: here its default destination backs
 * {@code client.destination()} without naming the destination per call.
 */
@ExtendWith(WeldJunit5Extension.class)
class AppliesJmsConfigTest {

    static final JmsMocks MOCKS = new JmsMocks();

    @WeldSetup
    WeldInitiator setup = WeldInitiator
            .from(JmsClientProducer.class, CachingConnectionFactoryProducer.class,
                    JmsContextBean.class, JmsConfigBean.class)
            .build();

    @Inject
    Instance<JmsClient> clients;

    @Test
    void resolvesTheConfiguredDefaultDestination() {
        clients.get().destination().send("hello");

        verify(MOCKS.context).createQueue("orders");
        MOCKS.verifySent();
    }

    public static class JmsContextBean {

        @Produces
        public JMSContext jmsContext() {
            return MOCKS.context;
        }
    }

    public static class JmsConfigBean {

        @Produces
        public JmsConfig jmsConfig() {
            return JmsConfig.builder().defaultDestination("orders").build();
        }
    }
}
