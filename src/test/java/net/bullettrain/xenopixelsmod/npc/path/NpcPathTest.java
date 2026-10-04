package net.bullettrain.xenopixelsmod.npc.path;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The patrol route: its bounds, its ordering, and the three ways it can end.
 *
 * <p>Pure, which is why the walking rules live on {@link NpcPath} rather than inside the walker —
 * "what comes after point 4 on a ping-pong route" is exactly the sort of thing that is obvious
 * until it is wrong at the turning points, and it needs no entity to check.
 */
class NpcPathTest {

    private static NpcPath of(int count) {
        NpcPath path = new NpcPath();
        for (int i = 0; i < count; i++) {
            path.add(new NpcPath.Point(i, 64, 0));
        }
        return path;
    }

    // ------------------------------------------------------------ shape

    @Test
    void aRouteNeedsTwoPointsToBeWorthWalking() {
        // One point is a place to stand. Treating it as a route would have the NPC endlessly
        // re-path to where it already is.
        assertFalse(new NpcPath().walkable());
        assertFalse(of(1).walkable());
        assertTrue(of(2).walkable());
    }

    @Test
    void theCapHoldsOnAddAndOnRead() {
        NpcPath path = of(NpcPath.MAX_POINTS + 10);
        assertEquals(NpcPath.MAX_POINTS, path.size());

        // And a hand-edited tag claiming more is truncated rather than trusted.
        CompoundTag tag = new CompoundTag();
        ListTag oversized = new ListTag();
        for (int i = 0; i < 200; i++) {
            oversized.add(new NpcPath.Point(i, 64, 0).save());
        }
        tag.put("Points", oversized);
        assertEquals(NpcPath.MAX_POINTS, NpcPath.load(tag).size());
    }

    @Test
    void speedIsBounded() {
        NpcPath path = new NpcPath();
        path.setSpeed(500.0);
        assertEquals(NpcPath.MAX_SPEED, path.speed());
        path.setSpeed(-3.0);
        assertEquals(NpcPath.MIN_SPEED, path.speed());
    }

    @Test
    void aRouteSurvivesASaveAndLoadInOrder() {
        NpcPath path = new NpcPath();
        path.add(new NpcPath.Point(1, 64, 2));
        path.add(new NpcPath.Point(3, 65, 4));
        path.setMode(NpcPath.Mode.PING_PONG);
        path.setSpeed(1.4);

        NpcPath read = NpcPath.load(path.save());
        assertEquals(2, read.size());
        assertEquals(new NpcPath.Point(1, 64, 2), read.get(0));
        assertEquals(new NpcPath.Point(3, 65, 4), read.get(1));
        assertEquals(NpcPath.Mode.PING_PONG, read.mode());
        assertEquals(1.4, read.speed(), 1e-6);
    }

    @Test
    void anUnknownModeFoldsToLoopRatherThanThrowing() {
        // A world saved with a mode a later build removed must still load.
        assertEquals(NpcPath.Mode.LOOP, NpcPath.Mode.byId("spiral"));
        assertEquals(NpcPath.Mode.LOOP, NpcPath.Mode.byId(null));
        assertEquals(NpcPath.Mode.PING_PONG, NpcPath.Mode.byId("  ping_pong  "));
    }

    @Test
    void anIndexTheRouteDoesNotHaveIsNullNotAnException() {
        NpcPath path = of(2);
        assertNull(path.get(-1));
        assertNull(path.get(99));
    }

    // ------------------------------------------------------------ editing

    @Test
    void swapReordersAndIsQuietAtTheEnds() {
        // Driven by a button, and the ends of the list are exactly where it will be pressed.
        NpcPath path = of(3);
        path.swap(0, 1);
        assertEquals(new NpcPath.Point(1, 64, 0), path.get(0));
        path.swap(0, -1);
        path.swap(2, 3);
        assertEquals(3, path.size(), "out-of-range swaps change nothing");
    }

    @Test
    void removeAndClearDoWhatTheySay() {
        NpcPath path = of(3);
        path.remove(1);
        assertEquals(List.of(new NpcPath.Point(0, 64, 0), new NpcPath.Point(2, 64, 0)),
                path.points());
        path.remove(99);
        assertEquals(2, path.size());
        path.clear();
        assertTrue(path.isEmpty());
    }

    // ------------------------------------------------------------ where next

    @Test
    void aLoopReturnsToTheStart() {
        NpcPath path = of(3);
        path.setMode(NpcPath.Mode.LOOP);
        assertEquals(1, path.next(0, true));
        assertEquals(2, path.next(1, true));
        assertEquals(0, path.next(2, true), "and round again");
    }

    @Test
    void onceStopsAtTheEnd() {
        NpcPath path = of(3);
        path.setMode(NpcPath.Mode.ONCE);
        assertEquals(2, path.next(1, true));
        assertEquals(-1, path.next(2, true), "-1 is how the walker knows to stop");
    }

    @Test
    void pingPongTurnsAroundAtBothEnds() {
        // The case that is obvious until it is wrong: at the last point the next step is the one
        // before it, not the last one again, or the NPC would stand still for a cycle.
        NpcPath path = of(4);
        path.setMode(NpcPath.Mode.PING_PONG);

        assertEquals(1, path.next(0, true));
        assertEquals(3, path.next(2, true));
        assertEquals(2, path.next(3, true), "turn around, do not repeat the end");
        assertEquals(1, path.next(2, false));
        assertEquals(0, path.next(1, false));
        assertEquals(1, path.next(0, false), "and turn around again");
    }

    @Test
    void pingPongDirectionFlipsOnlyAtTheEnds() {
        NpcPath path = of(4);
        path.setMode(NpcPath.Mode.PING_PONG);
        assertTrue(path.nextForward(0, true));
        assertTrue(path.nextForward(2, true));
        assertFalse(path.nextForward(3, true), "the last point turns it around");
        assertFalse(path.nextForward(1, false));
        assertTrue(path.nextForward(0, false), "and so does the first");
    }

    @Test
    void directionIsIgnoredForTheModesThatDoNotTurn() {
        NpcPath loop = of(3);
        assertTrue(loop.nextForward(2, true));
        assertTrue(loop.nextForward(2, false), "a loop only ever goes one way");
    }

    @Test
    void aRouteTooShortToWalkHasNoNextPoint() {
        assertEquals(-1, of(1).next(0, true));
        assertEquals(-1, new NpcPath().next(0, true));
    }

    // ------------------------------------------------------------ on the profile

    @Test
    void anNpcWithNoRouteWritesNoTag() {
        assertFalse(new NpcCombatProfile().toTag().contains("Path"));
    }

    @Test
    void aRouteRidesTheProfileAndSurvivesASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.path.add(new NpcPath.Point(10, 70, -4));
        profile.path.setMode(NpcPath.Mode.ONCE);

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(1, read.path.size());
        assertEquals(new NpcPath.Point(10, 70, -4), read.path.get(0));
        assertEquals(NpcPath.Mode.ONCE, read.path.mode());
    }

    @Test
    void theGuardTogglesDefaultToMonstersAndCreepersOnly() {
        // A guard that attacked livestock on sight would be a surprise.
        NpcCombatProfile profile = new NpcCombatProfile();
        assertFalse(profile.guardAnimals);
        assertTrue(profile.guardMonsters);
        assertTrue(profile.guardCreepers);
        // And a profile written before the job existed loads with those, not with false/false.
        NpcCombatProfile old = NpcCombatProfile.fromTag(new CompoundTag());
        assertFalse(old.guardAnimals);
        assertTrue(old.guardMonsters);
        assertTrue(old.guardCreepers);
    }

    @Test
    void theGuardTogglesSurviveASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.guardAnimals = true;
        profile.guardMonsters = false;
        profile.guardCreepers = false;

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertTrue(read.guardAnimals);
        assertFalse(read.guardMonsters);
        assertFalse(read.guardCreepers);
    }
}
