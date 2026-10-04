package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.BossEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour flags behind the reference's Stats and Display rows.
 */
class XenoNpcBehaviourTest {

    @Test
    void everyBehaviourFlagSurvivesARoundTrip() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.fireImmune = true;
        profile.burnsInSun = true;
        profile.canDrown = false;
        profile.noFallDamage = true;
        profile.potionImmune = true;
        profile.cobwebAffected = false;
        profile.visible = false;
        profile.hitboxScale = 2.5f;
        profile.bossBar = true;
        profile.bossBarColor = "RED";
        profile.creatureType = "undead";

        NpcCombatProfile back = NpcCombatProfile.fromTag(profile.toTag());

        assertTrue(back.fireImmune);
        assertTrue(back.burnsInSun);
        assertFalse(back.canDrown);
        assertTrue(back.noFallDamage);
        assertTrue(back.potionImmune);
        assertFalse(back.cobwebAffected);
        assertFalse(back.visible);
        assertEquals(2.5f, back.hitboxScale, 1.0e-4);
        assertTrue(back.bossBar);
        assertEquals("RED", back.bossBarColor);
        assertEquals("undead", back.creatureType);
    }

    @Test
    void anOlderProfileKeepsTheShippedDefaultsRatherThanReadingAsFalse() {
        // A tag written before this block existed has none of these keys. Reading a missing boolean
        // as false would silently stop every existing NPC drowning and let them phase through
        // cobwebs - so the three defaults-to-true flags check for presence.
        NpcCombatProfile old = NpcCombatProfile.fromTag(new CompoundTag());

        assertTrue(old.canDrown, "an existing NPC must keep drowning");
        assertTrue(old.cobwebAffected, "an existing NPC must keep being slowed by cobwebs");
        assertTrue(old.visible, "an existing NPC must stay visible");
        assertEquals(1.0f, old.hitboxScale, 1.0e-4);
        // These correctly default to off.
        assertFalse(old.fireImmune);
        assertFalse(old.potionImmune);
    }

    @Test
    void hitboxScaleIsClampedToSomethingRenderable() {
        // A zero or negative box would break collision; an enormous one breaks culling.
        assertEquals(1.0f, NpcCombatProfile.clampHitbox(0f), 1.0e-4);
        assertEquals(1.0f, NpcCombatProfile.clampHitbox(-3f), 1.0e-4);
        assertEquals(1.0f, NpcCombatProfile.clampHitbox(Float.NaN), 1.0e-4);
        assertEquals(0.25f, NpcCombatProfile.clampHitbox(0.01f), 1.0e-4);
        assertEquals(4.0f, NpcCombatProfile.clampHitbox(99f), 1.0e-4);
        assertEquals(2.0f, NpcCombatProfile.clampHitbox(2.0f), 1.0e-4);
    }

    @Test
    void nativeVisualSizeScalesHitboxUnlessTheServerSwitchIsOff() {
        assertEquals(1.0f, NpcCombatProfile.visualSizeScale(0), 1.0e-4);
        assertEquals(1.0f, NpcCombatProfile.visualSizeScale(5), 1.0e-4);
        assertEquals(6.0f, NpcCombatProfile.visualSizeScale(30), 1.0e-4);
        assertEquals(3.0f, NpcCombatProfile.effectiveHitboxScale(1.5f, 10, true), 1.0e-4);
        assertEquals(1.5f, NpcCombatProfile.effectiveHitboxScale(1.5f, 10, false), 1.0e-4);
        assertEquals(0.05f, NpcCombatProfile.effectiveHitboxScale(0.25f, 1, true), 1.0e-4);
        assertEquals(24.0f, NpcCombatProfile.effectiveHitboxScale(4.0f, 30, true), 1.0e-4);
    }

    @Test
    void anNpcThatCannotDrownKeepsItsAir() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.canDrown = false;
        // 260 was the air before the superclass decremented it to 259.
        assertEquals(260, XenoNpcBehaviour.airSupply(profile, 260, 259));

        profile.canDrown = true;
        assertEquals(259, XenoNpcBehaviour.airSupply(profile, 260, 259));
        // A null profile must not make an NPC immortal underwater.
        assertEquals(259, XenoNpcBehaviour.airSupply(null, 260, 259));
    }

    @Test
    void bossBarColourFallsBackRatherThanThrowing() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.bossBarColor = "red";
        assertEquals(BossEvent.BossBarColor.RED, XenoNpcBehaviour.bossBarColor(profile));

        profile.bossBarColor = "octarine";
        assertEquals(BossEvent.BossBarColor.PURPLE, XenoNpcBehaviour.bossBarColor(profile));

        profile.bossBarColor = null;
        assertEquals(BossEvent.BossBarColor.PURPLE, XenoNpcBehaviour.bossBarColor(profile));
        assertEquals(BossEvent.BossBarColor.PURPLE, XenoNpcBehaviour.bossBarColor(null));
    }

    @Test
    void theEditorCyclesRealValues() {
        // Both lists feed cycles, so they must be non-empty and contain the stored defaults.
        assertTrue(XenoNpcBehaviour.bossBarColors().contains("PURPLE"));
        assertEquals(BossEvent.BossBarColor.values().length,
                XenoNpcBehaviour.bossBarColors().size());
        assertTrue(XenoNpcBehaviour.creatureTypes().contains("normal"));
        assertTrue(XenoNpcBehaviour.creatureTypes().contains("undead"));
    }
}
