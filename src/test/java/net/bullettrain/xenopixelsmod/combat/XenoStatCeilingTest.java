package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stat ceiling rule.
 *
 * <p>Worth pinning rather than eyeballing on a live server: a ceiling read wrong is either a cap
 * that refuses to lift, or — far worse — a stat written past what DragonMineZ's {@code int} fields
 * hold, which wraps negative and looks like corruption rather than a bad setting.
 */
class XenoStatCeilingTest {

    @Test
    void offMeansDragonMineZKeepsItsOwnConfiguredMaximum() {
        assertTrue(XenoStatCeiling.deferring(XenoStatCeiling.OFF));
        assertEquals(1_000_000_000, XenoStatCeiling.effective(XenoStatCeiling.OFF, 1_000_000_000));
        // A hand-edited negative is "off" too, never a real ceiling.
        assertTrue(XenoStatCeiling.deferring(-1));
        assertEquals(50_000, XenoStatCeiling.effective(-1, 50_000));
    }

    @Test
    void anOverrideReplacesDragonMineZsValue() {
        assertFalse(XenoStatCeiling.deferring(2_000_000_000));
        assertEquals(2_000_000_000,
                XenoStatCeiling.effective(2_000_000_000, 1_000_000_000));
        // It can lower as well as raise: the override is the ceiling, not a maximum of the two.
        assertEquals(5_000, XenoStatCeiling.effective(5_000, 1_000_000_000));
    }

    @Test
    void nothingCanBeStoredOutsideWhatDragonMineZCanHold() {
        // Integer.MAX_VALUE is a wall in DragonMineZ's data model: Stats keeps each stat in an int.
        assertEquals(Integer.MAX_VALUE, XenoStatCeiling.MAX);
        assertEquals(Integer.MAX_VALUE, XenoStatCeiling.store(Integer.MAX_VALUE));
        // Below DragonMineZ's own floor, its getter would raise it back anyway, so store it raised.
        assertEquals(XenoStatCeiling.MIN, XenoStatCeiling.store(1));
        assertEquals(XenoStatCeiling.MIN, XenoStatCeiling.clamp(Integer.MIN_VALUE));
        assertEquals(XenoStatCeiling.MAX, XenoStatCeiling.clamp(Integer.MAX_VALUE));
    }

    @Test
    void zeroAndBelowStoreAsOffRatherThanAsACeilingOfNothing() {
        // Storing 0 as itself would read back as "cap every stat at zero" the moment deferring()
        // was ever loosened, so the stored form collapses to one unambiguous value.
        assertEquals(XenoStatCeiling.OFF, XenoStatCeiling.store(0));
        assertEquals(XenoStatCeiling.OFF, XenoStatCeiling.store(-999));
    }
}
