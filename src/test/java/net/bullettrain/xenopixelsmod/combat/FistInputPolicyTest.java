package net.bullettrain.xenopixelsmod.combat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FistInputPolicyTest {
    @Test void weaponsAndOffhandNeverAlsoOwnFists() {
        assertFalse(FistInputPolicy.emptyHands(false, true, false));
        assertFalse(FistInputPolicy.emptyHands(true, false, false));
        assertFalse(FistInputPolicy.emptyHands(true, true, true));
        assertTrue(FistInputPolicy.emptyHands(true, true, false));
    }
    @Test void nativeFallbackAndBlockRetargeting() {
        assertTrue(FistInputPolicy.ownsAttack(true, false, true, true, false, true));
        assertFalse(FistInputPolicy.ownsAttack(true, true, true, true, true, true));
        assertFalse(FistInputPolicy.ownsAttack(true, false, false, true, true, true));
        assertFalse(FistInputPolicy.ownsAttack(true, false, true, false, false, true));
        assertTrue(FistInputPolicy.ownsAttack(true, false, true, false, true, true));
        assertFalse(FistInputPolicy.ownsAttack(true, false, true, true, true, false));
    }
    @Test void fistsStayActiveOnABlockTargetSoMiningAndPunchingShareTheClick() {
        // Punching is unaffected by what the crosshair is on...
        assertTrue(FistInputPolicy.fistsActive(true, true, true, false, true));
        assertTrue(FistInputPolicy.fistsActive(true, true, false, true, true));
        // ...but the native attack is only suppressed away from a block, so the dig still runs.
        assertFalse(FistInputPolicy.ownsAttack(true, true, true, true, true, true));
        assertTrue(FistInputPolicy.ownsAttack(true, false, true, true, true, true));
    }
    @Test void fistsActiveStillRespectsHandsContextAndFeatureFlags() {
        assertFalse(FistInputPolicy.fistsActive(false, true, true, true, true));
        assertFalse(FistInputPolicy.fistsActive(true, false, true, true, true));
        assertFalse(FistInputPolicy.fistsActive(true, true, false, false, true));
        assertFalse(FistInputPolicy.fistsActive(true, true, true, true, false));
    }
}
