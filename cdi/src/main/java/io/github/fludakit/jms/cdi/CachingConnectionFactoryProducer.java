package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.CachingConnectionFactory;
import io.github.fludakit.jms.support.ConnectionFactorySource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;

import java.util.Optional;

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
 * <p>The raw factory is resolved once, in this order:</p>
 * <ol>
 *   <li>the application's {@link ConnectionFactory} bean;</li>
 *   <li>the first {@link ConnectionFactorySource} bean that has something configured - the
 *       {@code config} module contributes one, which builds the Artemis or Qpid factory from the
 *       {@code fluda.jms.artemis.*} / {@code fluda.jms.qpid.*} section when that client is on the
 *       classpath.</li>
 * </ol>
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

    @Inject
    Instance<ConnectionFactorySource> connectionFactorySources;

    /**
     * Produces the one caching factory of the application.
     *
     * @throws IllegalStateException when no {@link ConnectionFactory} bean and no configured
     *                               {@link ConnectionFactorySource} is available
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
        if (!connectionFactories.isUnsatisfied()) {
            return connectionFactories.get();
        }

        for (ConnectionFactorySource source : connectionFactorySources) {
            Optional<ConnectionFactory> factory = source.resolve();
            if (factory.isPresent()) {
                return factory.get();
            }
        }

        throw new IllegalStateException(
                "No JMS resources available: provide a jakarta.jms.JMSContext bean (a"
                        + " container-managed context, as on Jakarta EE), a"
                        + " jakarta.jms.ConnectionFactory bean, or configure a provider section -"
                        + " fluda.jms.artemis.broker-url or fluda.jms.qpid.remote-url - with that"
                        + " provider's client on the classpath; optionally add a JmsConfig bean for"
                        + " credentials and the default destination");
    }
}
