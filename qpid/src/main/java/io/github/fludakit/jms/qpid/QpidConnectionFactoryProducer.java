package io.github.fludakit.jms.qpid;

import io.github.fludakit.jms.config.JmsProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;

/**
 * Produces a Qpid JMS {@link ConnectionFactory} from the {@code fluda.jms.qpid.*} properties.
 *
 * <p>The producer is {@link ApplicationScoped} and produces a {@link Singleton} factory. It fails
 * fast with {@link IllegalStateException} when {@code fluda.jms.qpid.remote-url} is not set,
 * since the presence of this module signals intent to use Qpid.</p>
 *
 * <p>Credential priority: section-specific ({@code fluda.jms.qpid.username/password}) falls
 * back to shared ({@code fluda.jms.username/password}), then to anonymous access.</p>
 */
@ApplicationScoped
public class QpidConnectionFactoryProducer {

    @Inject
    private QpidProperties properties;

    @Inject
    private JmsProperties sharedProperties;

    @Produces
    @Singleton
    public ConnectionFactory produceConnectionFactory() {
        String remoteUri = nonBlank(properties.remoteUri.orElse(null));
        if (remoteUri == null) {
            throw new IllegalStateException(
                    "fluda.jms.qpid.remote-uri must be set when fluda-jms-qpid is deployed");
        }

        String username = credential(properties.username.orElse(null), sharedProperties.username.orElse(null));
        String password = credential(properties.password.orElse(null), sharedProperties.password.orElse(null));

        // Qpid's three-argument constructor takes (username, password, remoteUri) - the
        // opposite order of the Artemis one
        return username == null
                ? new JmsConnectionFactory(remoteUri)
                : new JmsConnectionFactory(username, password == null ? "" : password, remoteUri);
    }

    private static String nonBlank(String property) {
        return property == null || property.isBlank() ? null : property;
    }

    private static String credential(String sectionValue, String sharedValue) {
        String value = nonBlank(sectionValue);
        return value != null ? value : nonBlank(sharedValue);
    }
}
