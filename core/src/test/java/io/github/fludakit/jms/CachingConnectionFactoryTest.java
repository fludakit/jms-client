package io.github.fludakit.jms;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Session;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What the caching factory hands out, what it passes through, and when it closes the target's
 * contexts - all without a broker.
 */
class CachingConnectionFactoryTest {

    private final ConnectionFactory target = mock(ConnectionFactory.class);

    private final CachingConnectionFactory caching = new CachingConnectionFactory(target);

    @Test
    void connectionsAreNotCached() throws Exception {
        Connection first = mock(Connection.class);
        Connection second = mock(Connection.class);
        when(target.createConnection()).thenReturn(first, second);
        when(target.createConnection("artemis", "secret")).thenReturn(first);

        assertSame(first, caching.createConnection());
        assertSame(second, caching.createConnection());
        assertSame(first, caching.createConnection("artemis", "secret"));

        verify(target, times(2)).createConnection();
        verify(target, times(1)).createConnection("artemis", "secret");
    }

    @Test
    void theContextOfAThreadIsReused() {
        JMSContext delegate = mock(JMSContext.class);
        when(target.createContext(anyInt())).thenReturn(delegate);

        JMSContext context = caching.createContext();

        assertSame(context, caching.createContext());
        assertSame(context, caching.createContext(Session.AUTO_ACKNOWLEDGE));
        verify(target, times(1)).createContext(Session.AUTO_ACKNOWLEDGE);
    }

    @Test
    void equivalentContextOverloadsShareOneEntry() {
        JMSContext delegate = mock(JMSContext.class);
        when(target.createContext(anyString(), anyString(), anyInt())).thenReturn(delegate);

        JMSContext context = caching.createContext("artemis", "secret");

        assertSame(context, caching.createContext("artemis", "secret"));
        assertSame(context, caching.createContext("artemis", "secret", Session.AUTO_ACKNOWLEDGE));
        verify(target, times(1)).createContext("artemis", "secret", Session.AUTO_ACKNOWLEDGE);
    }

    @Test
    void eachThreadGetsItsOwnContext() throws Exception {
        JMSContext mainThread = mock(JMSContext.class);
        JMSContext otherThread = mock(JMSContext.class);
        when(target.createContext(anyInt())).thenReturn(mainThread, otherThread);

        JMSContext context = caching.createContext();

        Callable<JMSContext> task = caching::createContext;
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            JMSContext fromOtherThread = executor.submit(task).get(10, TimeUnit.SECONDS);

            assertNotSame(context, fromOtherThread);
            // each thread keeps its own context
            assertSame(context, caching.createContext());
            assertSame(fromOtherThread, executor.submit(task).get(10, TimeUnit.SECONDS));
        }

        verify(target, times(2)).createContext(anyInt());
    }

    @Test
    void differentCredentialsGetDifferentContexts() {
        JMSContext ops = mock(JMSContext.class);
        JMSContext audit = mock(JMSContext.class);
        when(target.createContext(anyString(), anyString(), anyInt())).thenReturn(ops, audit);

        JMSContext opsContext = caching.createContext("ops", "secret");
        JMSContext auditContext = caching.createContext("audit", "secret");

        assertNotSame(opsContext, auditContext);
        verify(target, times(1)).createContext("ops", "secret", Session.AUTO_ACKNOWLEDGE);
        verify(target, times(1)).createContext("audit", "secret", Session.AUTO_ACKNOWLEDGE);
    }

    @Test
    void differentSessionModesGetDifferentContexts() {
        JMSContext auto = mock(JMSContext.class);
        JMSContext clientAck = mock(JMSContext.class);
        when(target.createContext(anyInt())).thenReturn(auto, clientAck);

        JMSContext autoContext = caching.createContext(Session.AUTO_ACKNOWLEDGE);
        JMSContext clientAckContext = caching.createContext(Session.CLIENT_ACKNOWLEDGE);

        assertNotSame(autoContext, clientAckContext);
        verify(target, times(1)).createContext(Session.AUTO_ACKNOWLEDGE);
        verify(target, times(1)).createContext(Session.CLIENT_ACKNOWLEDGE);
    }

    @Test
    void aMissingPasswordIsSentAsAnEmptyOne() {
        JMSContext delegate = mock(JMSContext.class);
        when(target.createContext(anyString(), anyString(), anyInt())).thenReturn(delegate);

        caching.createContext("artemis", null);

        verify(target, times(1)).createContext("artemis", "", Session.AUTO_ACKNOWLEDGE);
    }

    @Test
    void closingAHandedOutContextDoesNotCloseTheCachedOne() {
        JMSContext delegate = mock(JMSContext.class);
        when(target.createContext(anyInt())).thenReturn(delegate);

        JMSContext context = caching.createContext();
        context.close();

        verify(delegate, never()).close();
        assertSame(context, caching.createContext());
    }

    @Test
    void theProxyForwardsEverythingElse() {
        JMSContext delegate = contextWithClientId("client-1");
        JMSRuntimeException boom = new JMSRuntimeException("stopped");
        doThrow(boom).when(delegate).stop();
        when(target.createContext(anyInt())).thenReturn(delegate);

        JMSContext context = caching.createContext();

        assertEquals("client-1", context.getClientID());
        assertSame(boom, assertThrows(JMSRuntimeException.class, context::stop));
        assertTrue(context.toString().startsWith("CachedJMSContext["));
        assertFalse(context.equals(caching.createContext(Session.CLIENT_ACKNOWLEDGE)));
    }

    @Test
    void resetClosesEveryCachedContextButKeepsTheFactoryUsable() throws Exception {
        JMSContext mainThread = contextWithClientId("main");
        JMSContext otherThread = contextWithClientId("other");
        JMSContext replacement = contextWithClientId("replacement");
        when(target.createContext(anyInt())).thenReturn(mainThread, otherThread, replacement);

        JMSContext context = caching.createContext();
        contextInAnotherThread(caching);

        caching.reset();

        verify(mainThread, times(1)).close();
        verify(otherThread, times(1)).close();

        JMSContext fresh = caching.createContext();
        assertNotSame(context, fresh);
        assertEquals("replacement", fresh.getClientID());
        verify(target, times(3)).createContext(anyInt());
    }

    @Test
    void closeClosesEveryCachedContextAndRefusesFurtherUse() throws Exception {
        JMSContext mainThread = mock(JMSContext.class);
        JMSContext otherThread = mock(JMSContext.class);
        when(target.createContext(anyInt())).thenReturn(mainThread, otherThread);

        caching.createContext();
        contextInAnotherThread(caching);

        caching.close();

        verify(mainThread).close();
        verify(otherThread).close();
        assertThrows(IllegalStateException.class, caching::createContext);
        assertThrows(IllegalStateException.class, caching::createConnection);
    }

    @Test
    void closeIsIdempotent() {
        JMSContext context = mock(JMSContext.class);
        when(target.createContext(anyInt())).thenReturn(context);

        caching.createContext();
        caching.close();
        caching.reset();
        caching.close();

        verify(context, times(1)).close();
    }

    @Test
    void closeReportsAFailureButStillClosesTheRest() throws Exception {
        JMSContext broken = mock(JMSContext.class);
        JMSContext healthy = mock(JMSContext.class);
        JMSRuntimeException boom = new JMSRuntimeException("boom");
        doThrow(boom).when(broken).close();
        when(target.createContext(anyInt())).thenReturn(broken, healthy);

        caching.createContext();
        contextInAnotherThread(caching);

        JmsClientException exception = assertThrows(JmsClientException.class, caching::close);

        assertEquals(JmsClientException.Code.CONNECTION, exception.getCode());
        assertSame(boom, exception.getCause());
        verify(healthy).close();
    }

    @Test
    void aBoundedCacheEvictsTheLeastRecentlyUsedEntry() {
        CachingConnectionFactory bounded = new CachingConnectionFactory(target, 1);
        JMSContext first = contextWithClientId("first");
        JMSContext second = contextWithClientId("second");
        JMSContext third = contextWithClientId("third");
        when(target.createContext(anyString(), anyString(), anyInt())).thenReturn(first, second, third);

        // three different cache keys on one thread: credentials are part of the key
        assertEquals("first", bounded.createContext("one", "secret").getClientID());
        // a second entry pushes the first one out
        assertEquals("second", bounded.createContext("two", "secret").getClientID());
        verify(first, times(1)).close();

        // and the next one pushes the second one out
        assertEquals("third", bounded.createContext("three", "secret").getClientID());
        verify(second, times(1)).close();
    }

    @Test
    void contextsOfDeadThreadsAreClosedOnTheNextRequest() throws Exception {
        JMSContext deadThread = mock(JMSContext.class);
        JMSContext mainThread = contextWithClientId("main");
        when(target.createContext(anyInt())).thenReturn(deadThread, mainThread);

        contextInAnotherThread(caching);

        assertEquals("main", caching.createContext().getClientID());
        verify(deadThread, times(1)).close();
    }

    @Test
    void aTransactedContextIsRejectedBeforeAnyConnection() {
        assertThrows(IllegalArgumentException.class,
                () -> caching.createContext(Session.SESSION_TRANSACTED));
        assertThrows(IllegalArgumentException.class,
                () -> caching.createContext("artemis", "secret", Session.SESSION_TRANSACTED));

        verifyNoInteractions(target);
    }

    @Test
    void aFailedCreationPropagatesAndLeavesTheFactoryUsable() {
        JMSRuntimeException boom = new JMSRuntimeException("broker down");
        when(target.createContext(anyInt())).thenThrow(boom);

        assertSame(boom, assertThrows(JMSRuntimeException.class, caching::createContext));

        JMSContext recovered = contextWithClientId("recovered");
        doReturn(recovered).when(target).createContext(anyInt());
        assertEquals("recovered", caching.createContext().getClientID());
    }

    @Test
    void invalidConstructionArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new CachingConnectionFactory(null));
        assertThrows(IllegalArgumentException.class, () -> new CachingConnectionFactory(target, -1));
        assertEquals(0, caching.contextCacheSize());
        assertEquals(3, new CachingConnectionFactory(target, 3).contextCacheSize());
        verifyNoInteractions(target);
    }

    /** A context mock that identifies itself, so a test can tell which delegate a proxy fronts. */
    private static JMSContext contextWithClientId(String clientId) {
        JMSContext context = mock(JMSContext.class);
        when(context.getClientID()).thenReturn(clientId);
        return context;
    }

    private static JMSContext contextInAnotherThread(CachingConnectionFactory caching) throws Exception {
        AtomicReference<JMSContext> created = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                created.set(caching.createContext());
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "caching-factory-test-worker");
        worker.start();
        // join() returns only when the thread is dead, which is what the eviction checks
        worker.join(10_000);
        if (worker.isAlive()) {
            throw new AssertionError("the worker thread did not finish");
        }
        if (failure.get() != null) {
            throw new AssertionError("creating a context on another thread failed", failure.get());
        }
        return created.get();
    }
}
