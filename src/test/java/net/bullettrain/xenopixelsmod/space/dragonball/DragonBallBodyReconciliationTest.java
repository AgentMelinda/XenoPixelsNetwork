package net.bullettrain.xenopixelsmod.space.dragonball;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DragonBallBodyReconciliationTest {
    private static final UUID SAVED = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test void loadedSavedBodyIsReused() {
        var decision = DragonBallBodyReconciliation.decide(SAVED, List.of(SAVED));
        assertEquals(DragonBallBodyReconciliation.Action.EXISTING, decision.action());
        assertEquals(SAVED, decision.bodyId());
    }

    @Test void uniqueTaggedBodyWithoutLedgerIsAdopted() {
        var decision = DragonBallBodyReconciliation.decide(null, List.of(SAVED));
        assertEquals(DragonBallBodyReconciliation.Action.ADOPT, decision.action());
        assertEquals(SAVED, decision.bodyId());
    }

    @Test void missingSavedBodyWaitsInsteadOfCreatingADuplicate() {
        var decision = DragonBallBodyReconciliation.decide(SAVED, List.of());
        assertEquals(DragonBallBodyReconciliation.Action.WAIT_FOR_SABLE, decision.action());
        assertEquals(SAVED, decision.bodyId());
    }

    @Test void creationIsAllowedOnlyWhenNeitherLedgerNorTaggedBodyExists() {
        var decision = DragonBallBodyReconciliation.decide(null, List.of());
        assertEquals(DragonBallBodyReconciliation.Action.CREATE, decision.action());
        assertNull(decision.bodyId());
    }

    @Test void conflictingOrMultipleTaggedBodiesFailClosed() {
        assertEquals(DragonBallBodyReconciliation.Action.CONFLICT,
                DragonBallBodyReconciliation.decide(SAVED, List.of(OTHER)).action());
        assertEquals(DragonBallBodyReconciliation.Action.CONFLICT,
                DragonBallBodyReconciliation.decide(null, List.of(SAVED, OTHER)).action());
        assertEquals(DragonBallBodyReconciliation.Action.CONFLICT,
                DragonBallBodyReconciliation.decide(SAVED, null).action());
        assertEquals(DragonBallBodyReconciliation.Action.CONFLICT,
                DragonBallBodyReconciliation.decide(null, java.util.Arrays.asList((UUID) null)).action());
    }
}
