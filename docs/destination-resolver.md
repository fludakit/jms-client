# Destination Resolver

`DestinationResolver` turns a destination *name* into a `Destination`. The client uses it when `client.destination(name)` is called.

## Interface

```java
@FunctionalInterface
public interface DestinationResolver {

    Destination resolveDestination(JMSContext context, String name);
}
```

## Built-in resolvers

### SimpleDestinationResolver (default)

Creates destinations on the broker through the `JMSContext`, caching each resolution, so repeated lookups of the same name return the same destination:

- Names prefixed with `topic:` resolve to a `Topic`
- Everything else resolves to a `Queue`

```java
client.destination("orders").send(order);          // queue "orders"
client.destination("topic:events").send(event);    // topic "events"
```

The cache is thread-safe (`ConcurrentHashMap`), so one resolver instance can be shared by clients on multiple threads.

### JndiDestinationResolver

Looks destination names up in JNDI — the Jakarta EE convention, where the server owns the destination definitions and the application resolves them by name.

```java
JmsClient client = JmsClient.builder(connectionFactory)
        .destinationResolver(new JndiDestinationResolver())
        .build();

client.destination("java:comp/jms/orders").send(order);
```

The no-arg constructor uses a fresh `InitialContext`; pass a container-managed `Context` when the deployment requires a specific environment:

```java
client.destinationResolver(new JndiDestinationResolver(myContext));
```

Lookups are not cached: the JNDI provider is expected to cache looked-up objects.

## Custom resolver

Implement `DestinationResolver` for destination resolution strategies that the built-ins do not cover — for example, an explicit registry or a cloud-based destination service:

```java
public class RegistryDestinationResolver implements DestinationResolver {

    private final Map<String, Destination> registry;

    public RegistryDestinationResolver(Map<String, Destination> registry) {
        this.registry = Map.copyOf(registry);
    }

    @Override
    public Destination resolveDestination(JMSContext context, String name) {
        Destination destination = registry.get(name);
        if (destination == null) {
            throw new JmsClientException(JmsClientException.Code.CONNECTION,
                    "Unknown destination: " + name);
        }
        return destination;
    }
}
```

## CDI integration

In a CDI environment, a single `DestinationResolver` bean is picked up automatically and applied to every produced client:

```java
@ApplicationScoped
public class JmsBeans {

    @Produces
    @ApplicationScoped
    DestinationResolver destinationResolver() {
        return new JndiDestinationResolver();
    }
}
```

Keep one default-qualified bean per SPI — with more than one candidate, the container reports the ambiguity, so qualify the extras. The bean replaces the default application-wide; for a per-client choice use the builder in plain Java SE instead.

This applies to `fluda.jms.default-destination` and any name passed to `client.destination(name)`.
