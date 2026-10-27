package io.github.fludakit.jms.support;

import io.github.fludakit.jms.JmsClientException;

import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import java.util.Objects;

/**
 * JSON converter over Jakarta JSON Binding: payloads travel as the body of a {@link TextMessage},
 * and the payload class is published in the {@value JsonbMessageConverter#TYPE_ID_PROPERTY} property so
 * a consumer that is not told the type can still rebuild it.
 *
 * <p>Registering this converter is opt-in - the client's default remains
 * {@link SimpleMessageConverter}:</p>
 *
 * <pre>{@code
 * JmsClient client = JmsClient.builder(connectionFactory)
 *         .messageConverter(new JsonbMessageConverter())
 *         .build();
 * }</pre>
 *
 * <p><b>Optional dependency:</b> the JSON-B API is {@code provided} and no provider is required to
 * load this class - Jakarta JSON Binding types are named inline (fully qualified), and no catch
 * clause names one, so the class links without them. A project without JSON-B on the classpath can
 * therefore load this class safely; constructing the converter without a deployable provider fails
 * fast with a {@link JmsClientException} naming the missing piece (rather than a
 * {@link NoClassDefFoundError} from a class-load). Reflective enumeration of the members
 * ({@code getConstructors()}) does need JSON-B, because the public API mentions {@code Jsonb}.</p>
 *
 * <p><b>Type ids:</b> sending ALWAYS writes the payload's class name into {@code _type} - the
 * untyped {@link #fromMessage(Message)} has nothing else to go on, so it is a must. Receiving
 * prefers the type the caller asked for and only uses {@code _type} when it names a subtype of
 * that type - so a receive never instantiates a class the caller did not ask for, and a
 * {@code receive(Order.class)} still works for a message that carries no {@code _type} at all.</p>
 */
public class JsonbMessageConverter implements MessageConverter {

    /** The message property carrying the payload class name. */
    public static final String TYPE_ID_PROPERTY = "_type";

    private final jakarta.json.bind.Jsonb jsonb;

    /** Creates a converter around the JSON-B provider found by {@code JsonbBuilder.create()}. */
    public JsonbMessageConverter() {
        this(createJsonb());
    }

    /** Creates a converter around an application-configured {@code Jsonb} instance. */
    public JsonbMessageConverter(jakarta.json.bind.Jsonb jsonb) {
        this.jsonb = Objects.requireNonNull(jsonb, "jsonb must not be null");
    }

    /**
     * Creates the provider-backed {@code Jsonb}.
     *
     * <p>The method is fully qualified and catches only {@code java.base} types on purpose: naming
     * {@link jakarta.json.bind.JsonbException} anywhere (even in a signature) would force the JVM
     * to resolve it while linking this class, which fails when JSON-B is absent. {@code JsonbException}
     * extends {@code RuntimeException}, so the {@code RuntimeException} handler still covers it.</p>
     */
    private static jakarta.json.bind.Jsonb createJsonb() {
        try {
            return jakarta.json.bind.JsonbBuilder.create();
        } catch (LinkageError | RuntimeException | java.util.ServiceConfigurationError e) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "No Jakarta JSON Binding provider is deployed; add one to the runtime classpath "
                            + "(for example org.eclipse:yasson) or pass a Jsonb instance", e);
        }
    }

    @Override
    public Message toMessage(Object payload, JMSContext context) {
        if (payload == null) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "JSON payload must not be null");
        }
        Objects.requireNonNull(context, "context must not be null");
        try {
            TextMessage message = context.createTextMessage(jsonb.toJson(payload));
            // always: the untyped receive path has nothing else to rebuild the payload from
            message.setStringProperty(TYPE_ID_PROPERTY, payload.getClass().getName());
            return message;
        } catch (JMSException e) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Failed to create the JSON message", e);
        } catch (RuntimeException e) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Failed to serialize " + payload.getClass().getName() + " to JSON", e);
        }
    }

    /**
     * Rebuilds the payload as the class named by {@code _type}.
     *
     * @throws JmsClientException with {@link JmsClientException.Code#CONVERSION} when the message
     *                            carries no {@code _type} - use {@link #fromMessage(Message, Class)}
     *                            when the caller already knows the payload type
     */
    @Override
    public Object fromMessage(Message message) {
        String typeId = readTypeId(message);
        if (typeId == null) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Message carries no '" + TYPE_ID_PROPERTY
                            + "' property; receive into a known type instead");
        }
        return read(message, resolveTypeId(typeId));
    }

    /**
     * Rebuilds the payload as {@code payloadType}, or as the subtype named by {@code _type} when
     * one is published and is assignable to {@code payloadType}.
     */
    @Override
    public <T> T fromMessage(Message message, Class<T> payloadType) {
        Objects.requireNonNull(payloadType, "payloadType must not be null");
        Class<?> target = payloadType;
        String typeId = readTypeId(message);
        if (typeId != null) {
            Class<?> hinted = resolveTypeId(typeId);
            if (payloadType.isAssignableFrom(hinted)) {
                target = hinted;
            }
        }
        Object payload = read(message, target);
        return payloadType.cast(payload);
    }

    private Object read(Message message, Class<?> type) {
        String json = textOf(message);
        try {
            return jsonb.fromJson(json, type);
        } catch (RuntimeException e) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Failed to deserialize JSON into " + type.getName(), e);
        }
    }

    private String textOf(Message message) {
        if (!(message instanceof TextMessage text)) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "JSON conversion requires a text message, got " + message.getClass().getName());
        }
        try {
            return text.getText();
        } catch (JMSException e) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Failed to read the JSON message body", e);
        }
    }

    private String readTypeId(Message message) {
        try {
            return message.getStringProperty(TYPE_ID_PROPERTY);
        } catch (JMSException e) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Failed to read the '" + TYPE_ID_PROPERTY + "' property", e);
        }
    }

    private Class<?> resolveTypeId(String typeId) {
        try {
            // initialize=false: resolving a name a producer chose must not run its static
            // initializers
            return Class.forName(typeId, false, classLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION,
                    "Unknown '" + TYPE_ID_PROPERTY + "' type id: " + typeId, e);
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : JsonbMessageConverter.class.getClassLoader();
    }
}
