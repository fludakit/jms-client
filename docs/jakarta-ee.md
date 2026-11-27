# Jakarta EE

In a Jakarta EE environment, nothing is configured and nothing is created in the application: the container provides a `JMSContext` from `java:comp/DefaultJMSConnectionFactory`, and the `cdi` module builds the client from it.

## Declaring destinations

Jakarta EE does not let an application create destinations at runtime. Declare the destination with `@JMSDestinationDefinition` and inject it:

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.jms.JMSDestinationDefinition;

@JMSDestinationDefinition(
        name = "java:comp/jms/orders",
        interfaceName = "jakarta.jms.Queue",
        destinationName = "orders"
)
@ApplicationScoped
public class JmsResources {
}
```

The annotation is repeatable — a second queue would be another annotation on the same class.

## Using the client

Inject `JmsClient` and the `Queue`, then pass the injected `Destination` rather than a name:

```java
import jakarta.annotation.Resource;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.jms.Queue;

@Dependent
public class OrderService {

    @Inject
    JmsClient client;

    @Resource(lookup = "java:comp/jms/orders")
    Queue orders;

    public void place(Order order) {
        client.destination(orders).send(order);
    }
}
```

On a server the deployment decides the address, the queue-or-topic kind and the provider settings, while a name is resolved through `JMSContext.createQueue`/`createTopic` and ignores the resource definition. The `fluda.jms.default-destination` property has the same caveat — it is always a *name*.

!!! warning "Do not produce a second `JMSContext` bean"
    The server already exposes the container-managed `JMSContext` as a bean. A producer of your own makes the `Instance<JMSContext>` lookup ambiguous and deployment fails with `WELD-001335: Ambiguous dependencies`.

## CDI resource resolution

The CDI module resolves resources as described in [CDI Integration](getting-started.md#how-the-cdi-module-resolves-resources). On Jakarta EE, the first match is typically the container-managed `JMSContext` bean.

## Example

The [`jms-client-jakartaee`](https://github.com/fludakit/examples/tree/main/jms-client-jakartaee) example runs on GlassFish 8 and WildFly 42.
