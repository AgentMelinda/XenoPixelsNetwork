package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawn anchor and leash, and the respawn payload that used to lose everything.
 */
class XenoNpcHomeTest {

    @Test
    void aHomeSurvivesSaveAndLoad() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.GUARD);
        assertFalse(data.hasHome(), "a fresh NPC has no anchor until it is placed");

        data.setHome(100.5, 64.0, -200.5);
        XenoNpcData back = XenoNpcData.fromTag(data.toTag(), XenoNpcRole.GUARD);

        assertTrue(back.hasHome());
        assertEquals(100.5, back.homeX(), 1.0e-6);
        assertEquals(64.0, back.homeY(), 1.0e-6);
        assertEquals(-200.5, back.homeZ(), 1.0e-6);
    }

    @Test
    void aHomeAtTheOriginIsStillAHome() {
        // The reason hasHome is a flag rather than a null check: 0,0,0 is a legitimate spawn point.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setHome(0, 0, 0);
        assertTrue(XenoNpcData.fromTag(data.toTag(), XenoNpcRole.HUMANOID).hasHome());
    }

    @Test
    void anNpcSavedBeforeTheLeashExistedKeepsTheDefault() {
        // Reading a missing double as 0 would disable the leash for every existing NPC.
        XenoNpcData old = XenoNpcData.fromTag(new CompoundTag(), XenoNpcRole.HUMANOID);
        assertEquals(XenoNpcData.DEFAULT_LEASH_RADIUS, old.leashRadius(), 1.0e-6);
        assertFalse(old.hasHome());
    }

    @Test
    void theLeashRadiusIsBounded() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);

        data.setLeashRadius(-5);
        assertEquals(0.0, data.leashRadius(), 1.0e-6, "negative disables rather than inverts");

        data.setLeashRadius(99999);
        assertEquals(512.0, data.leashRadius(), 1.0e-6);

        data.setLeashRadius(Double.NaN);
        assertEquals(0.0, data.leashRadius(), 1.0e-6);

        data.setLeashRadius(48);
        assertEquals(48.0, data.leashRadius(), 1.0e-6);
    }

    @Test
    void restoreFromTagCarriesHomeAndLeash() {
        // The respawn path restores through this rather than constructing fresh, so a home lost
        // here is a home lost on every death.
        XenoNpcData source = new XenoNpcData(XenoNpcRole.TRADER);
        source.setHome(12, 70, 34);
        source.setLeashRadius(10);

        XenoNpcData target = new XenoNpcData(XenoNpcRole.TRADER);
        target.restoreFromTag(source.toTag());

        assertTrue(target.hasHome());
        assertEquals(12, target.homeX(), 1.0e-6);
        assertEquals(10.0, target.leashRadius(), 1.0e-6);
    }

    @Test
    void theRespawnPayloadCarriesTheProfileNotJustIdentity() {
        // The bug behind "on death and respawn it loses saved values": die() scheduled with
        // npcData.toTag(), which is identity only, so an NPC came back with default stats.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.GUARD);
        data.setDisplayName("Nappa");
        data.setHome(1, 2, 3);

        NpcCombatProfile profile = new NpcCombatProfile();
        profile.strength = 9001;
        profile.fireImmune = true;

        CompoundTag payload = data.toTag();
        assertFalse(payload.contains("Profile"), "identity alone carries no stats");

        payload.put("Profile", profile.toTag());

        NpcCombatProfile back = NpcCombatProfile.fromTag(payload.getCompound("Profile"));
        assertEquals(9001, back.strength);
        assertTrue(back.fireImmune);
        assertEquals("Nappa", payload.getString("Name"));
        assertTrue(payload.contains("HomeX"));
    }
}
