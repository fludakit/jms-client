package io.github.fludakit.jms.config;

import io.github.fludakit.jms.JmsConfig;
import io.smallrye.config.inject.ConfigExtension;
import jakarta.jms.Session;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Maps the {@code fluda.jms.*} properties of {@code microprofile-config.properties} to the
 * {@link JmsConfig} bean the {@code cdi} module consumes, and confirms that unset or empty
 * properties leave the corresponding setting unset instead of failing the deployment.
 *
 * <p>Plain Weld SE rather than {@code weld-junit5}: the "empty" case sets system properties, which
 * {@code weld-junit5} would read only after it has already started the container.</p>
 */
class JmsConfigProducerTest {

    @AfterEach
    void clearProperties() {
        clear("fluda.jms.username", "fluda.jms.password",
                "fluda.jms.acknowledge-mode", "fluda.jms.default-destination");
    }

    @Test
    void mapsConfiguredProperties() {
        WeldContainer container = weld().initialize();
        try {
            JmsConfig config = container.select(JmsConfig.class).get();

            assertEquals("admin", config.username());
            assertEquals("secret", config.password());
            assertEquals(Session.CLIENT_ACKNOWLEDGE, config.sessionMode());
            assertEquals("orders", config.defaultDestinationName());
        } finally {
            container.close();
        }
    }

    @Test
    void treatsEmptyPropertiesAsUnset() {
        setEmpty("fluda.jms.username", "fluda.jms.password",
                "fluda.jms.acknowledge-mode", "fluda.jms.default-destination");

        WeldContainer container = weld().initialize();
        try {
            JmsConfig config = container.select(JmsConfig.class).get();

            assertNull(config.username(), "an empty username must not authenticate the context");
            assertNull(config.password());
            assertNull(config.defaultDestinationName());
            assertEquals(Session.AUTO_ACKNOWLEDGE, config.sessionMode());
        } finally {
            container.close();
        }
    }

    @Test
    void treatsBlankValuesAsUnset() {
        // the username is the one that matters: blank must not become an authenticated context
        assertNull(JmsConfigProducer.nullIfBlank(null));
        assertNull(JmsConfigProducer.nullIfBlank(""));
        assertNull(JmsConfigProducer.nullIfBlank("   "));
        assertEquals("admin", JmsConfigProducer.nullIfBlank("admin"));
    }

    private static Weld weld() {
        return new Weld()
                .disableDiscovery()
                .addExtensions(new ConfigExtension())
                .addBeanClasses(JmsConfigProducer.class, JmsProperties.class);
    }

    private static void setEmpty(String... keys) {
        for (String key : keys) {
            System.setProperty(key, "");
        }
    }

    private static void clear(String... keys) {
        for (String key : keys) {
            System.clearProperty(key);
        }
    }
}
