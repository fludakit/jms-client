package io.github.fludakit.jms.config;

import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Holds the {@code fluda.jms.*} MicroProfile Config properties - the client-wide settings plus the
 * provider sections of Artemis and Qpid JMS, each mapped as a nested class.
 *
 * <p>Client-wide properties:</p>
 * <ul>
 *   <li>{@code fluda.jms.username} - broker username; unset or blank means anonymous access</li>
 *   <li>{@code fluda.jms.password} - broker password; unset or blank means no password</li>
 *   <li>{@code fluda.jms.acknowledge-mode} - {@code auto} (default), {@code client} or
 *       {@code dups-ok}, the same short values as Spring Boot's JMS starter</li>
 *   <li>{@code fluda.jms.default-destination} - optional destination <em>name</em>, used when an
 *       operation does not name one (see {@code JmsClient#destination()})</li>
 * </ul>
 *
 * <p>Provider sections, mapped into the nested {@link Artemis} and {@link Qpid} classes:</p>
 * <ul>
 *   <li>{@code fluda.jms.artemis.broker-url}, {@code .username}, {@code .password} - see
 *       {@link Artemis}</li>
 *   <li>{@code fluda.jms.qpid.remote-url}, {@code .username}, {@code .password} - see
 *       {@link Qpid}</li>
 * </ul>
 *
 * <p>A section is configured once its URL property is set ({@code artemis.brokerUrl} or
 * {@code qpid.remoteUrl}); {@link JmsConnectionFactorySource} then turns it into a
 * {@link jakarta.jms.ConnectionFactory} when that provider's client is on the classpath, and
 * {@link JmsConfigProducer} ignores the sections when none is.</p>
 *
 * <p>Every leaf is an {@link Optional}: SmallRye Config validates the mapping when the class is
 * created and treats a plain field as a <em>required</em> property, so only an {@code Optional}
 * (or a default value) can stay unset. A nested section class itself cannot be {@code Optional},
 * so its object is always created and an unconfigured section is one whose URL is empty. Field
 * names are used verbatim as property names, so the kebab-case ones carry an explicit
 * {@code @ConfigProperty(name = ...)}.</p>
 *
 * <p>Two properties from the requirements plan are deliberately not mapped in v1:
 * {@code fluda.jms.client-id}, because the client has no client id support, and
 * {@code fluda.jms.connection-factory-jndi}, because this module does not look factories up in
 * JNDI. The provider sections configure a factory directly instead, and on Jakarta EE the
 * {@code cdi} module still builds clients from the application's own {@code ConnectionFactory} or
 * {@code JMSContext} bean (an {@code @Resource} injection).</p>
 */
@Dependent
@ConfigProperties(prefix = "fluda.jms")
public class JmsProperties {

    /** {@code fluda.jms.username}. */
    public Optional<String> username;

    /** {@code fluda.jms.password}. */
    public Optional<String> password;

    /** {@code fluda.jms.acknowledge-mode}. */
    @ConfigProperty(name = "acknowledge-mode")
    public Optional<String> acknowledgeMode;

    /** {@code fluda.jms.default-destination}. */
    @ConfigProperty(name = "default-destination")
    public Optional<String> defaultDestination;

    /** The {@code fluda.jms.artemis.*} section; never {@code null}, empty when not configured. */
    public Artemis artemis;

    /** The {@code fluda.jms.qpid.*} section; never {@code null}, empty when not configured. */
    public Qpid qpid;

    /** The Artemis (core protocol) section. */
    public static class Artemis {

        /** {@code fluda.jms.artemis.broker-url} - the section exists once this is set. */
        @ConfigProperty(name = "broker-url")
        public Optional<String> brokerUrl;

        /** {@code fluda.jms.artemis.username} - falls back to {@code fluda.jms.username}. */
        public Optional<String> username;

        /** {@code fluda.jms.artemis.password} - falls back to {@code fluda.jms.password}. */
        public Optional<String> password;
    }

    /** The Qpid JMS (AMQP 1.0) section. */
    public static class Qpid {

        /** {@code fluda.jms.qpid.remote-url} - the section exists once this is set. */
        @ConfigProperty(name = "remote-url")
        public Optional<String> remoteUrl;

        /** {@code fluda.jms.qpid.username} - falls back to {@code fluda.jms.username}. */
        public Optional<String> username;

        /** {@code fluda.jms.qpid.password} - falls back to {@code fluda.jms.password}. */
        public Optional<String> password;
    }

    @Override
    public String toString() {
        // never the passwords
        return "JmsProperties[username=" + username.orElse(null)
                + ", acknowledgeMode=" + acknowledgeMode.orElse(null)
                + ", defaultDestination=" + defaultDestination.orElse(null)
                + ", artemis=" + artemis.brokerUrl.orElse("-")
                + ", qpid=" + qpid.remoteUrl.orElse("-") + "]";
    }
}
