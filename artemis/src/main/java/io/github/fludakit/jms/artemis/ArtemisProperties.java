package io.github.fludakit.jms.artemis;

import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Holds the {@code fluda.jms.artemis.*} MicroProfile Config properties for the Artemis provider.
 *
 * <p>Properties:</p>
 * <ul>
 *   <li>{@code fluda.jms.artemis.broker-url} - the Artemis broker URL (required)</li>
 *   <li>{@code fluda.jms.artemis.username} - broker username; falls back to
 *       {@code fluda.jms.username} when unset</li>
 *   <li>{@code fluda.jms.artemis.password} - broker password; falls back to
 *       {@code fluda.jms.password} when unset</li>
 * </ul>
 */
@Dependent
@ConfigProperties(prefix = "fluda.jms.artemis")
public class ArtemisProperties {

    /** {@code fluda.jms.artemis.broker-url} - the broker URL (required). */
    @ConfigProperty(name = "broker-url")
    public Optional<String> brokerUrl;

    /** {@code fluda.jms.artemis.username} - falls back to {@code fluda.jms.username}. */
    public Optional<String> username;

    /** {@code fluda.jms.artemis.password} - falls back to {@code fluda.jms.password}. */
    public Optional<String> password;

    @Override
    public String toString() {
        return "ArtemisProperties[brokerUrl=" + brokerUrl.orElse("-")
                + ", username=" + username.orElse("-") + "]";
    }
}
