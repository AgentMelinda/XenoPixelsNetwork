package net.bullettrain.xenopixelsmod.network.packet;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XenoNpcDisplaySavePolicyTest {
    @Test void displayModesAreAcceptedOnlyWithTheirRealWireTypesAndRanges() {
        var tag = new CompoundTag();
        tag.putInt("DisplayShowName", 2);
        tag.putInt("BossBarMode", 2);
        tag.putBoolean("HideDeadBody", true);
        assertTrue(XenoNpcSavePolicy.validate(tag).accepted());
        tag.putInt("DisplayShowName", 3);
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
        tag.putString("DisplayShowName", "2");
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
        tag.putInt("DisplayShowName", 1);
        tag.putInt("BossBarMode", 0);
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
        tag.putInt("BossBarMode", 1);
        tag.putString("HideDeadBody", "true");
        assertFalse(XenoNpcSavePolicy.validate(tag).accepted());
    }
}
