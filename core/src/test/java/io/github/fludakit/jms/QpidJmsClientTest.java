package io.github.fludakit.jms;

import jakarta.jms.JMSException;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end send/receive against an Artemis broker through Apache Qpid JMS (AMQP 1.0),
 * including cross-client interop with the Artemis native client (produce with one stack,
 * consume with the other). Docker-gated like every broker-backed suite.
 */
@Testcontainers(disabledWithoutDocker = true)
class QpidJmsClientTest {

    @Container
    static GenericContainer<?> artemis = new GenericContainer<>(
            DockerImageName.parse("apache/activemq-artemis:2.40.0"))
            .withExposedPorts(61616)
            .withEnv("ARTEMIS_USER", "artemis")
            .withEnv("ARTEMIS_PASSWORD", "artemis")
            .waitingFor(Wait.forListeningPort());

    private static JmsConnectionFactory qpidFactory;
    private static org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory nativeFactory;

    @BeforeAll
    static void createFactories() {
        String amqpUrl = "amqp://" + artemis.getHost() + ":" + artemis.getMappedPort(61616);
        qpidFactory = new JmsConnectionFactory(amqpUrl);
        qpidFactory.setUsername("artemis");
        qpidFactory.setPassword("artemis");

        String url = "tcp://" + artemis.getHost() + ":" + artemis.getMappedPort(61616);
        nativeFactory = new org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory(
                url, "artemis", "artemis");
    }

    private final JmsClient qpidClient = JmsClient.builder(qpidFactory).build();
    private final JmsClient nativeClient = JmsClient.builder(nativeFactory).build();

    @BeforeEach
    void drainTestQueue() {
        while (qpidClient.destination("test.queue")
                .receiveOptional(Object.class, Duration.ofMillis(200)).isPresent()) {
            // discard
        }
    }

    @Test
    void sendAndReceiveOverAmqp() {
        qpidClient.destination("test.queue").send("hello via amqp");

        String received = qpidClient.destination("test.queue")
                .receive(String.class, Duration.ofSeconds(5));

        assertEquals("hello via amqp", received);
    }

    @Test
    void produceWithQpidConsumeWithArtemisNative() {
        qpidClient.destination("test.queue").send("cross-client");

        String received = nativeClient.destination("test.queue")
                .receive(String.class, Duration.ofSeconds(5));

        assertEquals("cross-client", received);
    }
}
