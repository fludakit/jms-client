package io.github.fludakit.jms;

import java.util.Objects;

/**
 * Immutable configuration for a {@link JmsClient}: credentials, the JMS session mode, and an
 * optional default destination.
 *
 * <p>Built through {@link JmsClient#builder} - this type is used when the settings must travel
 * (for example from the config module's {@code fluda.jms.*} properties).</p>
 */
public final class JmsConfig {

    private final String username;
    private final String password;
    private final int sessionMode;
    private final String defaultDestinationName;

    private JmsConfig(Builder builder) {
        this.username = builder.username;
        this.password = builder.password;
        this.sessionMode = builder.sessionMode;
        this.defaultDestinationName = builder.defaultDestinationName;
    }

    /** The broker username, or {@code null} for anonymous access. */
    public String username() {
        return username;
    }

    /** The broker password, or {@code null}. */
    public String password() {
        return password;
    }

    /** The JMS session mode ({@link jakarta.jms.Session} constants). */
    public int sessionMode() {
        return sessionMode;
    }

    /** The default destination name, or {@code null} when every operation names its destination. */
    public String defaultDestinationName() {
        return defaultDestinationName;
    }

    /** Builder for a {@link JmsConfig}. */
    public static final class Builder {

        private String username;
        private String password;
        private int sessionMode = jakarta.jms.Session.AUTO_ACKNOWLEDGE;
        private String defaultDestinationName;

        /** Sets the broker username. */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /** Sets the broker password. */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the JMS session mode ({@link jakarta.jms.Session} constants; default
         * {@code AUTO_ACKNOWLEDGE}).
         */
        public Builder sessionMode(int sessionMode) {
            this.sessionMode = sessionMode;
            return this;
        }

        /** Sets the default destination name used when an operation does not name one. */
        public Builder defaultDestination(String defaultDestinationName) {
            this.defaultDestinationName = defaultDestinationName;
            return this;
        }

        /** Builds the immutable config. */
        public JmsConfig build() {
            return new JmsConfig(this);
        }
    }
}
