/**
 * Module descriptor for the published config JAR.
 *
 * <p>Kept outside {@code src/main/java} on purpose: it is not compiled by javac (tests and surefire
 * stay on the classpath), it is added to the JAR by moditect during {@code package}.</p>
 *
 * <p>The bean package is {@code opens} because the CDI implementation and the MicroProfile Config
 * extension reflect over the producer method and the property fields.</p>
 */
module fluda.jms.client.config {
    requires transitive fluda.jms.client.core;
    requires jakarta.cdi;
    requires microprofile.config.api;
    // optional provider clients: only the one whose section is configured must be present at runtime
    requires static artemis.jakarta.client;
    requires static qpid.jms.client;

    exports io.github.fludakit.jms.config;
    opens io.github.fludakit.jms.config;
}
