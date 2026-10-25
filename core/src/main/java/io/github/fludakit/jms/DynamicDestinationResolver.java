package io.github.fludakit.jms;

import jakarta.jms.Destination;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.jms.Topic;

/**
 * The default {@link DestinationResolver}: creates the destination on the broker through the
 * {@link JMSContext}. Names prefixed with {@code topic:} resolve to a {@link Topic}, everything
 * else to a {@link Queue} - the same convention as Spring's {@code DynamicDestinationResolver}.
 */
public final class DynamicDestinationResolver implements DestinationResolver {

    /** The prefix selecting topic resolution. */
    public static final String TOPIC_PREFIX = "topic:";

    @Override
    public Destination resolveDestination(JMSContext context, String name) {
        if (name.startsWith(TOPIC_PREFIX)) {
            return context.createTopic(name.substring(TOPIC_PREFIX.length()));
        }
        return context.createQueue(name);
    }
}
