package io.github.fludakit.jms.config;

import jakarta.jms.Session;

import java.util.Locale;

/**
 * The {@code fluda.jms.acknowledge-mode} values, mapped to the {@link Session} constants that
 * {@link io.github.fludakit.jms.JmsConfig} carries.
 *
 * <p>The values follow Spring Boot's {@code spring.jms.template.session.acknowledge-mode}: the
 * short names {@code auto} (the default), {@code client} and {@code dups-ok}, matched
 * case-insensitively, with {@code _} accepted for {@code -}. A missing value means
 * {@code auto}. A transacted session is not a mode here, exactly as in Spring Boot, where it is
 * the separate {@code transacted} flag: the fluent client never issues a commit, so a transacted
 * context would roll back on close - use an injected {@code JMSContext} and transact around it
 * instead.</p>
 */
enum AcknowledgeMode {

    AUTO("auto", Session.AUTO_ACKNOWLEDGE),
    CLIENT("client", Session.CLIENT_ACKNOWLEDGE),
    DUPS_OK("dups-ok", Session.DUPS_OK_ACKNOWLEDGE);

    private final String propertyValue;
    private final int sessionMode;

    AcknowledgeMode(String propertyValue, int sessionMode) {
        this.propertyValue = propertyValue;
        this.sessionMode = sessionMode;
    }

    /** The {@link Session} mode constant for this value. */
    int sessionMode() {
        return sessionMode;
    }

    /**
     * Resolves a property value.
     *
     * @throws IllegalArgumentException for an unknown value, or for a transacted session
     */
    static AcknowledgeMode from(String value) {
        String normalized = value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.isEmpty()) {
            return AUTO;
        }
        for (AcknowledgeMode mode : values()) {
            if (mode.propertyValue.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(unknownMessage(value, normalized));
    }

    private static String unknownMessage(String value, String normalized) {
        if ("transacted".equals(normalized) || "session-transacted".equals(normalized)) {
            return "fluda.jms.acknowledge-mode=" + value + " is not supported: the client never"
                    + " commits, so a transacted context would roll back on close; use an injected"
                    + " JMSContext and transact around it instead";
        }
        return "Unknown fluda.jms.acknowledge-mode '" + value + "'; expected auto, client or"
                + " dups-ok";
    }
}
