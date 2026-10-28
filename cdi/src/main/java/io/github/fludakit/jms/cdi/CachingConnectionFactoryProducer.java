package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.CachingConnectionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;

/**
 * Produces the {@link CachingConnectionFactory} that backs every
 * {@link io.github.fludakit.jms.JmsClient} built from a connection factory.
 *
 * <p>Without it, every client production would call {@code createContext} on the provider's raw
 * factory and open a connection that {@code JmsClient} never closes (the client does not own the
 * context). With it, the context of the calling thread is reused across clients - see
 * {@link CachingConnectionFactory}.</p>
 *
 * <h2>Which factory is cached</h2>
 * <p>The raw factory is the application's {@link ConnectionFactory} bean. Provider modules
 * ({@code fluda-jms-artemis}, {@code fluda-jms-qpid}) produce one from their configuration
 * properties.</p>
 *
 * <p>The properties that matter for CDI:</p>
 * <ul>
 *   <li>The bean is {@link Typed} to {@link CachingConnectionFactory} alone, so it is not a
 *       {@code ConnectionFactory} bean. The application's raw {@code ConnectionFactory} stays the
 *       only bean of that type, so producers that need the provider factory keep resolving it
 *       without an ambiguity. A consumer asks for the caching factory by its concrete type:
 *       {@code @Inject CachingConnectionFactory}.</li>
 *   <li>It is {@link Singleton} rather than {@code @ApplicationScoped} on purpose. The factory is
 *       a <em>final</em> class, which a normal scope cannot proxy, and the shared instance has no
 *       state a proxy would have to protect: {@code CachingConnectionFactory} is thread-safe on
 *       its own.</li>
 *   <li>{@link CachingConnectionFactory#close()} runs through the disposer when the container
 *       shuts down (or, in a server, when the deployment is undeployed).</li>
 * </ul>
 *
 * <p>An application that already produces a {@link CachingConnectionFactory} gets it back
 * unchanged instead of a second cache around the first.</p>
 */
@ApplicationScoped
public class CachingConnectionFactoryProducer {

    @Inject
    Instance<ConnectionFactory> connectionFactories;

    /**
     * Produces the one caching factory of the application.
     *
     * @throws IllegalStateException when no {@link ConnectionFactory} bean is available
     */
    @Produces
    @Typed(CachingConnectionFactory.class)
    @Singleton
    public CachingConnectionFactory produceCachingConnectionFactory() {
        ConnectionFactory target = rawConnectionFactory();
        return target instanceof CachingConnectionFactory caching ? caching : new CachingConnectionFactory(target);
    }

    void disposeCachingConnectionFactory(@Disposes CachingConnectionFactory connectionFactory) {
        connectionFactory.close();
    }

    private ConnectionFactory rawConnectionFactory() {
        if (connectionFactories.isUnsatisfied()) {
            throw new IllegalStateException(
                    "No JMS resources available: provide a jakarta.jms.JMSContext bean (a"
                            + " container-managed context, as on Jakarta EE), a"
                            + " jakarta.jms.ConnectionFactory bean, or add a provider module"
                            + " (fluda-jms-artemis or fluda-jms-qpid); optionally add a JmsConfig"
                            + " bean for credentials and the default destination");
        }
        return connectionFactories.get();
    }
}
