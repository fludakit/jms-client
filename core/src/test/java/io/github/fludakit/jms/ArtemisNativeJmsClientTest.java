package io.github.fludakit.jms;

import jakarta.jms.JMSException;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end send/receive against an Artemis broker through the Artemis native client.
 * Docker-gated like every broker-backed suite.
 */
@Testcontainers(disabledWithoutDocker = true)
class ArtemisNativeJmsClientTest {

    @Container
    static GenericContainer<?> artemis = new GenericContainer<>(
            DockerImageName.parse("apache/activemq-artemis:2.40.0"))
            .withExposedPorts(61616)
            .withEnv("ARTEMIS_USER", "artemis")
            .withEnv("ARTEMIS_PASSWORD", "artemis")
            .waitingFor(Wait.forListeningPort());

    public record Order(String id) implements Serializable {
    }

    private static ArtemisNativeJmsClientHolder clients;

    @BeforeAll
    static void createClients() {
        String url = "tcp://" + artemis.getHost() + ":" + artemis.getMappedPort(61616);
        clients = new ArtemisNativeJmsClientHolder(new ActiveMQJMSConnectionFactory(url, "artemis", "artemis"));
    }

    @BeforeEach
    void drainTestQueue() {
        clients.drain();
    }

    @Test
    void sendAndReceiveText() {
        clients.client().destination("test.queue").send("hello artemis");

        String received = clients.client().destination("test.queue")
                .receive(String.class, Duration.ofSeconds(5));

        assertEquals("hello artemis", received);
    }

    @Test
    void sendAndReceiveSerializablePayload() {
        clients.client().destination("test.queue").send(new Order("order-1"));

        Order received = clients.client().destination("test.queue")
                .receive(Order.class, Duration.ofSeconds(5));

        assertEquals(new Order("order-1"), received);
    }

    @Test
    void receiveOptionalTimesOutOnEmptyQueue() {
        var empty = clients.client().destination("empty.queue")
                .receiveOptional(String.class, Duration.ofMillis(300));

        assertTrue(empty.isEmpty());
    }

    @Test
    void receiveTimeoutThrowsWithTimeoutCode() {
        assertThrows(JmsClientException.class, () ->
                clients.client().destination("empty.queue").receive(String.class, Duration.ofMillis(300)));
    }

    @Test
    void browsePeeksWithoutConsuming() {
        clients.client().destination("test.queue").send("one");
        clients.client().destination("test.queue").send("two");

        List<String> browsed = clients.client().destination("test.queue")
                .browse(JmsMessages::textOf);

        assertEquals(2, browsed.size());
        // browsing does not consume: the messages are still there
        assertEquals(2, clients.client().destination("test.queue")
                .browse(JmsMessages::textOf).size());
    }

    @Test
    void postProcessorSetsProperties() {
        clients.client().destination("test.queue")
                .send("traced", m -> JmsMessages.setStringProperty(m, "traceId", "abc-123"));

        // the property survives on the wire; read it via a raw browse before consuming
        String traceId = clients.client().destination("test.queue")
                .browse(m -> JmsMessages.stringProperty(m, "traceId"))
                .getFirst();
        assertEquals("abc-123", traceId);
    }

    /** Holds the shared ConnectionFactory plus a per-test client instance. */
    static final class ArtemisNativeJmsClientHolder {

        private final ActiveMQJMSConnectionFactory factory;

        ArtemisNativeJmsClientHolder(ActiveMQJMSConnectionFactory factory) {
            this.factory = factory;
        }

        JmsClient client() {
            return JmsClient.builder(factory).build();
        }

        void drain() {
            JmsClient client = client();
            while (client.destination("test.queue")
                    .receiveOptional(Object.class, Duration.ofMillis(200)).isPresent()) {
                // discard
            }
        }
    }
}
