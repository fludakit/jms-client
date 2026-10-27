package io.github.fludakit.jms.support;

import jakarta.jms.ConnectionFactory;

import java.util.Optional;

/**
 * An optional source of a {@link ConnectionFactory} for modules that can build one from
 * configuration - {@code fluda-jms-client-config} does, from the {@code fluda.jms.artemis.*} and
 * {@code fluda.jms.qpid.*} sections.
 *
 * <p>The CDI module consults every {@code ConnectionFactorySource} bean as its <em>last</em>
 * resolution stage, after the application's own {@link ConnectionFactory} bean, so a factory the
 * application produces always wins. A source that has nothing configured - for example because
 * neither provider section is set, or because the provider's client is not on the classpath -
 * returns an empty {@link Optional} and the next stage is tried.</p>
 *
 * <p>{@link #resolve()} is expected to be cheap until it actually returns a factory: the CDI
 * module resolves once, on first use, not at container startup.</p>
 */
@FunctionalInterface
public interface ConnectionFactorySource {

    /**
     * The configured connection factory.
     *
     * @return the factory, or empty when this source has nothing to contribute
     * @throws IllegalStateException when a section is configured but its factory cannot be created
     */
    Optional<ConnectionFactory> resolve();
}
