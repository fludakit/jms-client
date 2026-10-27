package io.github.fludakit.jms;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Session;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link ConnectionFactory} decorator that keeps one {@link JMSContext} open per thread, so a
 * Java SE or CDI application reuses a connection instead of opening one per client.
 *
 * <pre>{@code
 * try (CachingConnectionFactory connectionFactory =
 *         new CachingConnectionFactory(rawConnectionFactory)) {
 *     JmsClient client = JmsClient.builder(connectionFactory)
 *             .username("artemis").password("secret")
 *             .build();
 *     client.destination("orders").send(order);
 * }   // closes the cached contexts
 * }</pre>
 *
 * <p>{@link JmsClient} never closes a context (see {@link JmsClient#builder(JMSContext)}), so every
 * {@code JmsClient.builder(connectionFactory).build()} on a raw factory opens a connection that is
 * only released when the broker or the GC cleans up. Wrapping the raw factory in this class keeps
 * {@code createContext} cheap: the second client on the same thread gets the already-open context.
 * The client stays unaware of it - this is a plain {@link ConnectionFactory}.</p>
 *
 * <h2>The returned context is owned by this factory</h2>
 * <p>Contexts handed out by {@code createContext(...)} are proxies whose {@code close()} does
 * nothing: closing them would defeat the cache, so it is swallowed like a pooled connection's
 * {@code close()}. Everything else - producers, consumers, {@code commit}, {@code acknowledge},
 * metadata, and even a {@code stop()}/{@code start()} that changes the connection state - is
 * forwarded to the real context. To drop the cached context deliberately (after a broker restart,
 * or when a test wants a clean session), call {@link #reset()}; to release everything at shutdown,
 * call {@link #close()}.</p>
 *
 * <h2>One context per thread, and why</h2>
 * <p>A {@link JMSContext} is not thread-safe, and {@link JmsClient} documents the same constraint.
 * Caching on the current thread means a cached context is only ever used by the thread that created
 * it, so nothing needs to be locked per operation: concurrency comes from a context per thread.
 * Contexts whose thread has died are closed and dropped on the next {@code createContext} call, so
 * {@code new Thread(...)}-per-task code does not leak them. A thread pool should still be sized
 * deliberately: each live thread holds one context, hence one connection.</p>
 *
 * <p>Virtual threads are a poor fit for the same reason - every task would look like a new owner.
 * Use a small pool of platform threads, or a raw factory with one client per task.</p>
 *
 * <p>Credentials and session mode are part of the cache key: {@code createContext("ops", "secret")}
 * and {@code createContext("audit", "secret")} get different contexts even on the same thread.
 * Overloads that mean the same thing share an entry: {@code createContext()} and
 * {@code createContext(Session.AUTO_ACKNOWLEDGE)} are the same request.</p>
 *
 * <h2>What it is not</h2>
 * <ul>
 *   <li>{@link #createConnection()} is <b>not</b> cached - it delegates straight to the target
 *       factory. Contexts are the caching unit; cache them via the {@code createContext} methods.</li>
 *   <li>It does not pool connections for concurrent consumers and producers. For that, use
 *       {@code org.messaginghub:pooled-jms} (a connection pool) or Spring's
 *       {@code CachingConnectionFactory} when Spring already manages the factory.</li>
 *   <li>It has no role on Jakarta EE: an injected {@code JMSContext} is already container-managed
 *       and pooled. Pass it to {@link JmsClient#builder(JMSContext)} instead.</li>
 * </ul>
 *
 * <p>The providers do not make this redundant: neither the Artemis native client nor qpid-jms
 * ships a client-side pool (the {@code PooledConnectionFactory} Qpid documents belongs to its
 * legacy AMQP 0-8 client), and {@code pooled-jms} pools connections rather than contexts. Wrapping
 * the factory is what gives the fluent client a reusable context without an extra dependency.</p>
 *
 * <p>This class is thread-safe. Instances are typically application-scoped and closed on shutdown;
 * like a connection pool, they are not serializable and not meant to be re-created per operation.</p>
 */
public final class CachingConnectionFactory implements ConnectionFactory, AutoCloseable {

    private final ConnectionFactory target;

    /** {@code 0} keeps every live thread's context; a positive value bounds the cache. */
    private final int contextCacheSize;

    /** Access-ordered, so iteration starts at the least recently used entry. Guarded by {@code this}. */
    private final Map<CacheKey, Entry> contexts = new LinkedHashMap<>(16, 0.75f, true);

    private boolean closed;

    /**
     * Wraps {@code target}, keeping the context of every live thread. Equivalent to
     * {@code new CachingConnectionFactory(target, 0)}.
     *
     * @throws NullPointerException when {@code target} is {@code null}
     */
    public CachingConnectionFactory(ConnectionFactory target) {
        this(target, 0);
    }

    /**
     * Wraps {@code target} with an explicit cache bound.
     *
     * @param contextCacheSize the maximum number of contexts to keep open, or {@code 0} for no
     *                         bound (contexts of dead threads are still evicted)
     * @throws NullPointerException     when {@code target} is {@code null}
     * @throws IllegalArgumentException when {@code contextCacheSize} is negative
     */
    public CachingConnectionFactory(ConnectionFactory target, int contextCacheSize) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        if (contextCacheSize < 0) {
            throw new IllegalArgumentException("contextCacheSize must not be negative, got " + contextCacheSize);
        }
        this.contextCacheSize = contextCacheSize;
    }

    /** The bound set at construction ({@code 0} when the cache is unbounded). */
    public int contextCacheSize() {
        return contextCacheSize;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Not cached: every call creates a new connection on the target factory. Cache contexts
     * through the {@code createContext} methods instead, or pool connections with
     * {@code pooled-jms}.</p>
     *
     * @throws IllegalStateException when this factory is closed
     */
    @Override
    public Connection createConnection() throws JMSException {
        requireOpen();
        return target.createConnection();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Not cached - see {@link #createConnection()}.</p>
     *
     * @throws IllegalStateException when this factory is closed
     */
    @Override
    public Connection createConnection(String userName, String password) throws JMSException {
        requireOpen();
        return target.createConnection(userName, password);
    }

    /**
     * Returns the context cached for the current thread, creating it on first use.
     *
     * <p>The context is shared with everything else this thread obtains from this factory: do not
     * hand it to another thread, and note that its {@code close()} is a no-op - use {@link #reset()}
     * to drop it.</p>
     *
     * @throws IllegalStateException when this factory is closed
     */
    @Override
    public JMSContext createContext() {
        return context(null, null, Session.AUTO_ACKNOWLEDGE);
    }

    /**
     * Returns the context cached for the current thread and session mode, creating it on first use.
     *
     * @throws IllegalArgumentException for {@link Session#SESSION_TRANSACTED}
     * @throws IllegalStateException    when this factory is closed
     */
    @Override
    public JMSContext createContext(int sessionMode) {
        return context(null, null, sessionMode);
    }

    /**
     * Returns the context cached for the current thread and credentials, creating it on first use.
     *
     * @throws IllegalStateException when this factory is closed
     */
    @Override
    public JMSContext createContext(String userName, String password) {
        return context(Objects.requireNonNull(userName, "userName must not be null"), password,
                Session.AUTO_ACKNOWLEDGE);
    }

    /**
     * Returns the context cached for the current thread, credentials and session mode, creating it
     * on first use.
     *
     * @throws IllegalArgumentException for {@link Session#SESSION_TRANSACTED}
     * @throws IllegalStateException    when this factory is closed
     */
    @Override
    public JMSContext createContext(String userName, String password, int sessionMode) {
        return context(Objects.requireNonNull(userName, "userName must not be null"), password, sessionMode);
    }

    /**
     * Closes and forgets every cached context; the factory stays usable.
     *
     * <p>Use after a connection-level failure (a broker restart, {@code JmsClientException} with
     * {@link JmsClientException.Code#CONNECTION}) so the next {@code createContext} opens a fresh
     * one. Contexts obtained before the reset are unusable afterwards. Close failures are ignored
     * because there is nothing left to recover.</p>
     */
    public void reset() {
        List<JMSContext> open;
        synchronized (this) {
            if (closed) {
                return;
            }
            open = drain();
        }
        closeQuietly(open);
    }

    /**
     * Closes every cached context and refuses further use. Idempotent.
     *
     * @throws JmsClientException with {@link JmsClientException.Code#CONNECTION} when a context
     *                            failed to close; the remaining ones are still closed, with the
     *                            first failure as the cause and the rest suppressed
     */
    @Override
    public void close() {
        List<JMSContext> open;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            open = drain();
        }

        JmsClientException failure = null;
        for (JMSContext context : open) {
            try {
                context.close();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = new JmsClientException(JmsClientException.Code.CONNECTION,
                            "Failed to close the cached JMS contexts", e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private JMSContext context(String userName, String password, int sessionMode) {
        if (sessionMode == Session.SESSION_TRANSACTED) {
            // Caching a transacted context would hide that every operation needs a commit; the
            // fluent client has no commit point at all, so messages would be rolled back on close.
            throw new IllegalArgumentException("A cached JMSContext cannot be transacted; use the "
                    + "target connection factory directly, or an injected context you commit yourself");
        }
        String effectivePassword = userName == null ? null : (password == null ? "" : password);
        CacheKey key = new CacheKey(Thread.currentThread(), userName, effectivePassword, sessionMode);

        synchronized (this) {
            requireOpen();
            Entry cached = contexts.get(key);
            if (cached != null) {
                return cached.context();
            }
        }

        // Creating a context opens a network connection: do it outside the lock so one slow broker
        // does not serialize every other thread.
        JMSContext created = create(userName, effectivePassword, sessionMode);
        Entry fresh = new Entry(proxied(created), created);

        List<JMSContext> evicted;
        synchronized (this) {
            if (closed) {
                closeQuietly(created);
                throw new IllegalStateException("The caching connection factory is closed");
            }
            Entry raced = contexts.get(key);
            if (raced != null) {
                // reset() or another caller slipped in while we were connecting
                closeQuietly(created);
                return raced.context();
            }
            contexts.put(key, fresh);
            evicted = dropDeadThreads();
            evicted.addAll(trimToSize());
        }
        closeQuietly(evicted);
        return fresh.context();
    }

    /** Creates the real context, delegating the exact overload that matches the request. */
    private JMSContext create(String userName, String password, int sessionMode) {
        if (userName == null) {
            return target.createContext(sessionMode);
        }
        return target.createContext(userName, password, sessionMode);
    }

    /**
     * Wraps {@code delegate} so that {@code close()} is a no-op: the factory decides when a context
     * really closes.
     */
    private static JMSContext proxied(JMSContext delegate) {
        return (JMSContext) Proxy.newProxyInstance(JMSContext.class.getClassLoader(),
                new Class<?>[] {JMSContext.class}, new CloseSuppressingHandler(delegate));
    }

    private synchronized void requireOpen() {
        if (closed) {
            throw new IllegalStateException("The caching connection factory is closed");
        }
    }

    /** Removes and returns every cached context - used by reset() and close(). */
    private List<JMSContext> drain() {
        List<JMSContext> open = new ArrayList<>(contexts.size());
        for (Entry entry : contexts.values()) {
            open.add(entry.delegate());
        }
        contexts.clear();
        return open;
    }

    /** Removes and returns the contexts whose owning thread has died. */
    private List<JMSContext> dropDeadThreads() {
        List<JMSContext> dead = new ArrayList<>();
        for (Iterator<Map.Entry<CacheKey, Entry>> it = contexts.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<CacheKey, Entry> entry = it.next();
            if (!entry.getKey().thread().isAlive()) {
                dead.add(entry.getValue().delegate());
                it.remove();
            }
        }
        return dead;
    }

    /** Removes and returns the least recently used contexts above the bound. */
    private List<JMSContext> trimToSize() {
        List<JMSContext> evicted = new ArrayList<>();
        if (contextCacheSize == 0) {
            return evicted;
        }
        for (Iterator<Entry> it = contexts.values().iterator();
                it.hasNext() && contexts.size() > contextCacheSize; ) {
            evicted.add(it.next().delegate());
            it.remove();
        }
        return evicted;
    }

    private static void closeQuietly(List<JMSContext> contexts) {
        for (JMSContext context : contexts) {
            closeQuietly(context);
        }
    }

    /**
     * Best-effort close: on the create/reset/eviction paths the caller asked for something else
     * and there is nothing useful to do with a failure, so it is not turned into an exception.
     * {@link #close()} is the explicit path and does report.
     */
    private static void closeQuietly(JMSContext context) {
        try {
            context.close();
        } catch (RuntimeException ignored) {
            // already broken or already closed - nothing to recover
        }
    }

    private record CacheKey(Thread thread, String userName, String password, int sessionMode) {
    }

    private record Entry(JMSContext context, JMSContext delegate) {
    }

    /** Forwards everything except {@code close()} to the cached context. */
    private static final class CloseSuppressingHandler implements InvocationHandler {

        private final JMSContext delegate;

        private CloseSuppressingHandler(JMSContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "CachedJMSContext[" + delegate + "]";
                    default -> method.invoke(delegate, args);
                };
            }
            if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
                return null;
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                // rethrow what the context threw, not the reflection wrapper
                throw e.getCause();
            }
        }
    }
}
