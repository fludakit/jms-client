package io.github.fludakit.jms.cdi;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Selects the {@link io.github.fludakit.jms.CachingConnectionFactory} produced by this module.
 *
 * <p>The application's own {@code jakarta.jms.ConnectionFactory} bean stays the unqualified
 * ({@code @Default}) one, so producers that need the raw factory keep resolving it without an
 * ambiguity. Inject this qualifier when the caching factory itself is needed - typically to call
 * {@link io.github.fludakit.jms.CachingConnectionFactory#reset()} after a broker restart:</p>
 *
 * <pre>{@code
 * @Inject
 * @Caching
 * CachingConnectionFactory connectionFactory;
 *
 * connectionFactory.reset();   // the next client opens a fresh context
 * }</pre>
 */
@Qualifier
@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD, FIELD, PARAMETER})
public @interface Caching {
}
