package io.github.fludakit.jms.config;

import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Holds the client-wide {@code fluda.jms.*} MicroProfile Config properties - the settings that
 * apply regardless of which provider module is deployed. Provider-specific properties
 * ({@code fluda.jms.artemis.*}, {@code fluda.jms.qpid.*}) belong to their respective modules.
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
 * <p>Every leaf is an {@link Optional}: SmallRye Config validates the mapping when the class is
 * created and treats a plain field as a <em>required</em> property, so only an {@code Optional}
 * (or a default value) can stay unset. Field names are used verbatim as property names, so the
 * kebab-case ones carry an explicit {@code @ConfigProperty(name = ...)}.</p>
 *
 * <p>Two properties from the requirements plan are deliberately not mapped in v1:
 * {@code fluda.jms.client-id}, because the client has no client id support, and
 * {@code fluda.jms.connection-factory-jndi}, because destination resolution through JNDI is
 * handled by {@link io.github.fludakit.jms.support.JndiDestinationResolver} in the client, not
 * at the factory level.</p>
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

    @Override
    public String toString() {
        // never the passwords
        return "JmsProperties[username=" + username.orElse(null)
                + ", acknowledgeMode=" + acknowledgeMode.orElse(null)
                + ", defaultDestination=" + defaultDestination.orElse(null) + "]";
    }
}
