package net.bullettrain.xenopixelsmod.space.dragonball;

import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallBodyScan.BodyRead;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallBodyScan.Read;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tag-reading rule, asserted without a server: {@code classify}, {@code attribute} and {@code fold} are what
 * decide whether a loaded body is a claim, damage, or nothing at all, and the adapter only feeds them primitives.
 */
class DragonBallBodyScanTest {
    private static final int SLOTS = 7;
    private static final int VERSION = DragonBallIdentity.VERSION;

    @Test void aTagNamingAUsableSlotClaimsThatSlot() {
        for (int slot = 1; slot <= SLOTS; slot++)
            assertEquals(Read.CLAIM, DragonBallBodyScan.classify(slot, VERSION, SLOTS), "slot " + slot);
    }

    @Test void aBindingWithoutASlotIsDamageRatherThanAbsence() {
        assertEquals(Read.UNATTRIBUTABLE, DragonBallBodyScan.classify(null, VERSION, SLOTS),
                "the adapter only calls this once the SDB tag key is present, so a slot-less tag is corrupt");
    }

    @Test void aSlotOutsideTheLedgerCannotIndexTheScan() {
        assertEquals(Read.UNATTRIBUTABLE, DragonBallBodyScan.classify(0, VERSION, SLOTS));
        assertEquals(Read.UNATTRIBUTABLE, DragonBallBodyScan.classify(SLOTS + 1, VERSION, SLOTS));
        assertEquals(Read.UNATTRIBUTABLE, DragonBallBodyScan.classify(-3, VERSION, SLOTS));
    }

    @Test void aBindingWrittenByAnotherIdentityVersionIsNotTrusted() {
        assertEquals(Read.UNATTRIBUTABLE, DragonBallBodyScan.classify(1, 0, SLOTS), "version key missing");
        assertEquals(Read.UNATTRIBUTABLE, DragonBallBodyScan.classify(1, VERSION + 1, SLOTS), "written ahead");
        assertEquals(Read.UNATTRIBUTABLE, DragonBallBodyScan.classify(1, VERSION - 1, SLOTS), "written before");
    }

    @Test void aOneSlotLedgerAcceptsOnlySlotOne() {
        assertEquals(Read.CLAIM, DragonBallBodyScan.classify(1, VERSION, 1));
        assertEquals(Read.UNATTRIBUTABLE, DragonBallBodyScan.classify(2, VERSION, 1));
    }

    @Test void anUnavailableLevelOrLedgerSizeYieldsNoScanRatherThanAnEmptyOne() {
        assertNull(DragonBallBodyScan.tagged(null, SLOTS),
                "an absent level is SCAN_UNAVAILABLE; an empty map would authorize seven duplicate planets");
        assertNull(DragonBallBodyScan.tagged(null, 0));
    }

    /**
     * Codex's 11:13 probe, reproduced: my first version collapsed a thrown or absent user-data read into the same
     * {@code null} as "this body is not ours", so a body I failed to open was reported as claiming nothing and the
     * planner could build a duplicate planet beside it. Absence of our tag is a fact about the body; absence of
     * the body or of its data is a failure of the scan.
     */
    @Test void aBodyThatCannotBeOpenedIsNotReadAsClaimingNothing() {
        assertEquals(BodyRead.UNREADABLE, DragonBallBodyScan.attribute(false, false, false),
                "a null entry in the listing is an unreadable body, not an empty world");
        assertEquals(BodyRead.UNREADABLE, DragonBallBodyScan.attribute(true, false, false),
                "a body with no user data cannot be asked what it claims");
        assertEquals(BodyRead.FOREIGN, DragonBallBodyScan.attribute(true, true, false),
                "opened and proven not ours by the absent binding tag key");
        assertEquals(BodyRead.OURS, DragonBallBodyScan.attribute(true, true, true));
    }

    @Test void aFoldedClaimLandsOnlyOnTheSlotItsTagNames() {
        Map<Integer, List<UUID>> claims = DragonBallBodyScan.freshSlotMap(SLOTS);
        var id = UUID.randomUUID();
        DragonBallBodyScan.fold(claims, 3, VERSION, SLOTS, id);
        assertEquals(List.of(id), claims.get(3));
        for (int slot = 1; slot <= SLOTS; slot++)
            if (slot != 3) assertEquals(List.of(), claims.get(slot), "slot " + slot + " was not touched");
    }

    @Test void aDamagedBindingConflictsTheSlotsItCouldHaveHeld() {
        var claims = DragonBallBodyScan.freshSlotMap(SLOTS);
        DragonBallBodyScan.fold(claims, 4, VERSION - 1, SLOTS, UUID.randomUUID());
        assertNull(claims.get(4), "a wrong-version tag naming slot 4 makes slot 4 ambiguous");
        for (int slot = 1; slot <= SLOTS; slot++)
            if (slot != 4) assertEquals(List.of(), claims.get(slot), "slot " + slot + " keeps its confirmed result");

        var suspect = DragonBallBodyScan.freshSlotMap(SLOTS);
        DragonBallBodyScan.fold(suspect, null, VERSION, SLOTS, UUID.randomUUID());
        for (int slot = 1; slot <= SLOTS; slot++)
            assertNull(suspect.get(slot), "a tag naming no slot could have named any of them");
    }

    /**
     * The defect I found while tracing Codex's report: {@code markUnattributable} replaces a slot's list with
     * {@code null}, so a valid claimant arriving later for that same slot hit {@code claims.get(slot).add(...)}
     * and threw. An uncaught throw here would propagate out of the scan into the server tick.
     */
    @Test void aClaimOnAnAmbiguousSlotStaysAmbiguousInsteadOfCrashing() {
        var claims = DragonBallBodyScan.freshSlotMap(SLOTS);
        DragonBallBodyScan.fold(claims, 2, VERSION - 1, SLOTS, UUID.randomUUID());
        DragonBallBodyScan.fold(claims, 2, VERSION, SLOTS, UUID.randomUUID());
        assertNull(claims.get(2), "a torn binding and a real claimant is the conflict null already reports");
        assertTrue(claims.get(1).isEmpty(), "the other slots are untouched by slot 2's ambiguity");
    }
}
