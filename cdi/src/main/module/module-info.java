/**
 * Module descriptor for the published CDI JAR.
 *
 * <p>Kept outside {@code src/main/java} on purpose: it is not compiled by javac (tests and surefire
 * stay on the classpath), it is added to the JAR by moditect during {@code package}.</p>
 *
 * <p>The bean package is {@code opens} because the CDI implementation (Weld in the tests, the
 * server's CDI container on Jakarta EE) reflects over producer methods and qualifiers.</p>
 */
module fluda.jms.client.cdi {
    requires transitive fluda.jms.client.core;
    requires transitive jakarta.cdi;
    requires transitive jakarta.messaging;

    exports io.github.fludakit.jms.cdi;
    opens io.github.fludakit.jms.cdi;
}
