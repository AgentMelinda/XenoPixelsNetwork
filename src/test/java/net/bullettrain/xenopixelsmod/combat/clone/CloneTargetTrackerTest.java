package net.bullettrain.xenopixelsmod.combat.clone;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CloneTargetTrackerTest {

    @Test
    void dropsADeadLockAndRefusesADeadId() {
        CloneTargetTracker<String> tracker = new CloneTargetTracker<>();
        UUID owner = UUID.randomUUID();
        Map<Integer, String> world = new HashMap<>();
        world.put(7, "corpse");

        tracker.accept(owner, 7, 10L, world::get, "alive"::equals);
        assertNull(tracker.target(owner, 10L, "alive"::equals));

        // Same-tick heartbeat of the same dead id must not revive the corpse.
        tracker.accept(owner, 7, 10L, world::get, "alive"::equals);
        assertNull(tracker.target(owner, 10L, "alive"::equals));
    }

    @Test
    void keepsALivingLockAndClearsItOnALaterDeadId() {
        CloneTargetTracker<String> tracker = new CloneTargetTracker<>();
        UUID owner = UUID.randomUUID();
        Map<Integer, String> world = new HashMap<>();
        world.put(3, "alive");
        world.put(4, "corpse");

        tracker.accept(owner, 3, 10L, world::get, "alive"::equals);
        assertEquals("alive", tracker.target(owner, 10L, "alive"::equals));

        tracker.accept(owner, 4, 11L, world::get, "alive"::equals);
        assertNull(tracker.target(owner, 11L, "alive"::equals));
    }

    @Test
    void targetDropsWhenTheCurrentLockIsNoLongerAlive() {
        CloneTargetTracker<String> tracker = new CloneTargetTracker<>();
        UUID owner = UUID.randomUUID();
        Map<Integer, String> world = new HashMap<>();
        world.put(1, "alive");
        tracker.accept(owner, 1, 5L, world::get, "alive"::equals);
        assertEquals("alive", tracker.target(owner, 5L, "alive"::equals));
        assertNull(tracker.target(owner, 6L, s -> false));
    }
}
