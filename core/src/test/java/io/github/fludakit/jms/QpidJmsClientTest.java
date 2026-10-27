package io.github.fludakit.jms;

import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end send/receive against an Artemis broker through Apache Qpid JMS (AMQP 1.0),
 * including cross-client interop with the Artemis native client (produce with one stack,
 * consume with the other). The broker comes from Testcontainers' Artemis module; Docker-gated
 * like every broker-backed suite.
 */
@Testcontainers(disabledWithoutDocker = true)
class QpidJmsClientTest {

    @Container
    static final ArtemisContainer artemis = new ArtemisContainer("apache/activemq-artemis:2.40.0");

    /** The broker exposes a dedicated AMQP-only acceptor next to the CORE protocol one. */
    private static final int AMQP_PORT = 5672;

    private final JmsClient qpidClient = JmsClient.builder(qpidConnectionFactory()).build();
    private final JmsClient nativeClient = JmsClient.builder(new ActiveMQJMSConnectionFactory(
            artemis.getBrokerUrl(), artemis.getUser(), artemis.getPassword())).build();

    private static JmsConnectionFactory qpidConnectionFactory() {
        JmsConnectionFactory factory = new JmsConnectionFactory(
                "amqp://" + artemis.getHost() + ":" + artemis.getMappedPort(AMQP_PORT));
        factory.setUsername(artemis.getUser());
        factory.setPassword(artemis.getPassword());
        return factory;
    }

    @BeforeEach
    void drainTestQueue() {
        while (qpidClient.destination("test.queue")
                .receiveOptional(Object.class, Duration.ofMillis(200)).isPresent()) {
            // discard messages left behind by a previous test
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
