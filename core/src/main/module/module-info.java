/**
 * Module descriptor for the published JAR.
 *
 * <p>Kept outside {@code src/main/java} on purpose: it is not compiled by javac (tests and surefire
 * stay on the classpath), it is added to the JAR by moditect during {@code package}.</p>
 *
 * <p>{@code jakarta.json.bind} is {@code static} so the module resolves without JSON-B; JSON-B is
 * only needed when {@code JsonbMessageConverter} is actually used at runtime.</p>
 *
 * <p>{@code java.naming} is required by {@code JndiDestinationResolver}; it is part of the JDK
 * and always available.</p>
 */
module fluda.jms.core {
    requires transitive jakarta.messaging;
    requires java.naming;
    requires static jakarta.json.bind;

    exports io.github.fludakit.jms;
    exports io.github.fludakit.jms.support;
}
