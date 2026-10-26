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
 * }</pre>
 *
 * <p>The client operates on a {@link JMSContext} (the JMS 2.0+ simplified API): created from the
 * ConnectionFactory in Java SE, or passed in as an injected, container-managed context on
 * Jakarta EE. A {@code JmsClient} is <b>not thread-safe</b> - it shares one JMSContext across
 * all its operations; for concurrent producers or consumers, create one client per thread.</p>
 *
 * <p>The client does <b>not</b> manage the JMSContext lifecycle - creating, closing and caching
 * the context belongs to the application (or the CDI container for an injected context).
 * Applications that need explicit cleanup create the context themselves via
 * {@code connectionFactory.createContext(...)} and use {@link #builder(JMSContext)}.</p>
 */
public class JmsClient {

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
     * Starts building a client for the supplied ConnectionFactory (Java SE usage): {@link
     * Builder#build()} creates the JMSContext through the ConnectionFactory with the builder's
     * credentials and session mode (default {@code AUTO_ACKNOWLEDGE}). The context lifecycle
     * belongs to the application.
     */
    public static Builder builder(ConnectionFactory connectionFactory) {
        return new Builder(Objects.requireNonNull(connectionFactory, "connectionFactory must not be null"));
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
            return converter.fromMessage(message, payloadType);
        }
    }

    /**
     * Builder for a {@link JmsClient}: either ConnectionFactory-based (Java SE - the client
     * creates and owns its JMSContext) or JMSContext-based (Java EE - an injected,
     * container-managed context is used and left open).
     */
    public static final class Builder {

        private final ConnectionFactory connectionFactory;
        private final JMSContext context;
        private String username;
        private String password;
        private int sessionMode = Session.AUTO_ACKNOWLEDGE;
        private String defaultDestinationName;
        private MessageConverter converter = new SimpleMessageConverter();
        private DestinationResolver destinationResolver = new SimpleDestinationResolver();

        private Builder(ConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
            this.context = null;
        }

        private Builder(JMSContext context) {
            this.connectionFactory = null;
            this.context = context;
        }

        /**
         * Sets the broker username; setting one makes {@link #build()} create an authenticated
         * context. Ignored by a context-based builder, whose context is already authenticated.
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the broker password (default: empty); only meaningful together with
         * {@link #username(String)}.
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the acknowledge mode of the context created for this client (default
         * {@link Session#AUTO_ACKNOWLEDGE}).
         *
         * @throws IllegalArgumentException for {@link Session#SESSION_TRANSACTED}: a transacted
         *                                  session needs an explicit commit, which the fluent
         *                                  client never issues - use an injected JMSContext and
         *                                  transact around it instead
         */
        public Builder sessionMode(int sessionMode) {
            // SESSION_TRANSACTED(0) is deliberately excluded: without a commit point the client
            // would send messages into a transaction that is rolled back on close
            if (sessionMode != Session.AUTO_ACKNOWLEDGE
                    && sessionMode != Session.CLIENT_ACKNOWLEDGE
                    && sessionMode != Session.DUPS_OK_ACKNOWLEDGE) {
                throw new IllegalArgumentException("Unsupported session mode " + sessionMode
                        + "; use AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE or DUPS_OK_ACKNOWLEDGE");
            }
            this.sessionMode = sessionMode;
            return this;
        }

        /**
         * Applies a {@link JmsConfig}: its credentials, session mode and default destination for
         * a ConnectionFactory-based builder, and its default destination for a context-based one -
         * a context that already exists keeps its own credentials and session mode.
         *
         * @throws IllegalStateException when a context-based builder is given a config with
         *                               credentials or a non-default session mode
         */
        public Builder config(JmsConfig config) {
            Objects.requireNonNull(config, "config must not be null");
            if (connectionFactory == null) {
                if (config.username() != null || config.password() != null
                        || config.sessionMode() != Session.AUTO_ACKNOWLEDGE) {
                    throw new IllegalStateException("An injected JMSContext keeps its own credentials "
                            + "and session mode; only the default destination can be applied from JmsConfig");
                }
            } else {
                this.username = config.username();
                this.password = config.password();
                sessionMode(config.sessionMode());
            }
            this.defaultDestinationName = config.defaultDestinationName();
            return this;
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

        /**
         * Builds the client, creating its JMSContext when it was configured with a
         * ConnectionFactory.
         *
         * @throws JmsClientException with {@link JmsClientException.Code#CONNECTION} when the
         *                            context cannot be created
         */
        public JmsClient build() {
            JMSContext jmsContext = connectionFactory != null ? createContext() : context;
            return new JmsClient(jmsContext, connectionFactory != null, converter,
                    destinationResolver, defaultDestinationName);
        }

        private JMSContext createContext() {
            try {
                if (username == null) {
                    return connectionFactory.createContext(sessionMode);
                }
                return connectionFactory.createContext(username, password == null ? "" : password, sessionMode);
            } catch (JMSRuntimeException e) {
                throw new JmsClientException(JmsClientException.Code.CONNECTION,
                        "Failed to create the JMS context", e);
            }
        }
    }
}
