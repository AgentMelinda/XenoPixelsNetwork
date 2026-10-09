package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcProfileVisibilityTest {
    @Test void oldProfilesRetainNameplatesAndVisibleDeadBodies() {
        var profile = NpcCombatProfile.fromTag(new CompoundTag());
        assertTrue(profile.showName(false));
        assertTrue(profile.showName(true));
        assertFalse(profile.hideDeadBody);
        assertFalse(profile.showBossBar(true));
        profile.bossBar = true;
        assertTrue(profile.showBossBar(false));
    }

    @Test void conditionalDisplaySurvivesPersistenceAndLiveVisualSync() {
        var server = new NpcCombatProfile();
        server.displayShowName = 2;
        server.bossBar = true;
        server.bossBarMode = 2;
        server.hideDeadBody = true;
        var saved = NpcCombatProfile.fromTag(server.toTag());
        assertFalse(saved.showName(false));
        assertTrue(saved.showName(true));
        assertFalse(saved.showBossBar(false));
        assertTrue(saved.showBossBar(true));
        assertTrue(saved.hideDeadBody);
        var client = new NpcCombatProfile();
        client.applyVisualOptions(server.visualOptionsTag());
        assertEquals(2, client.displayShowName);
        assertTrue(client.hideDeadBody);
        assertEquals(2, client.bossBarMode);
    }

    @Test void displayFlagsClampInvalidPersistedModesAndStayHiddenWhenRequested() {
        var tag = new CompoundTag();
        tag.putInt("DisplayShowName", -4);
        tag.putInt("BossBarMode", 99);
        var profile = NpcCombatProfile.fromTag(tag);
        assertEquals(0, profile.displayShowName);
        assertEquals(1, profile.bossBarMode);
        profile.displayShowName = 1;
        assertFalse(profile.showName(false));
        assertFalse(profile.showName(true));
    }
}
