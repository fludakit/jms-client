package io.github.fludakit.jms.support;

import jakarta.jms.Destination;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.jms.Topic;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The default {@link DestinationResolver}: resolves names to {@link Destination}s created on the
 * broker through the {@link JMSContext}, caching each resolution so repeated lookups of the same
 * name return the same destination instead of creating duplicates.
 *
 * <p>Names prefixed with {@code topic:} resolve to a {@link Topic}, everything else to a
 * {@link Queue} - the same convention as Spring's resolvers, whose dynamic and simple behaviors
 * this class merges. Jakarta EE deployments that resolve through JNDI plug in their own
 * {@link DestinationResolver} instead.</p>
 *
 * <p>Thread-safe: the cache is a {@link ConcurrentHashMap}, so one resolver instance can be
 * shared by clients on multiple threads.</p>
 */
public final class SimpleDestinationResolver implements DestinationResolver {

    /** The prefix selecting topic resolution. */
    public static final String TOPIC_PREFIX = "topic:";

    private final ConcurrentMap<String, Destination> cache = new ConcurrentHashMap<>();

    @Override
    public Destination resolveDestination(JMSContext context, String name) {
        return cache.computeIfAbsent(name, key -> createDestination(context, key));
    }

    private Destination createDestination(JMSContext context, String name) {
        if (name.startsWith(TOPIC_PREFIX)) {
            return context.createTopic(name.substring(TOPIC_PREFIX.length()));
        }
        return context.createQueue(name);
    }

    /** Discards all cached resolutions. */
    public void clear() {
        cache.clear();
    }

    /** The names resolved so far, in resolution order. */
    public List<String> cachedNames() {
        return List.copyOf(cache.keySet());
    }
}
