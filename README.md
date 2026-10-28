# FluDa :: Jms Client

A fluent JMS client for Jakarta Messaging, in the style of Spring's `JmsClient` - part of the
[FluDa Kit](https://github.com/fludakit).

## Status

v1 is implemented - see [issue #1](../../issues/1) for the requirements plan and phased delivery.

**In v1:** fluent send/receive over a `jakarta.jms.JmsContext`, a `MessageConverter` SPI
(Simple + optional JSON-B), connection/context caching, and CDI wiring for Java SE and Jakarta EE.
**Deferred to 1.1:** `@JmsListener`/`JmsListenerContainer` and the CDI event bridge.

## Modules

| Module | Responsibility |
|---|---|
| `fluda-jms-core` | `JmsClient` API, `MessageConverter` and `DestinationResolver` SPI (including `JndiDestinationResolver`), `CachingConnectionFactory` - Jakarta Messaging API only |
| `fluda-jms-cdi` | CDI producers for `JmsClient` and `CachingConnectionFactory` |
| `fluda-jms-config` | MicroProfile Config `fluda.jms.*` client-wide properties -> `JmsConfig` |
| `fluda-jms-artemis` | Artemis connection factory producer from `fluda.jms.artemis.*` properties |
| `fluda-jms-qpid` | Qpid JMS connection factory producer from `fluda.jms.qpid.*` properties |

All modules share one version. Add `fluda-jms-core` for plain Java SE:

```xml
<dependency>
    <groupId>io.github.fludakit</groupId>
    <artifactId>fluda-jms-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

For CDI, add `fluda-jms-cdi`, `fluda-jms-config` (optional, for client-wide properties), and the provider module for your broker (`fluda-jms-artemis` or `fluda-jms-qpid`).

## Usage

Build a client from the provider's connection factory. Wrap it in a `CachingConnectionFactory` to
reuse one `JMSContext` per thread:

```java
try (var factory = new ActiveMQJMSConnectionFactory("tcp://localhost:61616", "artemis", "artemis");
     var caching = new CachingConnectionFactory(factory)) {
    JmsClient client = JmsClient.builder(caching).build();
    client.destination("orders").send(new Order("order-1", "keyboard", 2));
}
```

In a CDI application, inject `JmsClient` instead - the `cdi` and `config` modules supply it.

## Documentation

The [JMS client documentation](https://fludakit.github.io/documentation/jms-client/getting-started/)
covers CDI in Java SE, Jakarta EE, configuration, converters and destination resolution.

Runnable examples live in [fludakit/examples](https://github.com/fludakit/examples):
`jms-client-vanilla`, `jms-client-artemis`, `jms-client-qpid` and `jms-client-jakartaee`.

## Build

JDK 21+, Maven wrapper included:

```bash
./mvnw verify
```

## License

Apache License 2.0 - see [LICENSE](LICENSE).
