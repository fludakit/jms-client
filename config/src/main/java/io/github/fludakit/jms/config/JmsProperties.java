package io.github.fludakit.jms.config;

import jakarta.enterprise.context.Dependent;
import org.eclipse.microprofile.config.inject.ConfigProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Holds the {@code fluda.jms.*} MicroProfile Config properties for JMS client configuration.
 *
 * <p>Supported properties:</p>
 * <ul>
 *   <li>{@code fluda.jms.username} - broker username; unset or blank means anonymous access</li>
 *   <li>{@code fluda.jms.password} - broker password; unset or blank means no password</li>
 *   <li>{@code fluda.jms.acknowledge-mode} - {@code auto-acknowledge} (default),
 *       {@code client-acknowledge} or {@code dups-ok-acknowledge}</li>
 *   <li>{@code fluda.jms.default-destination} - optional destination <em>name</em>, used when an
 *       operation does not name one (see {@code JmsClient#destination()})</li>
 * </ul>
 *
 * <p>Two properties from the requirements plan are deliberately not mapped in v1:
 * {@code fluda.jms.client-id}, because the client has no client id support, and
 * {@code fluda.jms.connection-factory-jndi}, because this module does not look factories up in
 * JNDI - the {@code cdi} module builds clients from the application's own
 * {@code ConnectionFactory} or {@code JMSContext} bean (on Jakarta EE that is an
 * {@code @Resource} injection).</p>
 */
@Dependent
@ConfigProperties(prefix = "fluda.jms")
public class JmsProperties {

    @ConfigProperty(name = "username", defaultValue = "")
    private String username;

    @ConfigProperty(name = "password", defaultValue = "")
    private String password;

    @ConfigProperty(name = "acknowledge-mode", defaultValue = "auto-acknowledge")
    private String acknowledgeMode;

    @ConfigProperty(name = "default-destination", defaultValue = "")
    private String defaultDestination;

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String acknowledgeMode() {
        return acknowledgeMode;
    }

    public String defaultDestination() {
        return defaultDestination;
    }
}
