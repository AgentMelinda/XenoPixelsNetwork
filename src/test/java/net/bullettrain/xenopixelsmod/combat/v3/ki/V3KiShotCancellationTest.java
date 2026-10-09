package net.bullettrain.xenopixelsmod.combat.v3.ki;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class V3KiShotCancellationTest {
    private record Pending(String owner, String cast, int ordinal) {}

    @Test void damageCallbackCanCancelCurrentCastAndItsDelayedVolleyShots() {
        var active = new Pending("caster", "first", 0);
        var delayed = new Pending("caster", "first", 1);
        var other = new Pending("caster", "second", 0);
        var pending = new ArrayList<>(List.of(active, delayed, other));
        var stepped = new ArrayList<Pending>();
        assertDoesNotThrow(() -> V3KiShots.tickPending(pending, shot -> {
            stepped.add(shot);
            if (shot == active) pending.removeIf(candidate -> candidate.cast().equals("first"));
            return false;
        }));
        assertEquals(List.of(active, other), stepped);
        assertEquals(List.of(other), pending);
    }

    @Test void damageCallbackCanClearAllWorkWithoutSteppingCanceledEntries() {
        var pending = new ArrayList<>(List.of(1, 2, 3));
        var stepped = new ArrayList<Integer>();
        assertDoesNotThrow(() -> V3KiShots.tickPending(pending, shot -> {
            stepped.add(shot);
            pending.clear();
            return true;
        }));
        assertEquals(List.of(1), stepped);
        assertTrue(pending.isEmpty());
    }

    @Test void callbackQueuedShotsStartOnTheNextTickAndFinishedShotsLeaveOnce() {
        var pending = new ArrayList<>(List.of(1, 2));
        var stepped = new ArrayList<Integer>();
        V3KiShots.tickPending(pending, shot -> {
            stepped.add(shot);
            if (shot == 1) pending.add(3);
            return true;
        });
        assertEquals(List.of(1, 2), stepped);
        assertEquals(List.of(3), pending);
        V3KiShots.tickPending(pending, shot -> {
            stepped.add(shot);
            return true;
        });
        assertEquals(List.of(1, 2, 3), stepped);
        assertTrue(pending.isEmpty());
    }

    @Test void cancelOwnerPreservesAnotherCastersPendingWork() {
        var first = new Pending("first", "cast", 0);
        var delayed = new Pending("first", "cast", 1);
        var second = new Pending("second", "cast", 0);
        var pending = new ArrayList<>(List.of(first, delayed, second));
        var stepped = new ArrayList<Pending>();
        V3KiShots.tickPending(pending, shot -> {
            stepped.add(shot);
            if (shot == first) pending.removeIf(candidate -> candidate.owner().equals("first"));
            return false;
        });
        assertEquals(List.of(first, second), stepped);
        assertEquals(List.of(second), pending);
    }
}
