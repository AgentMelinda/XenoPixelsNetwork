package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-09-29 owner: "it should be as default the v9 brain not v1 and all skills under dmz toggeled
 * on with max level of every skill that exist in skills menu xenonpc dmz tab".
 *
 * <p>Applies to an NPC nobody has configured yet (no stored profile). A saved profile keeps what
 * it says - an old save without a brain tag still reads V1.
 */
class NpcFreshProfileDefaultsTest {
    private static final List<String> IDS = List.of("fly", "jump", "kisense", "kicontrol");

    /** DMZ's own maximum (its cost table), as the skills tab shows it. */
    private static int max(String id) {
        return NpcSkillSet.maxLevelOf(id);
    }

    @Test
    void aFreshNpcRunsTheV9Brain() {
        NpcCombatProfile fresh = NpcCombatProfile.freshDefaults(false, IDS,
                NpcFreshProfileDefaultsTest::max);
        assertEquals(NpcCombatBrainVersion.V9, fresh.brainVersion);
        assertTrue(fresh.combatBrain, "a new NPC fights with its brain switched on");
        assertTrue(fresh.noFallDamage, "a new NPC takes no fall damage");
        assertFalse(fresh.brainDeflectBlast, "V9's own safe deflection defaults still apply");
        assertFalse(fresh.brainDeflectWave);
    }

    @Test
    void everySkillInTheDmzTabIsOnAtItsMax() {
        NpcCombatProfile fresh = NpcCombatProfile.freshDefaults(true, IDS,
                NpcFreshProfileDefaultsTest::max);
        for (String id : IDS) {
            assertTrue(fresh.skills.isActive(id), id);
            assertEquals(max(id), fresh.skills.level(id), id);
        }
        assertTrue(fresh.flySkillOn, "the Fly row and the fly skill agree");
        assertEquals(NpcCombatProfile.clampFlySkillLevel(max("fly")), fresh.flySkillLevel);
    }

    @Test
    void kiManipulationIsLearnedButDoesNotDrawTheKiWeapon() {
        NpcCombatProfile fresh = NpcCombatProfile.freshDefaults(true, List.of("kimanipulation", "fly"),
                NpcFreshProfileDefaultsTest::max);
        assertFalse(fresh.skills.isActive(NpcCombatProfile.KI_WEAPON_SKILL),
                "an active kimanipulation shows the ki weapon; the KI Weapon toggle owns that");
        assertEquals(max("kimanipulation"), fresh.skills.level("kimanipulation"));
        assertFalse(fresh.kiWeaponOn);
    }

    @Test
    void aFreshNpcHasBodyTypeTwoAndHairStyleOne() {
        // 2026-09-30 owner: "default of dmz body type 2 hair on by default and hair number 1".
        NpcCombatProfile fresh = NpcCombatProfile.freshDefaults(true, IDS, NpcFreshProfileDefaultsTest::max);
        assertEquals(2, fresh.appearance.bodyType);
        assertTrue(fresh.hairEnabled);
        assertEquals(1, fresh.hairStyleId);
    }

    @Test
    void savedNativeNpcsGetNoFallDamageOnceAndKeepAnAuthorsChoice() {
        NpcCombatProfile old = NpcCombatProfile.fromTag(new CompoundTag());
        NpcCombatProfile.applyNativeFallDefault(true, old);
        assertTrue(old.noFallDamage, "an NPC saved before the default stops dying from falls");
        old.noFallDamage = false;
        NpcCombatProfile reread = NpcCombatProfile.fromTag(old.toTag());
        NpcCombatProfile.applyNativeFallDefault(true, reread);
        assertFalse(reread.noFallDamage, "an author who turned it back off keeps that");
        NpcCombatProfile foreign = NpcCombatProfile.fromTag(new CompoundTag());
        NpcCombatProfile.applyNativeFallDefault(false, foreign);
        assertFalse(foreign.noFallDamage, "CustomNPCs / My NPCs NPCs are not touched");
    }

    @Test
    void aSavedProfileKeepsWhatItSays() {
        // The release's default brain: V1 in XenoPixels, V9 in XenoNPCs (NpcBrainPolicy).
        assertEquals(NpcBrainPolicy.resolve(NpcCombatBrainVersion.V1),
                NpcCombatProfile.fromTag(new CompoundTag()).brainVersion);
        assertTrue(NpcCombatProfile.fromTag(new CompoundTag()).skills.isEmpty());
    }
}
