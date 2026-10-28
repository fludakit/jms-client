# JMS Provider Modules Refactor

**Date:** 2026-10-28  
**Status:** Approved for implementation

## Summary

Extract Artemis and Qpid connection factory creation from the `config` module into dedicated provider modules (`fluda-jms-artemis`, `fluda-jms-qpid`), eliminating class-detection logic and the `ConnectionFactorySource` abstraction. Add a `JndiDestinationResolver` to core for Jakarta EE deployments.

## Motivation

The current `config` module holds optional dependencies on both Artemis and Qpid clients and uses runtime class-detection (`Class.forName`) to decide which factory to build. This design:

- Couples the config module to provider clients that may not be present
- Requires a `ConnectionFactorySource` abstraction to defer factory creation
- Makes the module's responsibility unclear (property mapping vs. factory construction)

By splitting provider-specific code into dedicated modules:

- Each provider module directly depends on its client (no optional deps, no detection)
- The `ConnectionFactorySource` interface can be removed; provider modules produce `ConnectionFactory` beans directly
- The `config` module focuses solely on client-wide property mapping
- Users opt into a provider by adding its module, making dependencies explicit

## Current State

**Modules:**
- `fluda-jms-client-core`: `JmsClient`, `DestinationResolver` SPI, `SimpleDestinationResolver`, `ConnectionFactorySource`
- `fluda-jms-client-cdi`: Producers for `JmsClient` and `CachingConnectionFactory`; resolves `JMSContext` → `ConnectionFactory` bean → `ConnectionFactorySource`
- `fluda-jms-client-config`: `JmsProperties` (client-wide + nested Artemis/Qpid sections), `JmsConfigProducer`, `JmsConnectionFactorySource` (builds factories from properties with class-detection)

**Dependencies:**
- `config` has optional deps on `artemis-jakarta-client` and `qpid-jms-client`
- `JmsConnectionFactorySource` uses `Class.forName` to check provider presence, then instantiates the factory via nested loader classes

## Target Architecture

| Module | Responsibility | Dependencies |
|---|---|---|
| `fluda-jms-client-core` | `JmsClient`, resolver SPI, `SimpleDestinationResolver`, **new `JndiDestinationResolver`** | Jakarta Messaging API only |
| `fluda-jms-client-cdi` | Producers; resolves `JMSContext` first, then `ConnectionFactory` bean | core |
| `fluda-jms-client-config` | `fluda.jms.*` client-wide properties → `JmsConfig`; **no provider client deps** | core |
| **`fluda-jms-artemis`** (new) | `ArtemisProperties` + `@Produces @Singleton ConnectionFactory` built directly with `ActiveMQJMSConnectionFactory` | Artemis client, CDI API, MP Config API |
| **`fluda-jms-qpid`** (new) | `QpidProperties` + `@Produces @Singleton ConnectionFactory` built directly with `JmsConnectionFactory` | Qpid client, CDI API, MP Config API |

**Naming:**
- Artifact IDs: `fluda-jms-artemis`, `fluda-jms-qpid`
- JPMS module names: `fluda.jms.artemis`, `fluda.jms.qpid`
- Java packages: `io.github.fludakit.jms.artemis`, `io.github.fludakit.jms.qpid`

## Module-by-Module Changes

### `core`

**Add:**
- `io.github.fludakit.jms.support.JndiDestinationResolver`
  - Constructors: no-arg (uses `new InitialContext()`) and `JndiDestinationResolver(Context)` for container-managed/test contexts
  - `resolveDestination(JMSContext context, String name)`: performs `context.lookup(name)`, validates result is a `Destination`, wraps `NamingException` or non-`Destination` result in `JmsClientException(Code.CONNECTION)`
  - No caching; the JNDI provider owns that

**Remove:**
- `io.github.fludakit.jms.support.ConnectionFactorySource` (interface)

**Update:**
- `module-info.java`: add `requires java.naming;`
- `DestinationResolver` javadoc: point to `JndiDestinationResolver` for Jakarta EE usage
- `JmsClient` javadoc: update references to destination resolution

**Tests:**
- Add `JndiDestinationResolverTest`: lookup success, `NamingException` handling, non-`Destination` result handling (using mocked `Context`)

### `config`

**Update:**
- `JmsProperties`: remove nested `Artemis` and `Qpid` classes; keep only client-wide properties (`username`, `password`, `acknowledgeMode`, `defaultDestination`)
- `JmsConnectionFactorySource`: delete
- `JmsConnectionFactorySourceTest`: delete
- `pom.xml`: remove optional deps on `artemis-jakarta-client` and `qpid-jms-client`
- `module-info.java`: remove `requires static artemis.jakarta.client;` and `requires static qpid.jms.client;`
- `JmsProperties` javadoc: remove references to provider sections and `JmsConnectionFactorySource`

**Tests:**
- `JmsConfigProducerTest`: unchanged (still maps client-wide properties)
- `AcknowledgeModeTest`: unchanged

### `cdi`

**Update:**
- `CachingConnectionFactoryProducer`:
  - Remove `@Inject Instance<ConnectionFactorySource> connectionFactorySources;`
  - Remove the loop iterating sources in `rawConnectionFactory()`
  - Update fail-fast message: "provide a `JMSContext` bean, a `ConnectionFactory` bean, or add `fluda-jms-artemis` / `fluda-jms-qpid`"
- `JmsClientProducer` javadoc:
  - Remove third stage (configured provider section) from the resolution list
  - Mention that provider modules produce `ConnectionFactory` beans directly
- `CachingConnectionFactoryProducer` javadoc:
  - Remove reference to `ConnectionFactorySource` in "Which factory is cached"

**Tests:**
- Delete `BuildsFromConnectionFactorySourceTest`
- Delete `PrefersConnectionFactoryBeanOverSourceTest`
- Keep `ProducesFromConnectionFactoryTest` (validates `ConnectionFactory` bean wiring)
- Keep `FailsFastWithoutResourcesTest` (validates fail-fast message; update assertion if message changes)

### `artemis` (new module)

**Add:**
- `io.github.fludakit.jms.artemis.ArtemisProperties`
  - `@ConfigProperties(prefix = "fluda.jms.artemis")`
  - Fields: `brokerUrl` (`@ConfigProperty(name = "broker-url") Optional<String>`), `username` (`Optional<String>`), `password` (`Optional<String>`)
- `io.github.fludakit.jms.artemis.ArtemisConnectionFactoryProducer`
  - `@ApplicationScoped`
  - Inject `ArtemisProperties` and shared credentials:
    ```java
    @Inject @ConfigProperty(name = "fluda.jms.username") Optional<String> sharedUsername;
    @Inject @ConfigProperty(name = "fluda.jms.password") Optional<String> sharedPassword;
    ```
  - `@Produces @Singleton ConnectionFactory produceConnectionFactory()`
    - Fail fast if `brokerUrl` is blank: `throw new IllegalStateException("fluda.jms.artemis.broker-url must be set")`
    - Credential priority: section → shared → anonymous
    - Instantiate directly: `new ActiveMQJMSConnectionFactory(brokerUrl, username, password)` or `new ActiveMQJMSConnectionFactory(brokerUrl)`
- `src/main/module/module-info.java`:
  ```java
  module fluda.jms.artemis {
      requires transitive jakarta.messaging;
      requires jakarta.cdi;
      requires microprofile.config.api;
      requires artemis.jakarta.client;
      
      exports io.github.fludakit.jms.artemis;
      opens io.github.fludakit.jms.artemis;
  }
  ```
- `src/main/resources/META-INF/beans.xml`: empty marker (CDI 4.1)
- `pom.xml`:
  - Parent: `fluda-jms-client-parent`
  - ArtifactId: `fluda-jms-artemis`
  - Dependencies: `artemis-jakarta-client` (non-optional), `jakarta.enterprise:jakarta.enterprise.cdi-api` (provided), `jakarta.jms:jakarta.jms-api` (provided), `org.eclipse.microprofile.config:microprofile-config-api` (provided), test deps (junit, weld-junit5, smallrye-config)
  - Build: moditect plugin for module-info

**Tests:**
- `ArtemisConnectionFactoryProducerTest` (Weld SE + SmallRye Config):
  - Configured section with section credentials → `ActiveMQJMSConnectionFactory` with correct user/password
  - Blank section credentials fall back to shared `fluda.jms.username/password`
  - Blank shared credentials → anonymous (null user/password)
  - Section credentials win over shared when both set
  - Missing or blank `broker-url` → `IllegalStateException`
  - Both section and shared blank → anonymous factory

### `qpid` (new module)

**Add:**
- `io.github.fludakit.jms.qpid.QpidProperties`
  - `@ConfigProperties(prefix = "fluda.jms.qpid")`
  - Fields: `remoteUrl` (`@ConfigProperty(name = "remote-url") Optional<String>`), `username`, `password`
- `io.github.fludakit.jms.qpid.QpidConnectionFactoryProducer`
  - Same pattern as Artemis
  - Instantiate: `new JmsConnectionFactory(remoteUrl)` or `new JmsConnectionFactory(username, password, remoteUrl)` (note: Qpid's three-arg constructor order is `(username, password, remoteUrl)`, opposite of Artemis)
- `module-info.java`:
  ```java
  module fluda.jms.qpid {
      requires transitive jakarta.messaging;
      requires jakarta.cdi;
      requires microprofile.config.api;
      requires qpid.jms.client;
      
      exports io.github.fludakit.jms.qpid;
      opens io.github.fludakit.jms.qpid;
  }
  ```
- `pom.xml`: analogous to Artemis, with `qpid-jms-client` dependency

**Tests:**
- `QpidConnectionFactoryProducerTest`: analogous to Artemis, asserting remote URI and credentials

### Root `pom.xml`

**Update:**
- `<modules>`: add `artemis` and `qpid`
- `<dependencyManagement>`: add entries for `fluda-jms-artemis` and `fluda-jms-qpid` (both `${fluda-jms-client.version}`)
- Keep `artemis.version` and `qpid-jms.version` properties (used by provider modules)

### `README.md`

**Update:**
- Module table: add `fluda-jms-artemis` and `fluda-jms-qpid` rows
- CDI dependency guidance: "Add `fluda-jms-client-cdi`, `fluda-jms-client-config` (optional), and the provider module for your broker (`fluda-jms-artemis` or `fluda-jms-qpid`)"
- Remove mention of adding raw provider client directly

## Examples Update

**`examples/pom.xml` (parent):**
- Add `dependencyManagement` entries:
  ```xml
  <dependency>
      <groupId>io.github.fludakit</groupId>
      <artifactId>fluda-jms-artemis</artifactId>
      <version>${fluda-jms-client.version}</version>
  </dependency>
  <dependency>
      <groupId>io.github.fludakit</groupId>
      <artifactId>fluda-jms-qpid</artifactId>
      <version>${fluda-jms-client.version}</version>
  </dependency>
  ```
- Keep `artemis-jakarta-client` and `qpid-jms-client` entries (still used transitively)

**`examples/jms-client-artemis/pom.xml`:**
- Remove `org.apache.activemq:artemis-jakarta-client`
- Add `io.github.fludakit:fluda-jms-artemis`

**`examples/jms-client-qpid/pom.xml`:**
- Remove `org.apache.qpid:qpid-jms-client`
- Add `io.github.fludakit:fluda-jms-qpid`

## Migration / Breaking Changes

**Breaking:**
- `ConnectionFactorySource` removed from core
- `JmsProperties.artemis` and `JmsProperties.qpid` nested classes removed from config
- `config` module no longer depends on provider clients; users must add `fluda-jms-artemis` or `fluda-jms-qpid` explicitly
- Adding both provider modules is now an ambiguous-dependency error (no Artemis-first preference)

**Migration path:**
- Replace `fluda-jms-client-config` + raw provider client with `fluda-jms-client-config` + provider module
- If using `ConnectionFactorySource` in custom code, replace with direct `ConnectionFactory` bean production
- If relying on Artemis-first preference when both configured, remove the unused provider module

## Tests

**New:**
- `JndiDestinationResolverTest` (core)
- `ArtemisConnectionFactoryProducerTest` (artemis)
- `QpidConnectionFactoryProducerTest` (qpid)

**Updated:**
- `FailsFastWithoutResourcesTest` (cdi): update assertion if fail-fast message changes

**Removed:**
- `JmsConnectionFactorySourceTest` (config)
- `BuildsFromConnectionFactorySourceTest` (cdi)
- `PrefersConnectionFactoryBeanOverSourceTest` (cdi)

**Validation:**
- Run `./mvnw verify` on the whole reactor (includes moditect packaging for new modules)
- Run `./mvnw test` on examples to verify dependency changes

## Out of Scope

**Sibling repositories:**
- `fludakit.github.io` docs (getting-started, index) still describe the old module layout; update after library release
- `fludakit/examples` other examples (vanilla, jakartaee) not touched unless they use provider clients directly

**Future work:**
- Consider whether provider modules should depend on `fluda-jms-client-cdi` to auto-register producers (currently users must add both)
- Consider adding `@Alternative` or qualifier-based precedence if app-defined `ConnectionFactory` beans need to override provider modules
