package io.github.fludakit.jms.qpid;

import io.github.fludakit.jms.config.JmsProperties;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.jms.ConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QpidConnectionFactoryProducerTest {

    @AfterEach
    void clearProperties() {
        clear("fluda.jms.username", "fluda.jms.password");
        clear("fluda.jms.qpid.remote-uri", "fluda.jms.qpid.username", "fluda.jms.qpid.password");
    }

    @Test
    void createsTheFactoryFromItsSection() {
        System.setProperty("fluda.jms.qpid.remote-uri", "amqp://broker:5672");
        System.setProperty("fluda.jms.qpid.username", "qpid-user");
        System.setProperty("fluda.jms.qpid.password", "qpid-secret");

        var factory = assertInstanceOf(JmsConnectionFactory.class, resolve());

        assertTrue(factory.getRemoteURI().contains("broker:5672"), factory.getRemoteURI());
        assertEquals("qpid-user", factory.getUsername());
        assertEquals("qpid-secret", factory.getPassword());
    }

    @Test
    void treatsABlankUsernameAsAnonymousAccess() {
        System.setProperty("fluda.jms.username", " ");
        System.setProperty("fluda.jms.password", " ");
        System.setProperty("fluda.jms.qpid.remote-uri", "amqp://broker:5672");

        var factory = assertInstanceOf(JmsConnectionFactory.class, resolve());

        assertNull(factory.getUsername());
        assertNull(factory.getPassword());
    }

    @Test
    void fallsBackToTheSharedCredentials() {
        System.setProperty("fluda.jms.username", "shared-user");
        System.setProperty("fluda.jms.password", "shared-secret");
        System.setProperty("fluda.jms.qpid.remote-uri", "amqp://broker:5672");

        var factory = assertInstanceOf(JmsConnectionFactory.class, resolve());

        assertEquals("shared-user", factory.getUsername());
        assertEquals("shared-secret", factory.getPassword());
    }

    @Test
    void prefersTheSectionCredentials() {
        System.setProperty("fluda.jms.username", "shared-user");
        System.setProperty("fluda.jms.password", "shared-secret");
        System.setProperty("fluda.jms.qpid.remote-uri", "amqp://broker:5672");
        System.setProperty("fluda.jms.qpid.username", "qpid-user");

        var factory = assertInstanceOf(JmsConnectionFactory.class, resolve());

        assertEquals("qpid-user", factory.getUsername());
        assertEquals("shared-secret", factory.getPassword());
    }

    @Test
    void failsFastWithoutARemoteUri() {
        IllegalStateException error = assertThrows(IllegalStateException.class, QpidConnectionFactoryProducerTest::resolve);
        assertEquals("fluda.jms.qpid.remote-uri must be set when fluda-jms-qpid is deployed",
                error.getMessage());
    }

    @Test
    void treatsABlankRemoteUriAsMissing() {
        System.setProperty("fluda.jms.qpid.remote-uri", "   ");

        assertThrows(IllegalStateException.class, QpidConnectionFactoryProducerTest::resolve);
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
                .addBeanClasses(QpidConnectionFactoryProducer.class, PropertiesProducer.class)
                .initialize();
    }

    @Dependent
    public static class PropertiesProducer {
        @Produces
        public QpidProperties qpidProperties() {
            QpidProperties props = new QpidProperties();
            props.remoteUri = optional("fluda.jms.qpid.remote-uri");
            props.username = optional("fluda.jms.qpid.username");
            props.password = optional("fluda.jms.qpid.password");
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
