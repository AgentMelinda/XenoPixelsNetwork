package net.bullettrain.xenopixelsmod.space.dragonball;

import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetScheduler.Phase;
import net.bullettrain.xenopixelsmod.space.physics.SpaceActivationBudget;
import net.bullettrain.xenopixelsmod.space.physics.SpaceActivationBudget.Owner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cadence and create-intent policy for the seven planet-form Super Dragon Balls.
 *
 * <p>Cadence mechanics are asserted on a one-slot scheduler: with all seven slots carrying work, some slot is
 * always ready, which would mask a park. Ordering and neighbour isolation are asserted on the seven-slot world.
 */
class DragonBallPlanetSchedulerTest {
    /**
     * {@code CosmicDragonBalls.COUNT} is 7 (CosmicDragonBalls.java:47), but that class imports 21
     * Minecraft/Sable/NeoForge types, so the production constant cannot be referenced from a Gradle-free test.
     */
    private static final int SLOTS = 7;
    private static final int SCAN = 40;

    private static DragonBallBodyPlan.Plan fresh(int slots) {
        return DragonBallBodyPlan.plan(new ArrayList<>(Collections.nCopies(slots, null)), Map.of());
    }

    private static DragonBallBodyPlan.Plan savedAt(int slot, UUID savedBody, Map<Integer, List<UUID>> loaded) {
        var saved = new ArrayList<UUID>();
        for (int i = 1; i <= SLOTS; i++) saved.add(i == slot ? savedBody : null);
        return DragonBallBodyPlan.plan(saved, loaded);
    }

    /** The ledger the plan rejects outright: one Sable body bound to both slot 1 and slot 2. */
    private static DragonBallBodyPlan.Plan duplicateLedger() {
        var shared = UUID.randomUUID();
        var saved = new ArrayList<UUID>();
        saved.add(shared);
        saved.add(shared);
        for (int i = 3; i <= SLOTS; i++) saved.add(null);
        return DragonBallBodyPlan.plan(saved, Map.of());
    }

    @Test void aFreshWorldQueuesEveryCreateSlot() {
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(fresh(SLOTS), 100);
        assertEquals(SLOTS, scheduler.queuedCreates());
        assertEquals(0, scheduler.waitingForLoad());
    }

    @Test void aSlotWithoutValidatedAssignmentDoesNotQueueABuild() {
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        var admitted = DragonBallBodyPlan.deferUnassignedCreates(fresh(SLOTS), java.util.Set.of(1, 2, 3, 4, 5, 7));
        scheduler.reconcile(admitted, 100);
        assertEquals(6, scheduler.queuedCreates());
        assertEquals(Phase.IDLE, scheduler.phase(6));
        for (int slot : new int[]{1, 2, 3, 4, 5, 7}) {
            assertEquals(Integer.valueOf(slot), scheduler.takeCreateAttempt(100));
            scheduler.onBuildSucceeded(100);
        }
        assertNull(scheduler.takeCreateAttempt(100));
    }

    @Test void onlyOneBuildAttemptIsInFlightAtATime() {
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(fresh(SLOTS), 100);
        assertEquals(Integer.valueOf(1), scheduler.takeCreateAttempt(100));
        assertEquals(Integer.valueOf(1), scheduler.inFlightSlot());
        assertNull(scheduler.takeCreateAttempt(100), "a second body must not start while one is building");
        scheduler.onBuildSucceeded(100);
        assertEquals(Integer.valueOf(2), scheduler.takeCreateAttempt(100));
    }

    @Test void successesWalkThroughEveryReadySlotInOrder() {
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(fresh(SLOTS), 100);
        for (int slot = 1; slot <= SLOTS; slot++) {
            assertEquals(Integer.valueOf(slot), scheduler.takeCreateAttempt(100));
            scheduler.onBuildSucceeded(100);
        }
        assertNull(scheduler.takeCreateAttempt(100));
        assertEquals(0, scheduler.queuedCreates());
    }

    @Test void aDeniedActivationReAsksAtTheGateCadenceNotTheScanCadence() {
        var solo = new DragonBallPlanetScheduler(1, SCAN);
        solo.reconcile(fresh(1), 100);
        assertEquals(Integer.valueOf(1), solo.takeCreateAttempt(100));
        solo.onGateDenied(100);
        assertEquals(Phase.QUEUED, solo.phase(1));
        assertEquals(110, solo.nextActionTick(1), "SB-1.6: a 40-tick park would starve SDB of activations");
        assertNull(solo.takeCreateAttempt(105), "the parked slot must not be handed out early");
        assertEquals(Integer.valueOf(1), solo.takeCreateAttempt(110));
    }

    @Test void theWakeDeadlineFollowsTheDenialParkAndRescansDoNotResetIt() {
        var solo = new DragonBallPlanetScheduler(1, SCAN);
        solo.reconcile(fresh(1), 100);
        assertEquals(100, solo.nextWakeTick(60), "a deadline in the past means wake now, not schedule backwards");
        assertEquals(Integer.valueOf(1), solo.takeCreateAttempt(100));
        solo.onGateDenied(100);
        assertEquals(110, solo.nextWakeTick(100), "a parked slot holds the whole pass until its retry");
        solo.reconcile(fresh(1), 101);
        assertEquals(110, solo.nextActionTick(1), "a rescan must not reset a pending backoff");
    }

    @Test void aFailedBuildRetriesAndKeepsCountingItsFailures() {
        var solo = new DragonBallPlanetScheduler(1, SCAN);
        solo.reconcile(fresh(1), 100);
        for (int attempt = 0; attempt < 3; attempt++) {
            long tick = 100 + attempt * 10L;
            assertEquals(Integer.valueOf(1), solo.takeCreateAttempt(tick));
            solo.onBuildFailed(tick);
            assertEquals(attempt + 1, solo.buildFailures(1));
            assertEquals(tick + 10, solo.nextActionTick(1));
        }
        assertEquals(Integer.valueOf(1), solo.takeCreateAttempt(130));
        solo.onBuildSucceeded(130);
        assertEquals(0, solo.buildFailures(1), "a success clears the failure streak");
    }

    /**
     * Codex's 10:30 probe, reproduced: all seven slots queued and every build failing. A lowest-slot-first scan
     * returned slot 1 on all 21 attempts, so one permanently broken ball retried itself forever and the other
     * six never got a body.
     */
    @Test void aPersistentlyFailingSlotDoesNotStarveTheOtherSix() {
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(fresh(SLOTS), 100);
        var order = new ArrayList<Integer>();
        for (int attempt = 0; attempt < SLOTS; attempt++) {
            long tick = 100 + attempt * 10L;
            Integer slot = scheduler.takeCreateAttempt(tick);
            assertNotNull(slot, "attempt " + attempt + " found no eligible slot while seven were queued");
            order.add(slot);
            scheduler.onBuildFailed(tick);
        }
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), order, "failures must rotate, not repeat the lowest slot");
        for (int slot = 1; slot <= SLOTS; slot++) assertEquals(1, scheduler.buildFailures(slot));
        assertEquals(SLOTS, scheduler.queuedCreates(), "a failure keeps the work queued rather than dropping it");
    }

    @Test void aFailingSlotIsRetriedOnlyAfterAFullRotation() {
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(fresh(SLOTS), 100);
        for (int attempt = 0; attempt < SLOTS; attempt++) {
            long tick = 100 + attempt * 10L;
            assertEquals(Integer.valueOf(attempt + 1), scheduler.takeCreateAttempt(tick));
            scheduler.onBuildFailed(tick);
        }
        // The cursor is now at slot 7, so the second round starts again at slot 1, which has parked since 110.
        assertEquals(Integer.valueOf(1), scheduler.takeCreateAttempt(170));
        scheduler.onBuildFailed(170);
        assertEquals(2, scheduler.buildFailures(1), "the slot is retried, and its streak keeps counting");
        assertEquals(180, scheduler.nextActionTick(1));
        assertEquals(Integer.valueOf(2), scheduler.takeCreateAttempt(180), "and the next round goes to its neighbour");
    }

    @Test void aWaitingSlotNeverEscalatesIntoACreateOnATimer() {
        var body = UUID.randomUUID();
        var solo = new DragonBallPlanetScheduler(1, SCAN);
        var saved = new ArrayList<UUID>();
        saved.add(body);
        for (int pass = 0; pass < 50; pass++) {
            long tick = 100 + (long) pass * SCAN;
            solo.reconcile(DragonBallBodyPlan.plan(saved, Map.of()), tick);
            assertNull(solo.takeCreateAttempt(tick),
                    "Sable may still restore the saved body; a second body would strand the first");
            assertEquals(Phase.WAITING_FOR_LOAD, solo.phase(1));
            assertEquals(tick + SCAN, solo.nextActionTick(1));
        }
        assertEquals(0, solo.queuedCreates(), "fifty passes turned the wait into zero creates");

        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(savedAt(3, body, Map.of()), 100);
        assertEquals(Phase.WAITING_FOR_LOAD, scheduler.phase(3));
        assertEquals(SLOTS - 1, scheduler.queuedCreates(), "the other six slots keep their work");
    }

    @Test void anExistingOrAdoptedBodyClearsTheSlot() {
        var body = UUID.randomUUID();
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(savedAt(2, body, Map.of(2, List.of(body))), 100);
        assertEquals(Phase.IDLE, scheduler.phase(2));
        assertEquals(0, scheduler.buildFailures(2));
        assertEquals(SLOTS - 1, scheduler.queuedCreates(), "the unbodied slots still queue");

        scheduler.reconcile(savedAt(4, null, Map.of(4, List.of(body))), 110);
        assertEquals(Phase.IDLE, scheduler.phase(4), "an adopt is a ledger bind, not a build, so it never queues");
    }

    @Test void anInFlightBuildSurvivesAContradictingRescan() {
        var body = UUID.randomUUID();
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(fresh(SLOTS), 100);
        assertEquals(Integer.valueOf(1), scheduler.takeCreateAttempt(100));
        scheduler.reconcile(savedAt(1, body, Map.of(1, List.of(body))), 100);
        assertEquals(Phase.BUILDING, scheduler.phase(1),
                "the manager owns the running job; a stale plan must not orphan it");
        scheduler.onBuildSucceeded(100);
        scheduler.reconcile(savedAt(1, body, Map.of(1, List.of(body))), 110);
        assertEquals(Phase.IDLE, scheduler.phase(1));
    }

    @Test void aBlockedCycleAuthorizesNothingButDoesNotAbandonTheQueue() {
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(duplicateLedger(), 100);
        assertEquals(0, scheduler.queuedCreates());
        assertNull(scheduler.takeCreateAttempt(100));

        scheduler.reconcile(fresh(SLOTS), 100);
        assertEquals(SLOTS, scheduler.queuedCreates());
        scheduler.reconcile(duplicateLedger(), 120);
        assertNull(scheduler.takeCreateAttempt(120), "a duplicate-ledger cycle must not release work");
        assertEquals(160, scheduler.nextActionTick(1), "pending intents come back at the scan cadence");
        assertEquals(SLOTS, scheduler.queuedCreates());
    }

    @Test void anUnavailableScanQueuesNothingAndKeepsPolling() {
        var saved = new ArrayList<UUID>();
        saved.add(UUID.randomUUID());
        for (int i = 2; i <= SLOTS; i++) saved.add(null);
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(DragonBallBodyPlan.plan(saved, null), 100);
        assertEquals(Phase.IDLE, scheduler.phase(1), "a conflict authorizes no create");
        assertEquals(0, scheduler.queuedCreates(), "SCAN_UNAVAILABLE carries no information, so nothing queues");
        assertEquals(140, scheduler.nextWakeTick(100), "the scan cap keeps the manager polling");
    }

    @Test void aConflictOnOneSlotDoesNotBlockItsNeighbours() {
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        scheduler.reconcile(savedAt(1, UUID.randomUUID(), Map.of(1, List.of(UUID.randomUUID()))), 100);
        assertEquals(Phase.IDLE, scheduler.phase(1), "saved and tagged disagree, so slot 1 creates nothing");
        assertEquals(Integer.valueOf(2), scheduler.takeCreateAttempt(100), "slot 2 is unaffected and proceeds");
    }

    @Test void deadlinesSaturateInsteadOfOverflowingAtTheTickLimit() {
        long nearLimit = Long.MAX_VALUE - 5;
        var solo = new DragonBallPlanetScheduler(1, SCAN);
        solo.reconcile(fresh(1), nearLimit);
        assertEquals(Integer.valueOf(1), solo.takeCreateAttempt(nearLimit));
        solo.onGateDenied(nearLimit);
        assertEquals(Long.MAX_VALUE, solo.nextActionTick(1),
                "a wrapped deadline would fire every tick instead of parking");
        assertEquals(Long.MAX_VALUE, solo.nextWakeTick(nearLimit));
        assertNull(solo.takeCreateAttempt(nearLimit));
    }

    /**
     * Codex's 10:25 probe, reproduced: a conflict arriving on a slot that is already waiting kept the phase but
     * pinned the deadline to the current tick, so every pass woke immediately for the rest of the run.
     */
    @Test void aConflictAfterAWaitDoesNotMakeEveryPassDueImmediately() {
        var body = UUID.randomUUID();
        var saved = new ArrayList<UUID>();
        saved.add(body);
        var solo = new DragonBallPlanetScheduler(1, SCAN);
        solo.reconcile(DragonBallBodyPlan.plan(saved, Map.of()), 100);
        assertEquals(Phase.WAITING_FOR_LOAD, solo.phase(1));
        assertEquals(140, solo.nextActionTick(1));
        assertEquals(140, solo.nextWakeTick(100));

        var unscanned = new HashMap<Integer, List<UUID>>();
        unscanned.put(1, null);
        solo.reconcile(DragonBallBodyPlan.plan(saved, unscanned), 120);
        assertEquals(Phase.WAITING_FOR_LOAD, solo.phase(1));
        assertEquals(160, solo.nextActionTick(1), "a conflict must retry at the scan cadence, not every tick");
        assertNull(solo.takeCreateAttempt(120), "and it must still authorize no create");
        assertEquals(160, solo.nextWakeTick(120));
    }

    @Test void anInFlightBuildDoesNotForceAReconcileEveryTick() {
        var solo = new DragonBallPlanetScheduler(1, SCAN);
        solo.reconcile(fresh(1), 100);
        assertEquals(Integer.valueOf(1), solo.takeCreateAttempt(100));
        assertEquals(140, solo.nextWakeTick(100),
                "the manager steps the running job itself; an in-flight slot is not a reconcile deadline");
        solo.onBuildSucceeded(140);
        assertEquals(Phase.IDLE, solo.phase(1));
        assertEquals(180, solo.nextWakeTick(140));
    }

    @Test void badArgumentsAndUnmatchedOutcomesAreRejected() {
        var scheduler = new DragonBallPlanetScheduler(SLOTS, SCAN);
        assertThrows(IllegalArgumentException.class, () -> scheduler.phase(0));
        assertThrows(IllegalArgumentException.class, () -> scheduler.phase(SLOTS + 1));
        assertThrows(IllegalArgumentException.class, () -> new DragonBallPlanetScheduler(0, SCAN));
        assertThrows(IllegalArgumentException.class, () -> new DragonBallPlanetScheduler(SLOTS, 0));
        assertThrows(IllegalArgumentException.class, () -> scheduler.reconcile(null, 100));
        assertThrows(IllegalStateException.class, () -> scheduler.onGateDenied(100));
        assertThrows(IllegalStateException.class, () -> scheduler.onBuildFailed(100));
        assertThrows(IllegalStateException.class, () -> scheduler.onBuildSucceeded(100));
    }

    /**
     * SB-1.6 re-run against the real four-owner gate instead of my earlier three-owner synthetic probe: does
     * parking a denied SDB attempt at the gate cadence earn activations that the scan cadence would not?
     */
    @Test void theDenialParkKeepsSuperDragonBallsInTheRealGateRotation() {
        int horizon = 2000;
        int denialParked = sdbGrants(horizon, true);
        int scanCadenceOnly = sdbGrants(horizon, false);
        assertTrue(denialParked > 0,
                "denial-parked SDB got zero grants over " + horizon + " ticks, so the policy is broken");
        assertTrue(denialParked > scanCadenceOnly,
                "denial parking did not beat the scan cadence: " + denialParked + " vs " + scanCadenceOnly);
    }

    private static int sdbGrants(int horizon, boolean parkOnDenial) {
        var budget = new SpaceActivationBudget();
        int grants = 0;
        long sdbNext = 0;
        for (long tick = 0; tick < horizon; tick++) {
            if (tick % SpaceActivationBudget.MIN_INTERVAL_TICKS == 0) {
                budget.tryAcquire(Owner.CHART_PLANET, tick);
                budget.tryAcquire(Owner.ASTEROID, tick);
                budget.tryAcquire(Owner.SMALL_PLANET, tick);
            }
            if (tick >= sdbNext) {
                boolean granted = budget.tryAcquire(Owner.SUPER_DRAGON_BALL, tick);
                if (granted) grants++;
                sdbNext = parkOnDenial
                        ? SpaceActivationBudget.nextAttemptTick(tick, SCAN, !granted)
                        : SpaceActivationBudget.tickAfter(tick, SCAN);
            }
        }
        return grants;
    }
}
