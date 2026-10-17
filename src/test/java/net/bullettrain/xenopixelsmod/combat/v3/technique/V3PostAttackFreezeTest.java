package net.bullettrain.xenopixelsmod.combat.v3.technique;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class V3PostAttackFreezeTest {
    @Test void randomCastMayTransferOnlyItsOwnDashActionHold(){
        var a=java.util.UUID.randomUUID();var b=java.util.UUID.randomUUID();
        var dash=net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.DASH;
        assertTrue(V3PostAttackFreeze.transferable(a,a,dash,false));
        assertFalse(V3PostAttackFreeze.transferable(a,b,dash,false));
        assertFalse(V3PostAttackFreeze.transferable(a,a,dash,true));
        assertFalse(V3PostAttackFreeze.transferable(a,a,net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.RUSH,false));
        assertFalse(V3PostAttackFreeze.transferable(null,a,dash,false));
    }
    @Test void dashRecoveryAllowsKnockbackWhileRushAndFinisherStillPin() {
        assertFalse(V3PostAttackFreeze.pinsPosition(net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.DASH));
        assertTrue(V3PostAttackFreeze.pinsPosition(net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.RUSH));
        assertTrue(V3PostAttackFreeze.pinsPosition(net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.FINISHER));
    }
    @Test void dashRecoveryReplacesPinnedHoldsButDoesNotResetAnotherDashOrReintroducePinning() {
        var dash = net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.DASH;
        var rush = net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.RUSH;
        var finisher = net.bullettrain.xenopixelsmod.combat.v3.RecoverySnapshot.Kind.FINISHER;
        assertTrue(V3PostAttackFreeze.replaces(rush, dash));
        assertTrue(V3PostAttackFreeze.replaces(finisher, dash));
        assertFalse(V3PostAttackFreeze.replaces(dash, dash));
        assertFalse(V3PostAttackFreeze.replaces(dash, rush));
    }
    @Test void durationBeginsAtActualCompletionNotAtCastAdmission() {
        assertEquals(640, V3PostAttackFreeze.until(500, 140));
        assertEquals(900, V3PostAttackFreeze.until(760, 140));
        assertEquals(500, V3PostAttackFreeze.until(500, 0));
        assertEquals(1700, V3PostAttackFreeze.until(500, 99999));
    }
}
