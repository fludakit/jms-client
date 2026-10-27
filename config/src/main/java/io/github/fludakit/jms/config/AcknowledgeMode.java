package io.github.fludakit.jms.config;

import jakarta.jms.Session;

import java.util.Locale;

/**
 * The {@code fluda.jms.acknowledge-mode} values, mapped to the {@link Session} constants that
 * {@link io.github.fludakit.jms.JmsConfig} carries.
 *
 * <p>Values are matched case-insensitively, and {@code _} is accepted for {@code -} so a copied
 * {@code Session.AUTO_ACKNOWLEDGE} constant name also works. A missing value means
 * {@code auto-acknowledge}. {@code session-transacted} is rejected on purpose: the fluent client
 * never issues a commit, so a transacted context would roll back on close.</p>
 */
enum AcknowledgeMode {

    AUTO("auto-acknowledge", Session.AUTO_ACKNOWLEDGE),
    CLIENT("client-acknowledge", Session.CLIENT_ACKNOWLEDGE),
    DUPS_OK("dups-ok-acknowledge", Session.DUPS_OK_ACKNOWLEDGE);

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
     * @throws IllegalArgumentException for an unknown value, or for {@code session-transacted}
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
        if ("session-transacted".equals(normalized)) {
            return "fluda.jms.acknowledge-mode=session-transacted is not supported: the client never"
                    + " commits, so a transacted context would roll back on close; use an injected"
                    + " JMSContext and transact around it instead";
        }
        return "Unknown fluda.jms.acknowledge-mode '" + value + "'; expected auto-acknowledge,"
                + " client-acknowledge or dups-ok-acknowledge";
    }
}
