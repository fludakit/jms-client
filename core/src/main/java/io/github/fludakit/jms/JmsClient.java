package io.github.fludakit.jms;

import io.github.fludakit.jms.support.DestinationResolver;
import io.github.fludakit.jms.support.SimpleDestinationResolver;
import io.github.fludakit.jms.support.MessageConverter;
import io.github.fludakit.jms.support.SimpleMessageConverter;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSProducer;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A fluent JMS client for sending and receiving messages - comparable to Spring's {@code JmsClient}.
 *
 * <pre>{@code
 * JmsClient client = JmsClient.builder(connectionFactory).build();
 *
 * client.destination("orders").send(order);                       // payload via MessageConverter
 * client.destination("orders").send(m -> JmsMessages.setStringProperty(m, "source", "test")); // raw message
 * Order order = client.destination("orders").receive(Order.class, Duration.ofSeconds(5));
 *
 * client.close();
 * }</pre>
 *
 * <p>The client operates on a {@link JMSContext} (the JMS 2.0+ simplified JMSContext): created
 * from the ConnectionFactory in Java SE, or passed in as an injected, container-managed context
 * on Jakarta EE. A {@code JmsClient} is <b>not thread-safe</b> - it shares one JMSContext across
 * all its operations; for concurrent producers or consumers, create one client per thread
 * (via {@code connectionFactory.createContext()}).</p>
 *
 * <p>Implements {@link AutoCloseable}: closing the client closes the underlying JMSContext.</p>
 */
public class JmsClient implements AutoCloseable {

    private final JMSContext context;
    private final MessageConverter converter;
    private final DestinationResolver destinationResolver;
    private final String defaultDestinationName;

    private JmsClient(JMSContext context, MessageConverter converter,
                      DestinationResolver destinationResolver, String defaultDestinationName) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.converter = Objects.requireNonNull(converter, "converter must not be null");
        this.destinationResolver = Objects.requireNonNull(destinationResolver, "destinationResolver must not be null");
        this.defaultDestinationName = defaultDestinationName;
    }

    /**
     * Starts building a client for the supplied ConnectionFactory (Java SE usage): the client
     * creates its JMSContext through {@code connectionFactory.createContext(AUTO_ACKNOWLEDGE)}
     * and closes it on {@link #close()} - credentials and broker settings belong on the
     * ConnectionFactory itself.
     */
    public static Builder builder(ConnectionFactory connectionFactory) {
        Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
        return new Builder(connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE));
    }

    /** Starts building a client around an existing (possibly container-injected) JMSContext. */
    public static Builder builder(JMSContext context) {
        return new Builder(Objects.requireNonNull(context, "context must not be null"));
    }

    /**
     * Targets a destination by name, resolved through the client's {@link DestinationResolver},
     * for subsequent send/receive/browse operations.
     *
     * @param name the destination name
     * @return a per-destination operation spec
     */
    public DestinationSpec destination(String name) {
        Objects.requireNonNull(name, "name must not be null");
        return new DestinationSpec(destinationResolver.resolveDestination(context, name));
    }

    /** Targets an explicit {@link Destination} (queue or topic) for subsequent operations. */
    public DestinationSpec destination(Destination destination) {
        return new DestinationSpec(Objects.requireNonNull(destination, "destination must not be null"));
    }

    /**
     * Targets the default destination configured through the builder.
     *
     * @throws IllegalStateException when no default destination was configured
     */
    public DestinationSpec destination() {
        if (defaultDestinationName == null) {
            throw new IllegalStateException(
                    "No default destination configured; set defaultDestination(...) on the builder "
                            + "or call destination(name)");
        }
        return destination(defaultDestinationName);
    }

    /** The {@link MessageConverter} this client converts payloads with. */
    public MessageConverter getMessageConverter() {
        return converter;
    }

    /** Closes the underlying JMSContext. */
    @Override
    public void close() {
        context.close();
    }

    /**
     * The per-destination operations of a {@link JmsClient} - what {@code client.destination(...)}
     * returns. Send operations convert payloads through the client's {@link MessageConverter};
     * receive operations convert into the caller's requested type.
     */
    public class DestinationSpec {

        private final Destination destination;

        private DestinationSpec(Destination destination) {
            this.destination = destination;
        }

        /** Converts the payload and sends it to the destination. */
        public void send(Object payload) {
            send(payload, m -> { });
        }

        /**
         * Converts the payload and sends it, applying the post-processor to the message first
         * (properties, headers, ...) - the {@code MessagePostProcessor} role in Spring JMS.
         */
        public void send(Object payload, Consumer<Message> postProcessor) {
            Objects.requireNonNull(postProcessor, "postProcessor must not be null");
            Message message = converter.toMessage(payload, context);
            postProcessor.accept(message);
            send(message);
        }

        /**
         * Sends a message built by the creator - the raw escape hatch when no converter matches
         * (the creator receives the client's JMSContext as the message factory).
         */
        public void send(Function<JMSContext, Message> creator) {
            Objects.requireNonNull(creator, "creator must not be null");
            send(creator.apply(context));
        }

        /**
         * Receives one message and converts it to the requested payload type, blocking up to the
         * timeout - the {@code receiveAndConvert(timeout, Class)} role in Spring JMS.
         *
         * @throws JmsClientException with {@link JmsClientException.Code#TIMEOUT} when no message
         *                            arrives in time
         */
        public <T> T receive(Class<T> payloadType, Duration timeout) {
            Objects.requireNonNull(payloadType, "payloadType must not be null");
            Objects.requireNonNull(timeout, "timeout must not be null");
            try (var consumer = context.createConsumer(destination)) {
                Message message = consumer.receive(timeout.toMillis());
                if (message == null) {
                    throw new JmsClientException(JmsClientException.Code.TIMEOUT,
                            "No message received within " + timeout.toMillis() + " ms");
                }
                return convert(message, payloadType);
            } catch (JMSRuntimeException e) {
                throw new JmsClientException(JmsClientException.Code.RECEIVE,
                        "Failed to receive a message", e);
            }
        }

        /**
         * Receives at most one message and converts it, blocking up to the timeout.
         *
         * @return the payload, or empty when no message arrived in time
         */
        public <T> Optional<T> receiveOptional(Class<T> payloadType, Duration timeout) {
            Objects.requireNonNull(payloadType, "payloadType must not be null");
            Objects.requireNonNull(timeout, "timeout must not be null");
            try (var consumer = context.createConsumer(destination)) {
                Message message = consumer.receive(timeout.toMillis());
                if (message == null) {
                    return Optional.empty();
                }
                return Optional.of(convert(message, payloadType));
            } catch (JMSRuntimeException e) {
                throw new JmsClientException(JmsClientException.Code.RECEIVE,
                        "Failed to receive a message", e);
            }
        }

        /**
         * Receives one message matching the selector and converts it, blocking up to the timeout -
         * the {@code receiveSelectedAndConvert} role in Spring JMS.
         *
         * @throws JmsClientException with {@link JmsClientException.Code#TIMEOUT} when no matching
         *                            message arrives in time
         */
        public <T> T receiveSelected(Class<T> payloadType, String selector, Duration timeout) {
            Objects.requireNonNull(payloadType, "payloadType must not be null");
            Objects.requireNonNull(selector, "selector must not be null");
            Objects.requireNonNull(timeout, "timeout must not be null");
            try (var consumer = context.createConsumer(destination, selector)) {
                Message message = consumer.receive(timeout.toMillis());
                if (message == null) {
                    throw new JmsClientException(JmsClientException.Code.TIMEOUT,
                            "No message received within " + timeout.toMillis() + " ms for selector: " + selector);
                }
                return convert(message, payloadType);
            } catch (JMSRuntimeException e) {
                throw new JmsClientException(JmsClientException.Code.RECEIVE,
                        "Failed to receive a message", e);
            }
        }

        /**
         * Browses the messages currently on the queue (peek-only, not consuming) and maps each
         * through the mapper - the {@code browse(BrowserCallback)} role in Spring JMS.
         *
         * @throws JmsClientException if the destination is not a {@link Queue}
         */
        public <R> List<R> browse(Function<Message, R> mapper) {
            Objects.requireNonNull(mapper, "mapper must not be null");
            if (!(destination instanceof Queue queue)) {
                throw new JmsClientException(JmsClientException.Code.RECEIVE,
                        "Browsing requires a Queue destination");
            }
            try (QueueBrowser browser = context.createBrowser(queue)) {
                List<R> results = new ArrayList<>();
                for (var elements = browser.getEnumeration(); elements.hasMoreElements(); ) {
                    results.add(mapper.apply((Message) elements.nextElement()));
                }
                return results;
            } catch ( JMSException e) {
                throw new JmsClientException(JmsClientException.Code.RECEIVE,
                        "Failed to browse the queue", e);
            }
        }

        private void send(Message message) {
            try {
                // JMSProducer is a lightweight, non-closeable send facade in JMS 3.1
                JMSProducer producer = context.createProducer();
                producer.send(destination, message);
            } catch (JMSRuntimeException e) {
                throw new JmsClientException(JmsClientException.Code.SEND,
                        "Failed to send the message", e);
            }
        }

        private <T> T convert(Message message, Class<T> payloadType) {
            Object value = converter.fromMessage(message);
            if (!payloadType.isInstance(value)) {
                throw new JmsClientException(JmsClientException.Code.CONVERSION,
                        "Converted payload of type " + value.getClass().getName()
                                + " is not assignable to " + payloadType.getName());
            }
            return payloadType.cast(value);
        }
    }

    /**
     * Builder for a {@link JmsClient}: accepts the JMSContext to operate on - created from a
     * ConnectionFactory via {@link JmsClient#builder(ConnectionFactory)} (Java SE), or injected
     * on Jakarta EE.
     */
    public static final class Builder {

        private final JMSContext context;
        private String defaultDestinationName;
        private MessageConverter converter = new SimpleMessageConverter();
        private DestinationResolver destinationResolver = new SimpleDestinationResolver();

        private Builder(JMSContext context) {
            this.context = Objects.requireNonNull(context, "context must not be null");
        }

        /** Sets the default destination name for {@link JmsClient#destination()} operations. */
        public Builder defaultDestination(String defaultDestinationName) {
            this.defaultDestinationName = defaultDestinationName;
            return this;
        }

        /** Sets the MessageConverter (default {@link SimpleMessageConverter}). */
        public Builder messageConverter(MessageConverter converter) {
            this.converter = Objects.requireNonNull(converter, "converter must not be null");
            return this;
        }

        /**
         * Sets the strategy resolving destination names to {@link Destination}s
         * (default {@link SimpleDestinationResolver}, like Spring's).
         */
        public Builder destinationResolver(DestinationResolver destinationResolver) {
            this.destinationResolver = Objects.requireNonNull(destinationResolver, "destinationResolver must not be null");
            return this;
        }

        /** Builds the client. */
        public JmsClient build() {
            return new JmsClient(context, converter, destinationResolver, defaultDestinationName);
        }
    }
}
