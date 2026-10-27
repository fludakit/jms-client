# FluDa :: Jms Client

A fluent JMS client for Jakarta Messaging, in the style of Spring's `JmsClient` - part of the [FluDa Kit](https://github.com/fludakit).

## Status

v1 is implemented - see [issue #1](../../issues/1) for the requirements plan and phased delivery.

**In v1:** fluent send/receive over a `jakarta.jms.JmsContext`, a `MessageConverter` SPI (Simple + optional JSON-B), connection/context caching, and CDI wiring for Java SE and Jakarta EE. **Deferred to 1.1:** `@JmsListener`/`JmsListenerContainer` and the CDI event bridge.

## Modules

| Module | Responsibility |
|---|---|
| `fluda-jms-client-core` | `JmsClient` API, `MessageConverter` SPI, listener support - Jakarta Messaging API only |
| `fluda-jms-client-cdi` | CDI producers and wiring |
| `fluda-jms-client-config` | MicroProfile Config `fluda.jms.*` -> `JmsConfig`, and provider connection factories |
| `fluda-jms-client-bom` | Dependency management for the modules above |

## Usage

### Java SE

Build a client from the provider's own connection factory. Wrap it in a
`CachingConnectionFactory` to reuse one `JMSContext` per thread, and close the caching factory
before the raw one:

```java
try (var factory = new ActiveMQJMSConnectionFactory("tcp://localhost:61616", "artemis", "artemis");
     var caching = new CachingConnectionFactory(factory)) {
    JmsClient client = JmsClient.builder(caching).build();
    client.destination("orders").send(new Order("order-1", "keyboard", 2));
}
```

### CDI in Java SE

With `fluda-jms-client-cdi` and `fluda-jms-client-config` on the classpath, `JmsClient` is produced
for injection - no factory code in the application:

```properties
# META-INF/microprofile-config.properties
fluda.jms.artemis.broker-url=tcp://localhost:61616
fluda.jms.artemis.username=artemis
fluda.jms.artemis.password=artemis
fluda.jms.default-destination=orders
```

```java
@Dependent
public class OrderService {
    @Inject JmsClient client;

    public void place(Order order) {
        client.destination().send(order);
    }
}
```

### Jakarta EE

On a Jakarta EE server nothing is configured and nothing is created: the container provides a
`JMSContext` (from `java:comp/DefaultJMSConnectionFactory`), and the `cdi` module builds the client
from it. Do not produce a second `JMSContext` bean - the container already exposes one, and two
beans of the same type make the injection ambiguous.

```java
@JMSDestinationDefinition(name = "java:comp/jms/orders", interfaceName = "jakarta.jms.Queue",
        destinationName = "orders")
@ApplicationScoped
public class JmsResources {
}

@Dependent
public class OrderService {
    @Inject JmsClient client;
    @Resource(lookup = "java:comp/jms/orders") Queue orders;

    public void place(Order order) {
        client.destination(orders).send(order.text());
    }
}
```

### How the CDI module resolves resources

`JmsClientProducer` uses the first of these that exists:

1. a `JMSContext` bean - on Jakarta EE, the container-managed context;
2. a `ConnectionFactory` bean - wrapped in the `CachingConnectionFactory` bean;
3. a configured provider section (`fluda.jms.artemis.*` / `fluda.jms.qpid.*`) whose client is on
   the classpath - wrapped the same way.

When none exists, production fails fast with an `IllegalStateException` naming the three options.

## Configuration

Client-wide properties:

| Property | Default | Meaning |
|---|---|---|
| `fluda.jms.username` | - | broker username for factory-based clients |
| `fluda.jms.password` | - | broker password for factory-based clients |
| `fluda.jms.acknowledge-mode` | `auto-acknowledge` | `auto-acknowledge`, `client-acknowledge` or `dups-ok-acknowledge` |
| `fluda.jms.default-destination` | - | destination **name**, resolved through the `DestinationResolver` |

Provider sections - a section is active once its URL is set, and needs that provider's client on
the classpath (Artemis is tried before Qpid when both are):

| Property | Meaning |
|---|---|
| `fluda.jms.artemis.broker-url` | Artemis core URL, e.g. `tcp://localhost:61616` |
| `fluda.jms.artemis.username` / `.password` | fall back to `fluda.jms.username` / `.password` |
| `fluda.jms.qpid.remote-url` | Qpid JMS URL, e.g. `amqp://localhost:5672` |
| `fluda.jms.qpid.username` / `.password` | fall back to `fluda.jms.username` / `.password` |

An injected `JMSContext` keeps its own credentials and session mode; configuring incompatible
values makes client production fail loudly instead of silently ignoring them.

## Examples

Runnable examples live in [fludakit/examples](https://github.com/fludakit/examples):

| Example | Shows |
|---|---|
| [`jms-client-vanilla`](https://github.com/fludakit/examples/tree/main/jms-client-vanilla) | plain Java SE, no CDI |
| [`jms-client-artemis`](https://github.com/fludakit/examples/tree/main/jms-client-artemis) | CDI + config against Artemis |
| [`jms-client-qpid`](https://github.com/fludakit/examples/tree/main/jms-client-qpid) | the same over AMQP 1.0 with Qpid JMS |
| [`jms-client-jakartaee`](https://github.com/fludakit/examples/tree/main/jms-client-jakartaee) | Jakarta EE, on GlassFish and WildFly |

## Build

JDK 21+, Maven wrapper included:

```bash
./mvnw verify
```

## License

Apache License 2.0 - see [LICENSE](LICENSE).
