package io.github.fludakit.jms;

import io.github.fludakit.jms.support.DestinationResolver;
import io.github.fludakit.jms.support.MessageConverter;
import io.github.fludakit.jms.support.SimpleDestinationResolver;
import io.github.fludakit.jms.support.SimpleMessageConverter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
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
 * {@code connectionFactory.createContext(...)} and use {@link #builder(JMSContext)}. A Java SE
 * application that wants one connection reused across clients should wrap its factory in
 * {@link CachingConnectionFactory}: it caches a context per thread and leaves this client's API
 * unchanged.</p>
 *
 * <p><b>Jakarta EE:</b> no client configuration is involved at all. The application declares its
 * resources ({@code @JMSConnectionFactoryDefinition} / {@code @JMSDestinationDefinition}, or the
 * equivalent deployment descriptors), injects them with {@code @Resource}, and passes the injected
 * context to {@link #builder(JMSContext)}. Destinations are injected as {@link Destination}
 * objects and handed to {@link #destination(Destination)} - naming a destination would resolve it
 * through the context instead, which ignores the resource definition (its address, its queue-vs-topic
 * kind and any provider-specific settings). Credentials and session mode are meaningless there:
 * the container owns them.</p>
 *
 * <p><b>The default destination is an exception worth knowing about:</b> it is always a
 * <i>name</i>, so {@link #destination()} resolves it through the
 * {@link DestinationResolver} like any other name - including on Jakarta EE, where the default
 * resolver creates the destination from the injected context instead of looking up the resource.
 * {@code defaultDestination("orders")} therefore gives you the context's view of a destination
 * named {@code orders}, not the destination an application server was configured with. That is
 * fine when the two coincide; when the resource definition matters (a specific address, a topic,
 * a provider-specific setting), keep {@code destination()} for Java SE convenience and pass the
 * injected {@link Destination} explicitly: {@code client.destination(orders)}.</p>
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
     * Starts building a client for the supplied ConnectionFactory (Java SE usage):
     * {@link ConnectionFactorySpec#build()} creates the JMSContext through the factory with the
     * spec's credentials and session mode (default {@code AUTO_ACKNOWLEDGE}). The context lifecycle
     * belongs to the application.
     *
     * <p>Wrap a raw factory in {@link CachingConnectionFactory} to reuse one context - and one
     * connection - per thread across clients; this method takes either kind of factory.</p>
     */
    public static ConnectionFactorySpec builder(ConnectionFactory connectionFactory) {
        return new ConnectionFactorySpec(Objects.requireNonNull(connectionFactory, "connectionFactory must not be null"));
    }

    /**
     * Starts building a client around an existing (possibly container-injected) JMSContext. The
     * context lifecycle belongs to the application or the container.
     */
    public static JmsContextSpec builder(JMSContext context) {
        return new JmsContextSpec(Objects.requireNonNull(context, "context must not be null"));
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

    /**
     * Targets an explicit {@link Destination} (queue or topic) for subsequent operations.
     */
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

    /**
     * The {@link MessageConverter} this client converts payloads with.
     */
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

        /**
         * Converts the payload and sends it to the destination.
         */
        public void send(Object payload) {
            send(payload, m -> {
            });
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
            } catch (JMSException e) {
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
     * Options shared by both client specs - the default destination, the payload converter and the
     * destination resolver - plus the terminal {@link #build()}.
     *
     * @param <S> the concrete spec type, so every fluent method keeps returning this spec
     */
    public abstract static class AbstractJmsClientSpec<S extends AbstractJmsClientSpec<S>> {

        private String defaultDestinationName;
        private MessageConverter converter = new SimpleMessageConverter();
        private DestinationResolver destinationResolver = new SimpleDestinationResolver();

        AbstractJmsClientSpec() {
        }

        /**
         * Sets a default destination name for the convenience {@link JmsClient#destination()}
         * operation.
         *
         * <p><b>Optional</b> - and usually left unset: callers normally name the destination per
         * call ({@link JmsClient#destination(String)}) or pass one they already hold
         * ({@link JmsClient#destination(Destination)}). Nothing else in the client depends on it.</p>
         *
         * <p>The value is always a name, resolved by the {@link DestinationResolver} when
         * {@code destination()} is called. On Jakarta EE that means it goes through
         * {@code JMSContext.createQueue}/{@code createTopic} of the injected context, <b>not</b>
         * through the {@code @JMSDestinationDefinition} resource - see the class documentation.
         * Pass the injected {@link Destination} to {@link JmsClient#destination(Destination)} when
         * the resource definition must be honoured.</p>
         */
        public final S defaultDestination(String defaultDestinationName) {
            this.defaultDestinationName = defaultDestinationName;
            return self();
        }

        /**
         * Sets the MessageConverter (default {@link SimpleMessageConverter}).
         */
        public final S messageConverter(MessageConverter converter) {
            this.converter = Objects.requireNonNull(converter, "converter must not be null");
            return self();
        }

        /**
         * Sets the strategy resolving destination names to {@link Destination}s
         * (default {@link SimpleDestinationResolver}, like Spring's).
         */
        public final S destinationResolver(DestinationResolver destinationResolver) {
            this.destinationResolver = Objects.requireNonNull(destinationResolver, "destinationResolver must not be null");
            return self();
        }

        /**
         * Applies the parts of {@code config} that are valid for this spec.
         *
         * @throws IllegalStateException when the config carries options this spec cannot apply
         */
        public abstract S config(JmsConfig config);

        /**
         * Builds the client.
         *
         * @throws JmsClientException with {@link JmsClientException.Code#CONNECTION} when a
         *                            context has to be created and that fails
         */
        public JmsClient build() {
            return new JmsClient(context(), converter, destinationResolver, defaultDestinationName);
        }

        /** The context the built client uses: created for a factory spec, given for a context spec. */
        protected abstract JMSContext context();

        /** Applies the option both specs share. */
        final void defaultDestinationFrom(JmsConfig config) {
            this.defaultDestinationName = config.defaultDestinationName();
        }

        @SuppressWarnings("unchecked")
        private S self() {
            return (S) this;
        }
    }

    /**
     * Builder for a client that creates its own JMSContext from a {@link ConnectionFactory} (Java
     * SE usage): {@link #build()} calls {@code createContext} with the configured credentials and
     * session mode. The context lifecycle belongs to the application.
     *
     * <p>With a raw factory each client opens its own connection, because the client never closes
     * the context. Pass a {@link CachingConnectionFactory} instead to reuse one context per thread
     * - the credentials and session mode configured here become part of its cache key.</p>
     */
    public static final class ConnectionFactorySpec extends AbstractJmsClientSpec<ConnectionFactorySpec> {

        private final ConnectionFactory connectionFactory;
        private String username;
        private String password;
        private int sessionMode = Session.AUTO_ACKNOWLEDGE;

        private ConnectionFactorySpec(ConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
        }

        /**
         * Sets the broker username; setting one makes {@link #build()} create an authenticated
         * context.
         */
        public ConnectionFactorySpec username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the broker password (default: empty); only meaningful together with
         * {@link #username(String)}.
         */
        public ConnectionFactorySpec password(String password) {
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
        public ConnectionFactorySpec sessionMode(int sessionMode) {
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
         * Applies a {@link JmsConfig}: its credentials, session mode and default destination.
         */
        @Override
        public ConnectionFactorySpec config(JmsConfig config) {
            Objects.requireNonNull(config, "config must not be null");
            this.username = config.username();
            this.password = config.password();
            sessionMode(config.sessionMode());
            defaultDestinationFrom(config);
            return this;
        }

        @Override
        protected JMSContext context() {
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

    /**
     * Builder for a client that uses an existing {@link JMSContext} - injected by a container or
     * created by the application. {@link #build()} never creates or closes it.
     */
    public static final class JmsContextSpec extends AbstractJmsClientSpec<JmsContextSpec> {

        private final JMSContext jmsContext;

        private JmsContextSpec(JMSContext jmsContext) {
            this.jmsContext = jmsContext;
        }

        /**
         * Applies the default destination of {@code config}; a context that already exists keeps
         * its own credentials and session mode.
         *
         * @throws IllegalStateException when the config carries credentials or a non-default
         *                               session mode, which this spec cannot apply
         */
        @Override
        public JmsContextSpec config(JmsConfig config) {
            Objects.requireNonNull(config, "config must not be null");
            if (config.username() != null || config.password() != null
                    || config.sessionMode() != Session.AUTO_ACKNOWLEDGE) {
                throw new IllegalStateException("An injected JMSContext keeps its own credentials "
                        + "and session mode; only the default destination can be applied from JmsConfig");
            }
            defaultDestinationFrom(config);
            return this;
        }

        @Override
        protected JMSContext context() {
            return jmsContext;
        }
    }
}
