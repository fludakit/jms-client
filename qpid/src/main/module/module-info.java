module fluda.jms.qpid {
    requires transitive jakarta.messaging;
    requires jakarta.cdi;
    requires microprofile.config.api;
    requires fluda.jms.config;
    requires qpid.jms.client;

    exports io.github.fludakit.jms.qpid;
    opens io.github.fludakit.jms.qpid;
}
