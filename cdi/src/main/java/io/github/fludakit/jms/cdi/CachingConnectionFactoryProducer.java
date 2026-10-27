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
 * {@link io.github.fludakit.jms.JmsClient} built from a {@link ConnectionFactory} bean.
 *
 * <p>Without it, every client production would call {@code createContext} on the provider's raw
 * factory and open a connection that {@code JmsClient} never closes (the client does not own the
 * context). With it, the context of the calling thread is reused across clients - see
 * {@link CachingConnectionFactory}.</p>
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
     * @throws IllegalStateException when no {@link ConnectionFactory} bean is available; the
     *                               client producer reports the missing resources itself, so this
     *                               only fires when the caching factory is requested directly
     */
    @Produces
    @Typed(CachingConnectionFactory.class)
    @Singleton
    public CachingConnectionFactory produceCachingConnectionFactory() {
        if (connectionFactories.isUnsatisfied()) {
            throw new IllegalStateException(
                    "No jakarta.jms.ConnectionFactory bean available: produce one (e.g. the"
                            + " Artemis or Qpid factory configured with your broker's URL) or inject"
                            + " a jakarta.jms.JMSContext instead");
        }
        ConnectionFactory target = connectionFactories.get();
        return target instanceof CachingConnectionFactory caching ? caching : new CachingConnectionFactory(target);
    }

    void disposeCachingConnectionFactory(@Disposes CachingConnectionFactory connectionFactory) {
        connectionFactory.close();
    }
}
