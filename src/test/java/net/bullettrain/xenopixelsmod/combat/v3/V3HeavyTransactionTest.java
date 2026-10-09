package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.v3.V3Heavy.Start;
import org.junit.jupiter.api.Test;

class V3HeavyTransactionTest {
    private static final UUID SESSION = UUID.randomUUID();

    private static final class Stamina implements V3Resources.Pool {
        float value;
        int writes;
        Stamina(float value) { this.value = value; }
        @Override public float get() { return value; }
        @Override public void set(float next) { value = next; writes++; }
    }

    @Test void unaffordableActionChangesNeitherPool() {
        var tx = new V3Heavy.Transaction();
        var attacker = new Stamina(9.99f);
        var victim = new Stamina(50);
        assertEquals(Start.UNAFFORDABLE, tx.start(SESSION, 1, true, attacker, 10));
        assertEquals(0f, tx.accept(SESSION, 1, 5f, victim, 10));
        assertEquals(9.99f, attacker.value);
        assertEquals(50f, victim.value);
        assertEquals(0, attacker.writes + victim.writes);
    }

    @Test void repeatedSequenceSpendsOnce() {
        var tx = new V3Heavy.Transaction();
        var attacker = new Stamina(30);
        assertEquals(Start.STARTED, tx.start(SESSION, 4, true, attacker, 10));
        assertEquals(Start.DUPLICATE, tx.start(SESSION, 4, true, attacker, 10));
        assertEquals(Start.DUPLICATE, tx.start(SESSION, 3, true, attacker, 10));
        assertEquals(20f, attacker.value);
        assertEquals(Start.STARTED, tx.start(UUID.randomUUID(), 0, true, attacker, 10));
        assertEquals(10f, attacker.value);
    }

    @Test void protectedTargetIsRefusedBeforeSpend() {
        var tx = new V3Heavy.Transaction();
        var attacker = new Stamina(30);
        assertEquals(Start.REFUSED, tx.start(SESSION, 1, false, attacker, 10));
        assertEquals(30f, attacker.value);
        // A refusal does not consume the sequence.
        assertEquals(Start.STARTED, tx.start(SESSION, 1, true, attacker, 10));
    }

    @Test void startedButMissedCostsOnceAndDoesNotDrainVictim() {
        var tx = new V3Heavy.Transaction();
        var attacker = new Stamina(30);
        var victim = new Stamina(50);
        tx.start(SESSION, 1, true, attacker, 10);
        assertEquals(0f, tx.accept(SESSION, 1, 0f, victim, 10));
        assertEquals(20f, attacker.value);
        assertEquals(50f, victim.value);
    }

    @Test void acceptedHitDrainsOnceAndClampsAtZero() {
        var tx = new V3Heavy.Transaction();
        var attacker = new Stamina(30);
        var victim = new Stamina(50);
        tx.start(SESSION, 1, true, attacker, 10);
        assertEquals(10f, tx.accept(SESSION, 1, 3.5f, victim, 10));
        assertEquals(0f, tx.accept(SESSION, 1, 3.5f, victim, 10));
        assertEquals(40f, victim.value);

        var tired = new Stamina(4);
        tx.start(SESSION, 2, true, attacker, 10);
        assertEquals(4f, tx.accept(SESSION, 2, 1f, tired, 10));
        assertEquals(0f, tired.value);
    }

    @Test void canceledOrForeignDamageDrainsZero() {
        var tx = new V3Heavy.Transaction();
        var attacker = new Stamina(30);
        var victim = new Stamina(50);
        tx.start(SESSION, 1, true, attacker, 10);
        assertEquals(0f, tx.accept(SESSION, 1, Float.NaN, victim, 10));
        assertEquals(0f, tx.accept(SESSION, 1, -2f, victim, 10));
        assertEquals(0f, tx.accept(SESSION, 2, 5f, victim, 10));
        assertEquals(0f, tx.accept(UUID.randomUUID(), 1, 5f, victim, 10));
        assertEquals(50f, victim.value);
    }

    @Test void zeroConfigValueAffectsOnlyItsOwnPool() {
        var free = new V3Heavy.Transaction();
        var attacker = new Stamina(0);
        var victim = new Stamina(50);
        assertEquals(Start.STARTED, free.start(SESSION, 1, true, attacker, 0));
        assertEquals(10f, free.accept(SESSION, 1, 2f, victim, 10));
        assertEquals(0f, attacker.value);
        assertEquals(40f, victim.value);

        var noDrain = new V3Heavy.Transaction();
        attacker = new Stamina(30);
        assertEquals(Start.STARTED, noDrain.start(SESSION, 1, true, attacker, 10));
        assertEquals(0f, noDrain.accept(SESSION, 1, 2f, victim, 0));
        assertEquals(20f, attacker.value);
        assertEquals(40f, victim.value);
    }

    @Test void reactionIsExplicitAndNeverAHighLaunch() {
        for (V3Direction direction : V3Direction.values()) {
            var push = V3Heavy.reaction(direction, 0, 0, 1);
            assertTrue(push.y <= 0.3, direction + " must not launch");
            assertTrue(Math.abs(push.horizontalDistance() - 0.9) < 1e-6 || direction == V3Direction.BACK);
        }
        assertTrue(V3Heavy.reaction(V3Direction.FORWARD, 0, 0, 1).z > 0);
        assertTrue(V3Heavy.reaction(V3Direction.BACK, 0, 0, 1).z < 0);
        // Facing +Z, the attacker's right is -X.
        assertTrue(V3Heavy.reaction(V3Direction.RIGHT, 0, 0, 1).x < 0);
        assertTrue(V3Heavy.reaction(V3Direction.LEFT, 0, 0, 1).x > 0);
        // A degenerate facing still produces a finite push.
        assertTrue(Double.isFinite(V3Heavy.reaction(V3Direction.FORWARD, 0, 0, 0).lengthSqr()));
    }
}
