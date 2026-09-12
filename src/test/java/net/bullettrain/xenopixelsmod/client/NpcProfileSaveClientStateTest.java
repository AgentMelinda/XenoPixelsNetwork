package net.bullettrain.xenopixelsmod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcProfileSaveClientStateTest {
    @Test
    void acceptedSaveCarriesNoNotice() {
        NpcProfileSaveClientState.accept(true, "NPC saved");

        assertTrue(NpcProfileSaveClientState.success());
        assertEquals("NPC saved", NpcProfileSaveClientState.message());
        assertFalse(NpcProfileSaveClientState.consumeNotice());
    }

    @Test
    void rejectionIsAnnouncedOnlyOnce() {
        NpcProfileSaveClientState.accept(false, "NPC save rejected: NPC is more than 64 blocks away");

        assertFalse(NpcProfileSaveClientState.success());
        assertTrue(NpcProfileSaveClientState.consumeNotice());
        assertFalse(NpcProfileSaveClientState.consumeNotice());
    }

    @Test
    void sequenceAdvancesPerAnswer() {
        int before = NpcProfileSaveClientState.sequence();
        NpcProfileSaveClientState.accept(true, "NPC saved");
        assertEquals(before + 1, NpcProfileSaveClientState.sequence());
    }
}