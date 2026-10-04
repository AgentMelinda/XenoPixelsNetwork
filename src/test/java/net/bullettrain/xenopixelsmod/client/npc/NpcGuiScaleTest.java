package net.bullettrain.xenopixelsmod.client.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2026-09-30 owner: "gui scale dosn't effect npc want gui".
 *
 * <p>DragonMineZ's {@code ScaledScreen} multiplies its menus by {@code floor(sqrt(available))},
 * where {@code available} is the GUI-scaled window over 320x240. On 1920x1080 that is 2 at GUI Scale
 * 1 and 1 at GUI Scale 2, so both drew the NPC editor at the same size. Following the GUI Scale means
 * contributing no extra factor of our own.
 */
class NpcGuiScaleTest {

    @Test
    void followingTheGuiScaleAddsNoFactorOfItsOwn() {
        assertEquals(1.0f, NpcGuiScale.dynamicScale(true, 2.1213f));
        assertEquals(1.0f, NpcGuiScale.dynamicScale(true, 1.0f));
    }

    @Test
    void theDmzAdaptiveScaleIsKeptWhenSwitchedOff() {
        assertEquals(2.1213f, NpcGuiScale.dynamicScale(false, 2.1213f));
    }
}
