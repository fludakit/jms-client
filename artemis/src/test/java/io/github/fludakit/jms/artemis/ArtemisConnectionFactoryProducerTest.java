package io.github.fludakit.jms.artemis;

import io.github.fludakit.jms.config.JmsProperties;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArtemisConnectionFactoryProducerTest {

    @AfterEach
    void clearProperties() {
        clear("fluda.jms.username", "fluda.jms.password");
        clear("fluda.jms.artemis.broker-url", "fluda.jms.artemis.username", "fluda.jms.artemis.password");
    }

    @Test
    void createsTheFactoryFromItsSection() {
        System.setProperty("fluda.jms.artemis.broker-url", "tcp://broker:61616");
        System.setProperty("fluda.jms.artemis.username", "artemis-user");
        System.setProperty("fluda.jms.artemis.password", "artemis-secret");

        var factory = assertInstanceOf(ActiveMQJMSConnectionFactory.class, resolve());

        assertEquals("artemis-user", factory.getUser());
        assertEquals("artemis-secret", factory.getPassword());
    }

    @Test
    void treatsABlankUsernameAsAnonymousAccess() {
        System.setProperty("fluda.jms.username", " ");
        System.setProperty("fluda.jms.password", " ");
        System.setProperty("fluda.jms.artemis.broker-url", "tcp://broker:61616");

        var factory = assertInstanceOf(ActiveMQJMSConnectionFactory.class, resolve());

        assertNull(factory.getUser());
        assertNull(factory.getPassword());
    }

    @Test
    void fallsBackToTheSharedCredentials() {
        System.setProperty("fluda.jms.username", "shared-user");
        System.setProperty("fluda.jms.password", "shared-secret");
        System.setProperty("fluda.jms.artemis.broker-url", "tcp://broker:61616");

        var factory = assertInstanceOf(ActiveMQJMSConnectionFactory.class, resolve());

        assertEquals("shared-user", factory.getUser());
        assertEquals("shared-secret", factory.getPassword());
    }

    @Test
    void prefersTheSectionCredentials() {
        System.setProperty("fluda.jms.username", "shared-user");
        System.setProperty("fluda.jms.password", "shared-secret");
        System.setProperty("fluda.jms.artemis.broker-url", "tcp://broker:61616");
        System.setProperty("fluda.jms.artemis.username", "artemis-user");

        var factory = assertInstanceOf(ActiveMQJMSConnectionFactory.class, resolve());

        assertEquals("artemis-user", factory.getUser());
        assertEquals("shared-secret", factory.getPassword());
    }

    @Test
    void failsFastWithoutABrokerUrl() {
        IllegalStateException error = assertThrows(IllegalStateException.class, ArtemisConnectionFactoryProducerTest::resolve);
        assertEquals("fluda.jms.artemis.broker-url must be set when fluda-jms-artemis is deployed",
                error.getMessage());
    }

    @Test
    void treatsABlankBrokerUrlAsMissing() {
        System.setProperty("fluda.jms.artemis.broker-url", "   ");

        assertThrows(IllegalStateException.class, ArtemisConnectionFactoryProducerTest::resolve);
    }

    private static ConnectionFactory resolve() {
        WeldContainer container = weld();
        try {
            return container.select(ConnectionFactory.class).get();
        } finally {
            container.close();
        }
    }

    private static WeldContainer weld() {
        return new Weld()
                .disableDiscovery()
                .addBeanClasses(ArtemisConnectionFactoryProducer.class, PropertiesProducer.class)
                .initialize();
    }

    @Dependent
    public static class PropertiesProducer {
        @Produces
        public ArtemisProperties artemisProperties() {
            ArtemisProperties props = new ArtemisProperties();
            props.brokerUrl = optional("fluda.jms.artemis.broker-url");
            props.username = optional("fluda.jms.artemis.username");
            props.password = optional("fluda.jms.artemis.password");
            return props;
        }

        @Produces
        public JmsProperties jmsProperties() {
            JmsProperties props = new JmsProperties();
            props.username = optional("fluda.jms.username");
            props.password = optional("fluda.jms.password");
            props.acknowledgeMode = optional("fluda.jms.acknowledge-mode");
            props.defaultDestination = optional("fluda.jms.default-destination");
            return props;
        }

        private Optional<String> optional(String key) {
            String value = System.getProperty(key);
            return Optional.ofNullable(value).filter(v -> !v.isBlank());
        }
    }

    private static void clear(String... keys) {
        for (String key : keys) {
            System.clearProperty(key);
        }
    }
}
