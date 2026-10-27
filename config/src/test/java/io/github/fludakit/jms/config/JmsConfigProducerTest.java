package io.github.fludakit.jms.config;

import io.github.fludakit.jms.JmsConfig;
import io.smallrye.config.inject.ConfigExtension;
import jakarta.inject.Inject;
import jakarta.jms.Session;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Maps the {@code fluda.jms.*} properties of {@code microprofile-config.properties} to the
 * {@link JmsConfig} bean the {@code cdi} module consumes.
 */
@ExtendWith(WeldJunit5Extension.class)
class JmsConfigProducerTest {

    @WeldSetup
    WeldInitiator weld = WeldInitiator.from(
                    // extensions
                    ConfigExtension.class,

                    // bean classes
                    JmsConfigProducer.class, JmsProperties.class
            )
            .build();

    @Inject
    JmsConfig config;

    @Test
    void mapsConfiguredProperties() {
        assertEquals("admin", config.username());
        assertEquals("secret", config.password());
        assertEquals(Session.CLIENT_ACKNOWLEDGE, config.sessionMode());
        assertEquals("orders", config.defaultDestinationName());
    }

    @Test
    void treatsBlankValuesAsUnset() {
        // the username is the one that matters: blank must not become an authenticated context
        assertNull(JmsConfigProducer.nullIfBlank(null));
        assertNull(JmsConfigProducer.nullIfBlank(""));
        assertNull(JmsConfigProducer.nullIfBlank("   "));
        assertEquals("admin", JmsConfigProducer.nullIfBlank("admin"));
    }
}
