package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.JmsClient;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Neither a {@link jakarta.jms.JMSContext} nor a {@link jakarta.jms.ConnectionFactory} bean: the
 * producer must fail at production with guidance, not hand out a client that only fails on the
 * first send.
 */
@ExtendWith(WeldJunit5Extension.class)
class FailsFastWithoutResourcesTest {

    @WeldSetup
    WeldInitiator setup = WeldInitiator
            .from(JmsClientProducer.class, CachingConnectionFactoryProducer.class)
            .build();

    @Inject
    Instance<JmsClient> clients;

    @Test
    void productionFailsFastWithGuidance() {
        Exception thrown = assertThrows(Exception.class, () -> clients.get());

        assertTrue(causeChainContains(thrown, "No JMS resources available"),
                "the failure must be the producer's fail-fast, not an unrelated wrap: " + thrown);
    }

    private static boolean causeChainContains(Throwable throwable, String messagePart) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(messagePart)) {
                return true;
            }
        }
        return false;
    }
}
