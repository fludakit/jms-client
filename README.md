# FluDa :: Jms Client

A fluent JMS client for Jakarta Messaging, in the style of Spring's `JmsClient` - part of the [FluDa Kit](https://github.com/fludakit).

## Status

v1 is in design/development - see [issue #1](../../issues/1) for the requirements plan and phased delivery.

**In v1:** fluent send/receive over a `jakarta.jms.JmsContext`, a `MessageConverter` SPI (Simple + optional JSON-B), and connection/context caching for Java SE and CDI. **Deferred to 1.1:** `@JmsListener`/`JmsListenerContainer` and the CDI event bridge.

## Modules

| Module | Responsibility |
|---|---|
| `fluda-jms-client-core` | `JmsClient` API, `MessageConverter` SPI, listener support - Jakarta Messaging API only |
| `fluda-jms-client-cdi` | CDI producers and wiring |
| `fluda-jms-client-config` | MicroProfile Config `fluda.jms.*` -> `JmsConfig` |

## Build

JDK 21+, Maven wrapper included:

```bash
./mvnw verify
```

## License

Apache License 2.0 - see [LICENSE](LICENSE).
