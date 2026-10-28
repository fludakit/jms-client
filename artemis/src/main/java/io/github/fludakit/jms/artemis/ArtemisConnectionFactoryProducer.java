package io.github.fludakit.jms.artemis;

import io.github.fludakit.jms.config.JmsProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;

/**
 * Produces an Artemis {@link ConnectionFactory} from the {@code fluda.jms.artemis.*} properties.
 *
 * <p>The producer is {@link ApplicationScoped} and produces a {@link Singleton} factory. It fails
 * fast with {@link IllegalStateException} when {@code fluda.jms.artemis.broker-url} is not set,
 * since the presence of this module signals intent to use Artemis.</p>
 *
 * <p>Credential priority: section-specific ({@code fluda.jms.artemis.username/password}) falls
 * back to shared ({@code fluda.jms.username/password}), then to anonymous access.</p>
 */
@ApplicationScoped
public class ArtemisConnectionFactoryProducer {

    @Inject
    private ArtemisProperties properties;

    @Inject
    private JmsProperties sharedProperties;

    @Produces
    @Singleton
    public ConnectionFactory produceConnectionFactory() {
        String brokerUrl = nonBlank(properties.brokerUrl.orElse(null));
        if (brokerUrl == null) {
            throw new IllegalStateException(
                    "fluda.jms.artemis.broker-url must be set when fluda-jms-artemis is deployed");
        }

        String username = credential(properties.username.orElse(null), sharedProperties.username.orElse(null));
        String password = credential(properties.password.orElse(null), sharedProperties.password.orElse(null));

        return username == null
                ? new ActiveMQJMSConnectionFactory(brokerUrl)
                : new ActiveMQJMSConnectionFactory(brokerUrl, username, password == null ? "" : password);
    }

    private static String nonBlank(String property) {
        return property == null || property.isBlank() ? null : property;
    }

    private static String credential(String sectionValue, String sharedValue) {
        String value = nonBlank(sectionValue);
        return value != null ? value : nonBlank(sharedValue);
    }
}
