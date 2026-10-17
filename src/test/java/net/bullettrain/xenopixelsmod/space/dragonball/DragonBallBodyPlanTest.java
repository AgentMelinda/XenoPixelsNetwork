package net.bullettrain.xenopixelsmod.space.dragonball;

import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallBodyReconciliation.Action;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DragonBallBodyPlanTest {
    private static final int SLOTS = 7;
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private static List<UUID> ledger(UUID... bySlot) {
        assertEquals(SLOTS, bySlot.length, "test fixture must fill every slot");
        return Arrays.asList(bySlot);
    }

    @Test void aFreshWorldPlansOneCreatePerSlot() {
        var plan = DragonBallBodyPlan.plan(ledger(null, null, null, null, null, null, null), Map.of());
        assertEquals(DragonBallBodyPlan.Overall.PROCEED, plan.overall());
        assertEquals(SLOTS, plan.entries().size());
        for (int slot = 1; slot <= SLOTS; slot++) {
            assertEquals(Action.CREATE, plan.entry(slot).action(), "slot " + slot);
            assertNull(plan.entry(slot).bodyId());
        }
        assertFalse(plan.createsNothing());
    }

    @Test void onlyPhysicallySupportedAssignmentsRemainFreshCreateIntents() {
        var plan = DragonBallBodyPlan.plan(ledger(null, null, null, null, null, null, null), Map.of());
        var admitted = DragonBallBodyPlan.deferUnassignedCreates(plan, java.util.Set.of(1, 2, 3, 4, 5, 7));
        for (int slot = 1; slot <= SLOTS; slot++) {
            assertEquals(slot == 6 ? Action.DEFERRED_NO_ASSIGNMENT : Action.CREATE,
                    admitted.entry(slot).action(), "slot " + slot);
        }
        assertFalse(admitted.createsNothing(), "supported slots remain eligible to be created");
    }

    @Test void assignmentRemovalDoesNotOrphanAnExistingOrSavedSableBody() {
        var saved = ledger(A, null, null, null, null, null, null);
        var loaded = Map.of(2, List.of(B));
        var plan = DragonBallBodyPlan.plan(saved, loaded);
        var admitted = DragonBallBodyPlan.deferUnassignedCreates(plan, java.util.Set.of());
        assertEquals(Action.WAIT_FOR_SABLE, admitted.entry(1).action());
        assertEquals(Action.ADOPT, admitted.entry(2).action());
        assertEquals(Action.DEFERRED_NO_ASSIGNMENT, admitted.entry(3).action());
        assertEquals(Action.DEFERRED_NO_ASSIGNMENT, admitted.entry(4).action());
    }

    @Test void aCleanRestartPlansExistingForEveryBoundSlot() {
        var saved = ledger(A, B, C, null, null, null, null);
        var loaded = Map.of(1, List.of(A), 2, List.of(B), 3, List.of(C));
        var plan = DragonBallBodyPlan.plan(saved, loaded);
        assertEquals(DragonBallBodyPlan.Overall.PROCEED, plan.overall());
        assertEquals(Action.EXISTING, plan.entry(1).action());
        assertEquals(Action.EXISTING, plan.entry(3).action());
        assertEquals(Action.CREATE, plan.entry(4).action());
        assertEquals(A, plan.entry(1).bodyId());
    }

    @Test void anUnclaimedTaggedBodyIsAdoptedAndMarkedForBinding() {
        var plan = DragonBallBodyPlan.plan(ledger(null, null, null, null, null, null, null), Map.of(5, List.of(B)));
        assertEquals(DragonBallBodyPlan.Overall.PROCEED, plan.overall());
        assertEquals(Action.ADOPT, plan.entry(5).action());
        assertEquals(B, plan.entry(5).bodyId(), "the manager must bind the scanned UUID into the ledger");
        assertTrue(plan.entries().stream().filter(e -> e.action() == Action.ADOPT).findAny().isPresent());
    }

    @Test void aSavedBodyThatIsNotYetLoadedWaitsInsteadOfBeingRebuilt() {
        var plan = DragonBallBodyPlan.plan(ledger(A, null, null, null, null, null, null), Map.of());
        assertEquals(DragonBallBodyPlan.Overall.PROCEED, plan.overall());
        assertEquals(Action.WAIT_FOR_SABLE, plan.entry(1).action());
        assertEquals(A, plan.entry(1).bodyId(), "wait must keep pointing at the saved incarnation, not a new one");
    }

    @Test void oneLedgerBodyBoundToTwoSlotsBlocksTheWholeCycle() {
        var plan = DragonBallBodyPlan.plan(ledger(null, B, B, null, null, null, null), Map.of());
        assertEquals(DragonBallBodyPlan.Overall.DUPLICATE_LEDGER, plan.overall());
        assertTrue(plan.entries().isEmpty(), "a blocked cycle must expose no actionable slot");
        assertTrue(plan.reason().contains("slots 2 and 3"), plan.reason());
        assertTrue(plan.createsNothing());
    }

    @Test void oneLoadedBodyClaimingTwoSlotsIsNotAdoptedIntoEither() {
        // Per-slot decide() would answer ADOPT for both slots; binding both is what reaches bindPlanetBody's
        // duplicate rejection, so the whole-set check must stop the cycle first.
        var plan = DragonBallBodyPlan.plan(ledger(null, null, null, null, null, null, null),
                Map.of(2, List.of(C), 3, List.of(C)));
        assertEquals(DragonBallBodyPlan.Overall.DUPLICATE_LOADED_BODY, plan.overall());
        assertTrue(plan.entries().isEmpty());
        assertTrue(plan.reason().contains("slots 2 and 3"), plan.reason());
        assertTrue(plan.createsNothing());
    }

    @Test void anUnavailableScanIsReportedAsConflictRatherThanAbsence() {
        var plan = DragonBallBodyPlan.plan(ledger(A, null, null, null, null, null, null), null);
        assertEquals(DragonBallBodyPlan.Overall.SCAN_UNAVAILABLE, plan.overall());
        assertEquals(SLOTS, plan.entries().size(), "each slot is still described so the caller can log it");
        for (int slot = 1; slot <= SLOTS; slot++) assertEquals(Action.CONFLICT, plan.entry(slot).action());
        assertTrue(plan.createsNothing());
    }

    @Test void aDisagreeingSlotConflictsWithoutBlockingTheOtherSlots() {
        var saved = ledger(A, B, null, null, null, null, null);
        var loaded = Map.of(1, List.of(B), 4, List.of(C));
        var plan = DragonBallBodyPlan.plan(saved, loaded);
        assertEquals(DragonBallBodyPlan.Overall.PROCEED, plan.overall());
        assertEquals(Action.CONFLICT, plan.entry(1).action(), "saved A but loaded B for the same slot");
        assertEquals(Action.ADOPT, plan.entry(4).action(), "an unrelated clean slot must still advance");
        assertEquals(Action.CREATE, plan.entry(7).action());
    }

    @Test void aScanKeyOutsideTheLedgerSlotRangeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DragonBallBodyPlan.plan(ledger(null, null, null, null, null, null, null), Map.of(8, List.of(A))));
        assertThrows(IllegalArgumentException.class, () -> DragonBallBodyPlan.plan(List.of(), Map.of()));
    }

    @Test void moreThanOneLoadedBodyForOneSlotConflictsWithoutPickingOneArbitrarily() {
        var plan = DragonBallBodyPlan.plan(ledger(null, null, null, null, null, null, null),
                Map.of(1, List.of(A, B, C)));
        assertEquals(DragonBallBodyPlan.Overall.PROCEED, plan.overall());
        assertEquals(Action.CONFLICT, plan.entry(1).action(), "ambiguous tag must not pick one at random");
    }

    /**
     * Codex's 10:07/10:09 review, reproduced by probe as overall=PROCEED slot1=CREATE on the previous code.
     * Map.of cannot hold a null value, so these fixtures use HashMap.
     */
    @Test void apresentNullScanValueConflictsForThatSlotInsteadOfCreating() {
        var unscanned = new HashMap<Integer, List<UUID>>();
        unscanned.put(1, null);
        var plan = DragonBallBodyPlan.plan(ledger(null, null, null, null, null, null, null), unscanned);
        assertEquals(DragonBallBodyPlan.Overall.PROCEED, plan.overall(), "one unknown slot does not block the rest");
        assertEquals(Action.CONFLICT, plan.entry(1).action());
        assertNull(plan.entry(1).bodyId());
        assertEquals(Action.CREATE, plan.entry(2).action(),
                "an absent key still means a confirmed empty scan, so only the unknown slot is held");
    }

    @Test void anUnscannedSlotWithASavedBodyNeitherAdoptsNorCreates() {
        var unscanned = new HashMap<Integer, List<UUID>>();
        unscanned.put(1, null);
        var plan = DragonBallBodyPlan.plan(ledger(A, null, null, null, null, null, null), unscanned);
        assertEquals(Action.CONFLICT, plan.entry(1).action(),
                "a saved body with an unknown scan must not fall forward to CREATE");
        assertEquals(A, plan.entry(1).bodyId(), "the saved identity is reported so the caller can log what is pending");
        assertFalse(plan.createsNothing(), "slot 2 is still free to create; only slot 1 is held");
    }

    @Test void aPartialScanDoesNotEstablishADuplicateClaim() {
        var unscanned = new HashMap<Integer, List<UUID>>();
        unscanned.put(1, null);
        unscanned.put(2, List.of(A));
        var plan = DragonBallBodyPlan.plan(ledger(null, null, null, null, null, null, null), unscanned);
        assertEquals(DragonBallBodyPlan.Overall.PROCEED, plan.overall());
        assertEquals(Action.CONFLICT, plan.entry(1).action());
        assertEquals(Action.ADOPT, plan.entry(2).action());
    }
}
