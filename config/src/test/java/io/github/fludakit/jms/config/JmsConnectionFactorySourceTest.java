package io.github.fludakit.jms.config;

import io.github.fludakit.jms.support.ConnectionFactorySource;
import io.smallrye.config.inject.ConfigExtension;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The provider sections of {@link JmsProperties} turned into a real Artemis or Qpid factory by
 * {@link JmsConnectionFactorySource} - including the two gates: the section must be configured
 * <em>and</em> its client must be on the classpath.
 *
 * <p>Plain Weld SE rather than {@code weld-junit5}: the properties are per test and
 * {@code weld-junit5} starts the container before {@code @BeforeEach}. Each test gets a fresh
 * container and clears the system properties afterwards.</p>
 */
class JmsConnectionFactorySourceTest {

    @AfterEach
    void clearProperties() {
        clear("fluda.jms.username", "fluda.jms.password");
        clear("fluda.jms.artemis.broker-url", "fluda.jms.artemis.username", "fluda.jms.artemis.password");
        clear("fluda.jms.qpid.remote-url", "fluda.jms.qpid.username", "fluda.jms.qpid.password");
    }

    @Test
    void createsTheArtemisFactoryFromItsSection() {
        System.setProperty("fluda.jms.artemis.broker-url", "tcp://broker:61616");
        System.setProperty("fluda.jms.artemis.username", "artemis-user");
        System.setProperty("fluda.jms.artemis.password", "artemis-secret");

        var factory = assertInstanceOf(ActiveMQJMSConnectionFactory.class, resolve().orElseThrow());

        assertEquals("artemis-user", factory.getUser());
        assertEquals("artemis-secret", factory.getPassword());
    }

    @Test
    void createsTheQpidFactoryFromItsSection() {
        System.setProperty("fluda.jms.qpid.remote-url", "amqp://broker:5672");
        System.setProperty("fluda.jms.qpid.username", "qpid-user");
        System.setProperty("fluda.jms.qpid.password", "qpid-secret");

        var factory = assertInstanceOf(JmsConnectionFactory.class, resolve().orElseThrow());

        assertTrue(factory.getRemoteURI().contains("broker:5672"), factory.getRemoteURI());
        assertEquals("qpid-user", factory.getUsername());
        assertEquals("qpid-secret", factory.getPassword());
    }

    @Test
    void treatsABlankUsernameAsAnonymousAccess() {
        System.setProperty("fluda.jms.username", " ");
        System.setProperty("fluda.jms.password", " ");
        System.setProperty("fluda.jms.artemis.broker-url", "tcp://broker:61616");

        var factory = assertInstanceOf(ActiveMQJMSConnectionFactory.class, resolve().orElseThrow());

        assertNull(factory.getUser());
        assertNull(factory.getPassword());
    }

    @Test
    void fallsBackToTheSharedCredentials() {
        System.setProperty("fluda.jms.username", "shared-user");
        System.setProperty("fluda.jms.password", "shared-secret");
        System.setProperty("fluda.jms.artemis.broker-url", "tcp://broker:61616");

        var factory = assertInstanceOf(ActiveMQJMSConnectionFactory.class, resolve().orElseThrow());

        assertEquals("shared-user", factory.getUser());
        assertEquals("shared-secret", factory.getPassword());
    }

    @Test
    void prefersTheSectionCredentials() {
        System.setProperty("fluda.jms.username", "shared-user");
        System.setProperty("fluda.jms.password", "shared-secret");
        System.setProperty("fluda.jms.artemis.broker-url", "tcp://broker:61616");
        System.setProperty("fluda.jms.artemis.username", "artemis-user");

        var factory = assertInstanceOf(ActiveMQJMSConnectionFactory.class, resolve().orElseThrow());

        assertEquals("artemis-user", factory.getUser());
        assertEquals("shared-secret", factory.getPassword());
    }

    @Test
    void resolvesNothingWithoutAProviderSection() {
        assertEquals(Optional.empty(), resolve());
    }

    @Test
    void prefersArtemisWhenBothProvidersAreDeployed() {
        System.setProperty("fluda.jms.artemis.broker-url", "tcp://broker:61616");
        System.setProperty("fluda.jms.qpid.remote-url", "amqp://broker:5672");

        assertInstanceOf(ActiveMQJMSConnectionFactory.class, resolve().orElseThrow());
    }

    @Test
    void knowsWhetherTheProviderClientsAreOnTheClasspath() {
        assertTrue(JmsConnectionFactorySource.isOnClasspath(
                JmsConnectionFactorySource.ARTEMIS_CONNECTION_FACTORY_CLASS));
        assertTrue(JmsConnectionFactorySource.isOnClasspath(
                JmsConnectionFactorySource.QPID_CONNECTION_FACTORY_CLASS));

        assertFalse(JmsConnectionFactorySource.isOnClasspath("com.example.NoSuchConnectionFactory"));
    }

    @Test
    void treatsAProviderMissingFromTheClassLoaderAsAbsent() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            // an application that ships neither client cannot load either factory class
            Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0], null));
            assertFalse(JmsConnectionFactorySource.isOnClasspath(
                    JmsConnectionFactorySource.ARTEMIS_CONNECTION_FACTORY_CLASS));
            assertFalse(JmsConnectionFactorySource.isOnClasspath(
                    JmsConnectionFactorySource.QPID_CONNECTION_FACTORY_CLASS));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private static Optional<ConnectionFactory> resolve() {
        WeldContainer container = weld();
        try {
            return container.select(ConnectionFactorySource.class).get().resolve();
        } finally {
            container.close();
        }
    }

    private static WeldContainer weld() {
        return new Weld()
                .disableDiscovery()
                .addExtensions(new ConfigExtension())
                .addBeanClasses(JmsConnectionFactorySource.class, JmsProperties.class)
                .initialize();
    }

    private static void clear(String... keys) {
        for (String key : keys) {
            System.clearProperty(key);
        }
    }
}
