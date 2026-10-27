package io.github.fludakit.jms.config;

import jakarta.jms.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcknowledgeModeTest {

    @Test
    void mapsTheDocumentedValues() {
        assertEquals(Session.AUTO_ACKNOWLEDGE, AcknowledgeMode.from("auto-acknowledge").sessionMode());
        assertEquals(Session.CLIENT_ACKNOWLEDGE, AcknowledgeMode.from("client-acknowledge").sessionMode());
        assertEquals(Session.DUPS_OK_ACKNOWLEDGE, AcknowledgeMode.from("dups-ok-acknowledge").sessionMode());
    }

    @Test
    void defaultsToAutoAcknowledgeWhenUnset() {
        assertEquals(Session.AUTO_ACKNOWLEDGE, AcknowledgeMode.from(null).sessionMode());
        assertEquals(Session.AUTO_ACKNOWLEDGE, AcknowledgeMode.from("").sessionMode());
        assertEquals(Session.AUTO_ACKNOWLEDGE, AcknowledgeMode.from("   ").sessionMode());
    }

    @Test
    void ignoresCaseAndAcceptsConstantNames() {
        assertEquals(Session.CLIENT_ACKNOWLEDGE, AcknowledgeMode.from("CLIENT_ACKNOWLEDGE").sessionMode());
        assertEquals(Session.AUTO_ACKNOWLEDGE, AcknowledgeMode.from(" Auto-Acknowledge ").sessionMode());
    }

    @Test
    void rejectsSessionTransactedWithGuidance() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> AcknowledgeMode.from("session-transacted"));
        assertTrue(error.getMessage().contains("session-transacted"));
        assertTrue(error.getMessage().contains("JMSContext"));
    }

    @Test
    void rejectsUnknownValuesListingTheAcceptedOnes() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> AcknowledgeMode.from("sometimes"));
        assertTrue(error.getMessage().contains("sometimes"));
        assertTrue(error.getMessage().contains("auto-acknowledge"));
        assertTrue(error.getMessage().contains("client-acknowledge"));
        assertTrue(error.getMessage().contains("dups-ok-acknowledge"));
    }
}
