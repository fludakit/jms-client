package io.github.fludakit.jms.config;

import io.github.fludakit.jms.JmsConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperties;

/**
 * Produces the {@link JmsConfig} bean from the {@code fluda.jms.*} MicroProfile Config properties.
 * This is the optional integration point consumed by the {@code cdi} module: a
 * {@code JmsClient} injection picks it up and applies it to the spec it builds.
 *
 * <p>The bean is {@link Singleton} rather than {@code @ApplicationScoped} on purpose:
 * {@link JmsConfig} is a final, immutable class, which a normal scope cannot proxy, and one
 * instance is enough - nothing in it changes at runtime.</p>
 *
 * <p>Blank property values are treated as unset. That matters for the username: an empty string
 * would otherwise turn an anonymous connection into an authenticated one with an empty user.</p>
 */
@ApplicationScoped
public class JmsConfigProducer {

    @Inject
    @ConfigProperties
    private JmsProperties properties;

    /** Produces the one config of the application. */
    @Produces
    @Singleton
    public JmsConfig produce() {
        return JmsConfig.builder()
                .username(nullIfBlank(properties.username.orElse(null)))
                .password(nullIfBlank(properties.password.orElse(null)))
                .sessionMode(AcknowledgeMode.from(properties.acknowledgeMode.orElse(null)).sessionMode())
                .defaultDestination(nullIfBlank(properties.defaultDestination.orElse(null)))
                .build();
    }

    static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
