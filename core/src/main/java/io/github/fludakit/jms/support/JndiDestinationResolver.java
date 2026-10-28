package io.github.fludakit.jms.support;

import io.github.fludakit.jms.JmsClientException;
import jakarta.jms.Destination;
import jakarta.jms.JMSContext;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * A {@link DestinationResolver} that looks destination names up in JNDI - the Jakarta EE
 * convention, where the server owns the destination definitions and the application resolves
 * them by name.
 *
 * <p>Usage on Jakarta EE:</p>
 * <pre>{@code
 * client.destinationResolver(new JndiDestinationResolver());
 * client.destination("java:comp/jms/orders").send(order);
 * }</pre>
 *
 * <p>The no-arg constructor uses a fresh {@link InitialContext}; pass a container-managed
 * {@link Context} when the deployment requires a specific environment (or for tests):</p>
 * <pre>{@code
 * client.destinationResolver(new JndiDestinationResolver(myContext));
 * }</pre>
 *
 * <p>Lookups are not cached here: the JNDI provider is expected to cache looked-up objects.
 * Each call performs a fresh {@code lookup(name)}.</p>
 */
public final class JndiDestinationResolver implements DestinationResolver {

    private final Context context;

    /** Resolves destinations through a fresh {@link InitialContext}. */
    public JndiDestinationResolver() {
        this(createInitialContext());
    }

    /** Resolves destinations through the given {@link Context} (container-managed or test). */
    public JndiDestinationResolver(Context context) {
        this.context = context;
    }

    @Override
    public Destination resolveDestination(JMSContext ignored, String name) {
        Object lookedUp;
        try {
            lookedUp = context.lookup(name);
        } catch (NamingException e) {
            throw new JmsClientException(JmsClientException.Code.CONNECTION,
                    "Failed to look up JMS destination: " + name, e);
        }
        if (!(lookedUp instanceof Destination destination)) {
            throw new JmsClientException(JmsClientException.Code.CONNECTION,
                    "JNDI lookup for '" + name + "' did not return a Destination: "
                            + (lookedUp == null ? "null" : lookedUp.getClass().getName()));
        }
        return destination;
    }

    private static Context createInitialContext() {
        try {
            return new InitialContext();
        } catch (NamingException e) {
            throw new JmsClientException(JmsClientException.Code.CONNECTION,
                    "Failed to create InitialContext for JNDI destination resolution", e);
        }
    }
}
