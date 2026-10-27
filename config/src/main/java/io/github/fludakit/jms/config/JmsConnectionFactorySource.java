package io.github.fludakit.jms.config;

import io.github.fludakit.jms.support.ConnectionFactorySource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import org.eclipse.microprofile.config.inject.ConfigProperties;

import java.util.Optional;

/**
 * Turns a configured provider section into a {@link ConnectionFactory}: the last stage of the CDI
 * module's factory resolution, after the application's own {@code ConnectionFactory} bean.
 *
 * <p>A section is used when <em>both</em> conditions hold:</p>
 * <ol>
 *   <li>its URL property is set - {@code fluda.jms.artemis.broker-url} or
 *       {@code fluda.jms.qpid.remote-url};</li>
 *   <li>that provider's client is on the classpath -
 *       {@link #ARTEMIS_CONNECTION_FACTORY_CLASS} or {@link #QPID_CONNECTION_FACTORY_CLASS}.</li>
 * </ol>
 *
 * <p>The classpath check is what lets one config module serve either client: the provider clients
 * are optional dependencies of this module, so a section that is configured but whose client is
 * absent is skipped instead of failing the deployment. Artemis is tried first, then Qpid, so a
 * shared configuration file can carry both sections and each deployment picks the one it ships
 * with.</p>
 *
 * <p>Each factory is created with the provider class named inline, in a nested class that is only
 * loaded after its classpath check passed. That way this class never resolves a provider type, so
 * a deployment with only one of the two clients does not trip over the other.</p>
 */
@ApplicationScoped
public class JmsConnectionFactorySource implements ConnectionFactorySource {

    /** The factory created for the Artemis section; its presence is what gates the section. */
    static final String ARTEMIS_CONNECTION_FACTORY_CLASS =
            "org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory";

    /** The factory created for the Qpid section; its presence is what gates the section. */
    static final String QPID_CONNECTION_FACTORY_CLASS = "org.apache.qpid.jms.JmsConnectionFactory";

    @Inject
    @ConfigProperties
    private JmsProperties properties;

    /**
     * The factory of the first usable provider section.
     *
     * @throws IllegalStateException when a section is configured and its client is present, but the
     *                               factory rejects the URL or the credentials
     */
    @Override
    public Optional<ConnectionFactory> resolve() {
        return artemisFactory().or(this::qpidFactory);
    }

    private Optional<ConnectionFactory> artemisFactory() {
        String brokerUrl = nonBlank(properties.artemis.brokerUrl);
        if (brokerUrl == null || !isOnClasspath(ARTEMIS_CONNECTION_FACTORY_CLASS)) {
            return Optional.empty();
        }
        return Optional.of(ArtemisFactory.create(brokerUrl,
                credential(properties.artemis.username, properties.username),
                credential(properties.artemis.password, properties.password)));
    }

    private Optional<ConnectionFactory> qpidFactory() {
        String remoteUrl = nonBlank(properties.qpid.remoteUrl);
        if (remoteUrl == null || !isOnClasspath(QPID_CONNECTION_FACTORY_CLASS)) {
            return Optional.empty();
        }
        return Optional.of(QpidFactory.create(remoteUrl,
                credential(properties.qpid.username, properties.username),
                credential(properties.qpid.password, properties.password)));
    }

    /**
     * Whether a provider client can be loaded by the application - the gate that keeps a configured
     * section from failing when its client is not deployed.
     */
    static boolean isOnClasspath(String className) {
        if (className == null) {
            return false;
        }
        try {
            // not initialized: only the presence of the class is checked
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** The property value, or {@code null} when it is unset or blank. */
    private static String nonBlank(Optional<String> property) {
        return property == null ? null : property.filter(candidate -> !candidate.isBlank()).orElse(null);
    }

    /** The section's credential when set, otherwise the client-wide one. */
    private static String credential(Optional<String> sectionValue, Optional<String> clientValue) {
        String value = nonBlank(sectionValue);
        return value != null ? value : nonBlank(clientValue);
    }

    /** Loaded only once the Artemis client was found on the classpath. */
    private static final class ArtemisFactory {

        static ConnectionFactory create(String brokerUrl, String username, String password) {
            return username == null
                    ? new org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory(brokerUrl)
                    : new org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory(
                            brokerUrl, username, password);
        }
    }

    /** Loaded only once the Qpid client was found on the classpath. */
    private static final class QpidFactory {

        static ConnectionFactory create(String remoteUrl, String username, String password) {
            // Qpid's three-argument constructor takes (username, password, remoteUri) - the
            // opposite order of the Artemis one
            return username == null
                    ? new org.apache.qpid.jms.JmsConnectionFactory(remoteUrl)
                    : new org.apache.qpid.jms.JmsConnectionFactory(username, password, remoteUrl);
        }
    }
}
