package net.bullettrain.xenopixelsmod.client.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XenoNpcEditorRevisionTest {

    @Test
    void aFreshNpcKeepsRevisionZeroSoItsFirstSaveIsAccepted() {
        CompoundTag payload = new CompoundTag();
        payload.putInt("Revision", 0);

        assertEquals(0, XenoNpcEditorScreen.revisionFromPayload(payload));
    }

    @Test
    void olderOrInvalidRevisionsClampToZero() {
        CompoundTag payload = new CompoundTag();
        payload.putInt("Revision", -5);

        assertEquals(0, XenoNpcEditorScreen.revisionFromPayload(payload));
        assertEquals(0, XenoNpcEditorScreen.revisionFromPayload(null));
    }
}
