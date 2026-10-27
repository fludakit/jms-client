package io.github.fludakit.jms.cdi;

import io.github.fludakit.jms.CachingConnectionFactory;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * A connection factory bean that already caches is not wrapped a second time: the produced
 * {@link CachingConnectionFactory} bean is the application's own factory.
 */
@ExtendWith(WeldJunit5Extension.class)
class KeepsExistingCachingFactoryTest {

    static final CachingConnectionFactory EXISTING =
            new CachingConnectionFactory(mock(ConnectionFactory.class));

    @WeldSetup
    WeldInitiator setup = WeldInitiator
            .from(JmsClientProducer.class, CachingConnectionFactoryProducer.class, ExistingFactoryBean.class)
            .build();

    @Inject
    CachingConnectionFactory cachingConnectionFactory;

    @Test
    void isNotWrappedAgain() {
        assertSame(EXISTING, cachingConnectionFactory);
    }

    public static class ExistingFactoryBean {

        @Produces
        public ConnectionFactory connectionFactory() {
            return EXISTING;
        }
    }
}
