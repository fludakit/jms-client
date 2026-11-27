# Message Converter

`MessageConverter` turns payloads into JMS messages and back. The client uses it in `send` and `receive` operations.

## Interface

```java
public interface MessageConverter {

    Message toMessage(Object payload, JMSContext context);

    Object fromMessage(Message message);

    default <T> T fromMessage(Message message, Class<T> payloadType) {
        Object payload = fromMessage(message);
        if (!payloadType.isInstance(payload)) {
            throw new JmsClientException(JmsClientException.Code.CONVERSION, ...);
        }
        return payloadType.cast(payload);
    }
}
```

## Built-in converters

### SimpleMessageConverter (default)

Handles the common payload types:

| Payload | JMS message |
|---|---|
| `String` | `TextMessage` |
| `byte[]` | `BytesMessage` |
| `Map` | `MapMessage` |
| `Serializable` | `ObjectMessage` |

A payload type that the converter does not know makes `send` fail with the `CONVERSION` code.

### JsonbMessageConverter (opt-in)

Sends payloads as JSON in the body of a `TextMessage`. The payload class is published in the `_type` message property, so a consumer that is not told the type can still rebuild it.

```java
JmsClient client = JmsClient.builder(connectionFactory)
        .messageConverter(new JsonbMessageConverter())
        .build();
```

Or with a custom `Jsonb` instance:

```java
Jsonb jsonb = JsonbBuilder.create(jsonbConfig);
JmsClient client = JmsClient.builder(connectionFactory)
        .messageConverter(new JsonbMessageConverter(jsonb))
        .build();
```

JSON-B is an optional dependency: the class loads without it, and only constructing the converter without a provider fails, with a `JmsClientException` naming the missing piece.

## Custom converter

Implement `MessageConverter` for payload types that the built-ins do not cover:

```java
public class AvroMessageConverter implements MessageConverter {

    private final Schema schema;
    private final DatumWriter<GenericRecord> writer;
    private final DatumReader<GenericRecord> reader;

    // ...

    @Override
    public Message toMessage(Object payload, JMSContext context) {
        GenericRecord record = (GenericRecord) payload;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        writer.write(record, encoder);
        encoder.flush();
        BytesMessage message = context.createBytesMessage();
        message.writeBytes(out.toByteArray());
        return message;
    }

    @Override
    public Object fromMessage(Message message) {
        BytesMessage bytesMessage = (BytesMessage) message;
        byte[] data = new byte[(int) bytesMessage.getBodyLength()];
        bytesMessage.readBytes(data);
        return reader.read(null, DecoderFactory.get().binaryDecoder(data, null));
    }
}
```

## CDI integration

In a CDI environment, a single `MessageConverter` bean is picked up automatically and applied to every produced client:

```java
@ApplicationScoped
public class JmsBeans {

    @Produces
    @ApplicationScoped
    MessageConverter messageConverter() {
        return new JsonbMessageConverter();
    }
}
```

Keep one default-qualified bean per SPI — with more than one candidate, the container reports the ambiguity, so qualify the extras. The bean replaces the default application-wide; for a per-client choice use the builder in plain Java SE instead.
