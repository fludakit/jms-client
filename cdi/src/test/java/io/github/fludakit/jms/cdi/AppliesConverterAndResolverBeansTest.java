package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.JmsClient;
import io.github.fludakit.jms.support.DestinationResolver;
import io.github.fludakit.jms.support.MessageConverter;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.jms.Destination;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Optional {@link MessageConverter} and {@link DestinationResolver} beans are applied to the
 * produced client: here the converter builds the message and the resolver supplies the
 * destination, so neither default takes part.
 */
@ExtendWith(WeldJunit5Extension.class)
class AppliesConverterAndResolverBeansTest {

    static final JmsMocks MOCKS = new JmsMocks();

    static final Message CONVERTED = mock(Message.class);

    static final Queue RESOLVED = mock(Queue.class);

    @WeldSetup
    WeldInitiator setup = WeldInitiator
            .from(JmsClientProducer.class, CachingConnectionFactoryProducer.class,
                    JmsContextBean.class, ConverterBean.class, ResolverBean.class)
            .build();

    @Inject
    Instance<JmsClient> clients;

    @Test
    void usesTheConverterBeanAndTheResolverBean() {
        clients.get().destination("orders").send("hello");

        verify(MOCKS.producer).send(RESOLVED, CONVERTED);
        verify(MOCKS.context, never()).createQueue(anyString());
        verify(MOCKS.context, never()).createTextMessage(anyString());
    }

    public static class JmsContextBean {

        @Produces
        public JMSContext jmsContext() {
            return MOCKS.context;
        }
    }

    public static class ConverterBean {

        @Produces
        public MessageConverter messageConverter() {
            return new MessageConverter() {

                @Override
                public Message toMessage(Object payload, JMSContext context) {
                    return CONVERTED;
                }

                @Override
                public Object fromMessage(Message message) {
                    return "converted";
                }
            };
        }
    }

    public static class ResolverBean {

        @Produces
        public DestinationResolver destinationResolver() {
            return (context, name) -> (Destination) RESOLVED;
        }
    }
}
