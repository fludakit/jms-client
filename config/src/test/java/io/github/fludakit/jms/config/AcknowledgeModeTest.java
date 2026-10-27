package io.github.fludakit.jms.config;

import jakarta.jms.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcknowledgeModeTest {

    @Test
    void mapsTheDocumentedValues() {
        assertEquals(Session.AUTO_ACKNOWLEDGE, AcknowledgeMode.from("auto").sessionMode());
        assertEquals(Session.CLIENT_ACKNOWLEDGE, AcknowledgeMode.from("client").sessionMode());
        assertEquals(Session.DUPS_OK_ACKNOWLEDGE, AcknowledgeMode.from("dups-ok").sessionMode());
    }

    @Test
    void defaultsToAutoAcknowledgeWhenUnset() {
        assertEquals(Session.AUTO_ACKNOWLEDGE, AcknowledgeMode.from(null).sessionMode());
        assertEquals(Session.AUTO_ACKNOWLEDGE, AcknowledgeMode.from("").sessionMode());
        assertEquals(Session.AUTO_ACKNOWLEDGE, AcknowledgeMode.from("   ").sessionMode());
    }

    @Test
    void ignoresCaseAndAcceptsUnderscores() {
        assertEquals(Session.CLIENT_ACKNOWLEDGE, AcknowledgeMode.from("CLIENT").sessionMode());
        assertEquals(Session.DUPS_OK_ACKNOWLEDGE, AcknowledgeMode.from("DUPS_OK").sessionMode());
        assertEquals(Session.AUTO_ACKNOWLEDGE, AcknowledgeMode.from(" Auto ").sessionMode());
    }

    @Test
    void rejectsTransactedSessionsWithGuidance() {
        for (String value : new String[]{"transacted", "session-transacted", "SESSION_TRANSACTED"}) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> AcknowledgeMode.from(value));
            assertTrue(error.getMessage().contains("transacted"));
            assertTrue(error.getMessage().contains("JMSContext"));
        }
    }

    @Test
    void rejectsUnknownValuesListingTheAcceptedOnes() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> AcknowledgeMode.from("sometimes"));
        assertTrue(error.getMessage().contains("sometimes"));
        assertTrue(error.getMessage().contains("auto"));
        assertTrue(error.getMessage().contains("client"));
        assertTrue(error.getMessage().contains("dups-ok"));
    }

    @Test
    void rejectsTheOldLongNames() {
        // the pre-release spelling is gone; the short values replace it
        assertThrows(IllegalArgumentException.class,
                () -> AcknowledgeMode.from("auto-acknowledge"));
    }
}
