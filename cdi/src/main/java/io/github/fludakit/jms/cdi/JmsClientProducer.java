package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.CachingConnectionFactory;
import io.github.fludakit.jms.JmsClient;
import io.github.fludakit.jms.JmsConfig;
import io.github.fludakit.jms.support.DestinationResolver;
import io.github.fludakit.jms.support.MessageConverter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;

/**
 * Produces {@link JmsClient} beans for CDI.
 *
 * <p>The client is built from the first resource that exists, in this order:</p>
 * <ol>
 *   <li>a {@link JMSContext} bean - the {@code JmsClient} uses it as is and never closes it. On
 *       Jakarta EE this is normally the container-managed context of an injected
 *       {@code @Resource JMSContext}; in Java SE a Weld application can produce one itself.</li>
 *   <li>a {@link ConnectionFactory} bean - the client is built from the
 *       {@link CachingConnectionFactory} of {@link CachingConnectionFactoryProducer}, so all
 *       clients on one thread share a context (and a connection) instead of opening one per
 *       injection.</li>
 *   <li>a {@link io.github.fludakit.jms.support.ConnectionFactorySource} bean - the {@code config}
 *       module contributes one that builds the Artemis or Qpid factory from the
 *       {@code fluda.jms.artemis.*} / {@code fluda.jms.qpid.*} section, when that provider's client
 *       is on the classpath. The client is built from the caching factory, exactly as for a
 *       factory bean.</li>
 * </ol>
 *
 * <p>When none is available, production fails fast with {@link IllegalStateException} and
 * guidance (see {@link CachingConnectionFactoryProducer}), instead of handing out a client that
 * only fails on the first send.</p>
 *
 * <p>An optional {@link JmsConfig} bean (the {@code config} module produces one from
 * {@code fluda.jms.*} properties) is applied to the spec that is used: its default destination
 * and, for a {@code ConnectionFactory}, its credentials and session mode. An injected
 * {@link JMSContext} keeps its own credentials, so an incompatible config makes production fail
 * loudly rather than ignoring those settings - see {@link JmsClient.JmsContextSpec#config}.</p>
 *
 * <p>An optional {@link MessageConverter} and {@link DestinationResolver} bean are applied the
 * same way: define one to replace the client defaults ({@link
 * io.github.fludakit.jms.support.SimpleMessageConverter},
 * {@link io.github.fludakit.jms.support.SimpleDestinationResolver}) application-wide, for example
 * a {@link io.github.fludakit.jms.support.JsonbMessageConverter} or a JNDI-aware resolver. With
 * more than one candidate the container reports the ambiguity, so keep a single default bean (or
 * qualify the extra ones).</p>
 *
 * <h2>Scope</h2>
 * <p>The produced client is {@link Dependent}: a {@link JmsClient} is not thread-safe, so one
 * instance must not be shared by concurrent callers. Inject it into a {@code @RequestScoped} or
 * {@code @Dependent} bean, or obtain a fresh one per operation through
 * {@code @Inject Instance<JmsClient>}. Injecting it into an {@code @ApplicationScoped} bean pins
 * one client - and one cached context - for the whole application lifetime.</p>
 *
 * <p>On Jakarta EE the container-managed {@link JMSContext} is exempt from the caching concern: the
 * server already pools it, and the client is built from that context rather than from a factory.</p>
 */
@ApplicationScoped
public class JmsClientProducer {

    @Inject
    Instance<JMSContext> jmsContexts;

    @Inject
    Instance<CachingConnectionFactory> cachingFactories;

    @Inject
    Instance<JmsConfig> configs;

    @Inject
    Instance<MessageConverter> converters;

    @Inject
    Instance<DestinationResolver> destinationResolvers;

    /**
     * Produces a client from the injected {@link JMSContext} or connection factory.
     *
     * @throws IllegalStateException when no {@link JMSContext} bean, {@link ConnectionFactory} bean
     *                               or configured provider section is available, or when a
     *                               {@link JmsConfig} cannot be applied to the injected context
     */
    @Produces
    @Dependent
    public JmsClient produceJmsClient() {
        JmsConfig config = configs.isResolvable() ? configs.get() : null;

        if (!jmsContexts.isUnsatisfied()) {
            return apply(JmsClient.builder(jmsContexts.get()), config).build();
        }

        // the caching factory resolves the raw factory (bean, then configured provider section)
        // and fails with the full guidance when there is none
        return apply(JmsClient.builder(cachingFactories.get()), config).build();
    }

    private <S extends JmsClient.AbstractJmsClientSpec<S>> S apply(S spec, JmsConfig config) {
        S applied = config == null ? spec : spec.config(config);
        if (!converters.isUnsatisfied()) {
            applied = applied.messageConverter(converters.get());
        }
        if (!destinationResolvers.isUnsatisfied()) {
            applied = applied.destinationResolver(destinationResolvers.get());
        }
        return applied;
    }
}
