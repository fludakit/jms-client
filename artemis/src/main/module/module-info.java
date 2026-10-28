module fluda.jms.artemis {
    requires transitive jakarta.messaging;
    requires jakarta.cdi;
    requires microprofile.config.api;
    requires fluda.jms.config;
    requires artemis.jakarta.client;

    exports io.github.fludakit.jms.artemis;
    opens io.github.fludakit.jms.artemis;
}
