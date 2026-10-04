package net.bullettrain.xenopixelsmod.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A definable respawn point, a leash radius, and a respawn that can be turned off.
 *
 * <p>All three already had somewhere to live - {@code homeX/Y/Z}, {@code leashRadius} - but no way
 * to set them: {@code setHome} was called only by the wand at placement, so an NPC respawned
 * wherever it first happened to be put, forever. Respawn itself had no switch at all and no
 * configurable delay; every NPC came back after a hardcoded 100 ticks.
 */
class XenoNpcHomeAndRespawnTest {

    // ------------------------------------------------------------ home

    @Test
    void aHomeSurvivesASaveAndLoad() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setHome(100.5, 64.0, -200.5);

        XenoNpcData reloaded = XenoNpcData.fromTag(data.toTag(), XenoNpcRole.HUMANOID);
        assertTrue(reloaded.hasHome());
        assertEquals(100.5, reloaded.homeX());
        assertEquals(64.0, reloaded.homeY());
        assertEquals(-200.5, reloaded.homeZ());
    }

    @Test
    void noHomeIsDistinctFromAHomeAtTheOrigin() {
        // homeSet exists precisely so an NPC placed at 0,0,0 is not mistaken for one that never
        // had a home - the second falls back to dying where it fell, the first does not.
        XenoNpcData never = new XenoNpcData(XenoNpcRole.HUMANOID);
        assertFalse(XenoNpcData.fromTag(never.toTag(), XenoNpcRole.HUMANOID).hasHome());

        XenoNpcData atOrigin = new XenoNpcData(XenoNpcRole.HUMANOID);
        atOrigin.setHome(0.0, 0.0, 0.0);
        assertTrue(XenoNpcData.fromTag(atOrigin.toTag(), XenoNpcRole.HUMANOID).hasHome());
    }

    @Test
    void aNonFiniteHomeIsRefusedRatherThanStored() {
        // These arrive in a save packet. A NaN home would make every distance comparison false and
        // the leash would silently stop working.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setHome(Double.NaN, 64.0, 0.0);
        assertFalse(data.hasHome(), "a home with a non-finite coordinate is not a home");
    }

    @Test
    void aHomeFarOutsideTheWorldIsClamped() {
        // The world border caps at 30 million; a crafted save must not be able to schedule a
        // respawn at a coordinate the server cannot load.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setHome(1.0e12, 64.0, -1.0e12);
        assertTrue(Math.abs(data.homeX()) <= XenoNpcData.MAX_HOME_COORDINATE);
        assertTrue(Math.abs(data.homeZ()) <= XenoNpcData.MAX_HOME_COORDINATE);
    }

    // ------------------------------------------------------------ leash

    @Test
    void theLeashClampsRatherThanRefuses() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setLeashRadius(-5.0);
        assertEquals(0.0, data.leashRadius(), "negative means disabled, not negative");

        data.setLeashRadius(1.0e9);
        assertEquals(512.0, data.leashRadius(), "clamped to the documented ceiling");
    }

    @Test
    void aZeroLeashDisablesIt() {
        // Documented on the field as "Zero disables the leash" - pinned so a later clamp change
        // cannot quietly turn every NPC into a wanderer or a prisoner.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setLeashRadius(0.0);
        assertEquals(0.0, XenoNpcData.fromTag(data.toTag(), XenoNpcRole.HUMANOID).leashRadius());
    }

    @Test
    void anNpcThatSetsNothingKeepsTheOldDefaultLeash() {
        // The compatibility invariant: every NPC already in a world must behave exactly as before.
        assertEquals(XenoNpcData.DEFAULT_LEASH_RADIUS,
                XenoNpcData.fromTag(new CompoundTag(), XenoNpcRole.HUMANOID).leashRadius());
    }

    // ------------------------------------------------------------ respawn

    @Test
    void respawnIsOnByDefault() {
        // Every NPC respawned before this field existed. Defaulting to off would silently stop
        // every NPC in every existing world from coming back.
        assertTrue(new XenoNpcData(XenoNpcRole.HUMANOID).respawnEnabled());
        assertTrue(XenoNpcData.fromTag(new CompoundTag(), XenoNpcRole.HUMANOID).respawnEnabled());
    }

    @Test
    void respawnCanBeTurnedOffAndSurvivesASaveAndLoad() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setRespawnEnabled(false);
        assertFalse(XenoNpcData.fromTag(data.toTag(), XenoNpcRole.HUMANOID).respawnEnabled());
    }

    @Test
    void theDefaultDelayIsTheOldHardcodedOne() {
        // 100 ticks is what XenoNpcEntity.die used before the field existed.
        assertEquals(100, new XenoNpcData(XenoNpcRole.HUMANOID).respawnDelayTicks());
        assertEquals(100, XenoNpcData.fromTag(new CompoundTag(), XenoNpcRole.HUMANOID).respawnDelayTicks());
    }

    @Test
    void theDelayClampsIntoARangeAServerCanHonour() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setRespawnDelayTicks(0);
        assertEquals(1, data.respawnDelayTicks(), "zero would respawn inside the death tick");

        data.setRespawnDelayTicks(Integer.MAX_VALUE);
        assertEquals(XenoNpcData.MAX_RESPAWN_DELAY_TICKS, data.respawnDelayTicks());
    }

    @Test
    void aRespawnDelaySurvivesASaveAndLoad() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setRespawnDelayTicks(600);
        assertEquals(600, XenoNpcData.fromTag(data.toTag(), XenoNpcRole.HUMANOID).respawnDelayTicks());
    }
}
