package io.github.fludakit.jms;

import io.github.fludakit.jms.support.JmsMessages;

import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end send/receive against an Artemis broker through the Artemis native client.
 * The broker comes from Testcontainers' Artemis module; Docker-gated like every broker-backed suite.
 */
@Testcontainers(disabledWithoutDocker = true)
class ArtemisNativeJmsClientTest {

    @Container
    static final ArtemisContainer artemis = new ArtemisContainer("apache/activemq-artemis:2.40.0");

    public record Order(String id) implements Serializable {
    }

    private final JmsClient client = JmsClient.builder(new ActiveMQJMSConnectionFactory(
            artemis.getBrokerUrl(), artemis.getUser(), artemis.getPassword())).build();

    @BeforeEach
    void drainTestQueue() {
        while (client.destination("test.queue")
                .receiveOptional(Object.class, Duration.ofMillis(200)).isPresent()) {
            // discard messages left behind by a previous test
        }
    }

    @Test
    void sendAndReceiveText() {
        client.destination("test.queue").send("hello artemis");

        String received = client.destination("test.queue")
                .receive(String.class, Duration.ofSeconds(5));

        assertEquals("hello artemis", received);
    }

    @Test
    void sendAndReceiveSerializablePayload() {
        client.destination("test.queue").send(new Order("order-1"));

        Order received = client.destination("test.queue")
                .receive(Order.class, Duration.ofSeconds(5));

        assertEquals(new Order("order-1"), received);
    }

    @Test
    void receiveOptionalTimesOutOnEmptyQueue() {
        var empty = client.destination("empty.queue")
                .receiveOptional(String.class, Duration.ofMillis(300));

        assertTrue(empty.isEmpty());
    }

    @Test
    void receiveTimeoutThrowsWithTimeoutCode() {
        assertThrows(JmsClientException.class, () ->
                client.destination("empty.queue").receive(String.class, Duration.ofMillis(300)));
    }

    @Test
    void browsePeeksWithoutConsuming() {
        client.destination("test.queue").send("one");
        client.destination("test.queue").send("two");

        List<String> browsed = client.destination("test.queue")
                .browse(JmsMessages::textOf);

        assertEquals(2, browsed.size());
        // browsing does not consume: the messages are still there
        assertEquals(2, client.destination("test.queue")
                .browse(JmsMessages::textOf).size());
    }

    @Test
    void postProcessorSetsProperties() {
        client.destination("test.queue")
                .send("traced", m -> JmsMessages.setStringProperty(m, "traceId", "abc-123"));

        // the property survives on the wire; read it via a raw browse before consuming
        String traceId = client.destination("test.queue")
                .browse(m -> JmsMessages.stringProperty(m, "traceId"))
                .getFirst();
        assertEquals("abc-123", traceId);
    }
}
