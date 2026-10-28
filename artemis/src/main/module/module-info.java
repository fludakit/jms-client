/**
 * Module descriptor for the published Artemis provider JAR.
 *
 * <p>Kept outside {@code src/main/java} on purpose: it is not compiled by javac (tests and surefire
 * stay on the classpath), it is added to the JAR by moditect during {@code package}.</p>
 *
 * <p>The bean package is {@code opens} because the CDI implementation reflects over the producer
 * method and the property fields.</p>
 */
module fluda.jms.artemis {
    requires transitive jakarta.messaging;
    requires jakarta.cdi;
    requires microprofile.config.api;
    requires fluda.jms.config;
    requires artemis.jakarta.client;

    exports io.github.fludakit.jms.artemis;
    opens io.github.fludakit.jms.artemis;
}
