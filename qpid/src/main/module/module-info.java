/**
 * Module descriptor for the published Qpid provider JAR.
 *
 * <p>Kept outside {@code src/main/java} on purpose: it is not compiled by javac (tests and surefire
 * stay on the classpath), it is added to the JAR by moditect during {@code package}.</p>
 *
 * <p>The bean package is {@code opens} because the CDI implementation reflects over the producer
 * method and the property fields.</p>
 */
module fluda.jms.qpid {
    requires transitive jakarta.messaging;
    requires jakarta.cdi;
    requires microprofile.config.api;
    requires fluda.jms.config;
    requires qpid.jms.client;

    exports io.github.fludakit.jms.qpid;
    opens io.github.fludakit.jms.qpid;
}
