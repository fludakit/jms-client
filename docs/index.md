# JMS Client

Part of the [FluDa](https://fludakit.github.io/) toolkit — [module source](https://github.com/fludakit/jms-client).

FluDa JMS Client is a fluent, framework-agnostic client for [Jakarta Messaging](https://jakarta.ee/specifications/messaging/) (JMS 3.1). It mirrors the developer experience of Spring's `JmsClient` — `send`, `receive`, `browse`, payload conversion and destination resolution — without requiring Spring or any other framework. The `core` module depends only on the Jakarta Messaging API; the `cdi` and `config` modules add CDI wiring and MicroProfile Config for Java SE and Jakarta EE.

The project is organized into five published modules:

| Module   | Artifact                  | Description                                                                                                                     |
|----------|---------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `core`   | `fluda-jms-core`   | The fluent `JmsClient` API, the `MessageConverter` and `DestinationResolver` SPIs, `CachingConnectionFactory`, and the `JmsConfig` value type. Depends only on the JDK and the Jakarta Messaging API. |
| `config` | `fluda-jms-config` | Maps `fluda.jms.*` MicroProfile Config properties to a `JmsConfig` bean, and builds Artemis or Qpid JMS connection factories from provider-specific sections. |
| `cdi`    | `fluda-jms-cdi`    | Produces the `CachingConnectionFactory` and `JmsClient` beans, resolving the underlying `JMSContext`, `ConnectionFactory` or a configured provider section, and applying any optional `JmsConfig`, `MessageConverter` and `DestinationResolver` beans. |
| `artemis` | `fluda-jms-artemis` | Builds an Artemis `ConnectionFactory` from `fluda.jms.artemis.*` properties. |
| `qpid` | `fluda-jms-qpid` | Builds a Qpid JMS `ConnectionFactory` from `fluda.jms.qpid.*` properties. |

## Key concepts

- **`JmsClient`** — the entry point. Build it from a `ConnectionFactory` in Java SE (the client creates the context) or from an existing `JMSContext` on Jakarta EE (the container owns it). A client is **not thread-safe**: it shares one `JMSContext` across all operations, so create one client per thread.
- **`DestinationSpec`** — what `client.destination(...)` returns. It carries the send, receive and browse operations for one destination; the client is immutable, so the spec is cheap to create per call.
- **`MessageConverter`** — turns payloads into JMS messages and back. The default `SimpleMessageConverter` handles `String`, `byte[]`, `Map` and `Serializable` payloads; `JsonbMessageConverter` is an opt-in JSON-B alternative, and you can plug in your own.
- **`DestinationResolver`** — turns a destination *name* into a `Destination`. The default `SimpleDestinationResolver` creates a queue per name (a `topic:` prefix selects a topic) and caches the result.
- **`CachingConnectionFactory`** — a `ConnectionFactory` decorator that keeps one `JMSContext` per thread, so clients reuse a connection instead of opening one each. `JmsClient` never closes the context it is given; this decorator is how a Java SE application manages that lifecycle.
- **`JmsConfig`** — an immutable bundle of credentials, session mode and default destination name, applied to a builder with `config(...)`. The `config` module produces it from `fluda.jms.*` properties.

!!! note "v1 scope"
    v1 covers fluent send, receive and browse over a `JMSContext`, payload conversion, connection caching, and CDI wiring for Java SE and Jakarta EE. Annotation-driven `@JmsListener` containers and a CDI event bridge are deferred to 1.1; XA transactions are out of scope.

## Reading order

- Start with [getting started](getting-started.md) to add the dependencies and send your first message in Java SE or CDI.
- For Jakarta EE servers, see [Jakarta EE](jakarta-ee.md).
- The runnable examples in [fludakit/examples](https://github.com/fludakit/examples) cover plain Java SE (`jms-client-vanilla`), CDI with Artemis (`jms-client-artemis`), AMQP 1.0 with Qpid JMS (`jms-client-qpid`) and Jakarta EE on GlassFish and WildFly (`jms-client-jakartaee`).
