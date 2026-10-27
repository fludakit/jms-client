package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.CachingConnectionFactory;
import io.github.fludakit.jms.JmsClient;
import io.github.fludakit.jms.JmsConfig;
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
 *   <li>a {@link ConnectionFactory} bean - the client is built from the {@link Caching} factory of
 *       {@link CachingConnectionFactoryProducer}, so all clients on one thread share a context
 *       (and a connection) instead of opening one per injection.</li>
 * </ol>
 *
 * <p>When neither is available, production fails fast with {@link IllegalStateException} and
 * guidance, instead of handing out a client that only fails on the first send.</p>
 *
 * <p>An optional {@link JmsConfig} bean (the {@code config} module produces one from
 * {@code fluda.jms.*} properties) is applied to the spec that is used: its default destination and,
 * for a {@code ConnectionFactory}, its credentials and session mode. An injected
 * {@link JMSContext} keeps its own credentials, so an incompatible config makes production fail
 * loudly rather than ignoring those settings - see {@link JmsClient.JmsContextSpec#config}.</p>
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
    Instance<ConnectionFactory> connectionFactories;

    @Inject
    @Caching
    Instance<CachingConnectionFactory> cachingFactories;

    @Inject
    Instance<JmsConfig> configs;

    /**
     * Produces a client from the injected {@link JMSContext} or {@link ConnectionFactory}.
     *
     * @throws IllegalStateException when neither resource is available, or when a {@link JmsConfig}
     *                               cannot be applied to the injected context
     */
    @Produces
    @Dependent
    public JmsClient produceJmsClient() {
        JmsConfig config = configs.isResolvable() ? configs.get() : null;

        if (!jmsContexts.isUnsatisfied()) {
            return apply(JmsClient.builder(jmsContexts.get()), config).build();
        }

        if (!connectionFactories.isUnsatisfied()) {
            return apply(JmsClient.builder(cachingFactories.get()), config).build();
        }

        throw new IllegalStateException(
                "No JMS resources available: provide a jakarta.jms.JMSContext bean (a "
                        + "container-managed context, as on Jakarta EE) or a "
                        + "jakarta.jms.ConnectionFactory bean (the Artemis, Qpid or other provider "
                        + "factory in Java SE/CDI), and optionally a JmsConfig bean for credentials "
                        + "and the default destination");
    }

    private static <S extends JmsClient.AbstractJmsClientSpec<S>> S apply(S spec, JmsConfig config) {
        return config == null ? spec : spec.config(config);
    }
}
