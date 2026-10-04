package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XenoNpcWandRoleSelectionTest {

    @Test
    void anUnconfiguredWandDefaultsToHumanoid() {
        assertEquals(XenoNpcRole.HUMANOID,
                XenoNpcWandRoleSelection.current(new CompoundTag()));
    }

    @Test
    void oldSavedSelectionsDoNotOverrideTheNewNeutralDefault() {
        CompoundTag oldWand = new CompoundTag();
        oldWand.putString("XenoNpcRole", "trader");

        assertEquals(XenoNpcRole.HUMANOID, XenoNpcWandRoleSelection.current(oldWand));
    }

    @Test
    void aShiftSelectedRoleAppliesToOneSpawnAndThenResets() {
        CompoundTag wand = new CompoundTag();
        XenoNpcWandRoleSelection.select(wand, XenoNpcRole.TRADER);

        assertEquals(XenoNpcRole.TRADER, XenoNpcWandRoleSelection.current(wand));
        assertEquals(XenoNpcRole.TRADER, XenoNpcWandRoleSelection.consume(wand));
        assertEquals(XenoNpcRole.HUMANOID, XenoNpcWandRoleSelection.current(wand));
    }
}
