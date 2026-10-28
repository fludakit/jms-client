package io.github.fludakit.jms.qpid;

import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * Holds the {@code fluda.jms.qpid.*} MicroProfile Config properties for the Qpid provider.
 *
 * <p>Properties:</p>
 * <ul>
 *   <li>{@code fluda.jms.qpid.remote-uri} - the Qpid remote URI (required)</li>
 *   <li>{@code fluda.jms.qpid.username} - broker username; falls back to
 *       {@code fluda.jms.username} when unset</li>
 *   <li>{@code fluda.jms.qpid.password} - broker password; falls back to
 *       {@code fluda.jms.password} when unset</li>
 * </ul>
 */
@Dependent
@ConfigProperties(prefix = "fluda.jms.qpid")
public class QpidProperties {

    /** {@code fluda.jms.qpid.remote-uri} - the remote URI (required). */
    @ConfigProperty(name = "remote-uri")
    public Optional<String> remoteUri;

    /** {@code fluda.jms.qpid.username} - falls back to {@code fluda.jms.username}. */
    public Optional<String> username;

    /** {@code fluda.jms.qpid.password} - falls back to {@code fluda.jms.password}. */
    public Optional<String> password;

    @Override
    public String toString() {
        return "QpidProperties[remoteUri=" + remoteUri.orElse("-")
                + ", username=" + username.orElse("-") + "]";
    }
}
