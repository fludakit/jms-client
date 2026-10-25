package io.github.fludakit.jms;

import jakarta.jms.Destination;
import jakarta.jms.JMSContext;

/**
 * Resolves a destination <b>name</b> to a {@link Destination} - the SPI behind
 * {@code client.destination(name)} (the {@code DestinationResolver} role in Spring JMS).
 *
 * <p>The default {@link DynamicDestinationResolver} creates the destination on the broker:
 * names prefixed with {@code topic:} resolve to a topic, everything else to a queue. Applications
 * can plug in their own - e.g. a JNDI resolver on Jakarta EE or an explicit-registry resolver.</p>
 */
@FunctionalInterface
public interface DestinationResolver {

    /**
     * Resolves the destination name to a {@link Destination}.
     *
     * @param context the client's JMSContext (destination factory)
     * @param name    the destination name as configured by the application
     * @return the resolved destination
     * @throws JmsClientException with {@link JmsClientException.Code#CONNECTION} if resolution fails
     */
    Destination resolveDestination(JMSContext context, String name);
}
