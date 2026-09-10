package net.bullettrain.xenopixelsmod.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatTextTest {

    @Test
    void smallWholeNumbersAreShownExactly() {
        assertEquals("0", StatText.format(0));
        assertEquals("1", StatText.format(1));
        assertEquals("9999", StatText.format(9999));
    }

    @Test
    void fractionsKeepOneDecimalBelowTheThreshold() {
        assertEquals("12.5", StatText.format(12.5));
    }

    @Test
    void abbreviationKicksInAtEachThreshold() {
        assertEquals("10.0K", StatText.format(10_000));
        assertEquals("1.00M", StatText.format(1_000_000));
        assertEquals("1.00B", StatText.format(1_000_000_000));
    }

    @Test
    void negativesAbbreviateOnMagnitudeNotSign() {
        // Buffs and debuffs can drive a displayed stat negative; the sign must survive.
        assertEquals("-1.00M", StatText.format(-1_000_000));
        assertEquals("-42", StatText.format(-42));
    }

    @Test
    void battlePowerStaysReadableAtDragonMineZScale() {
        assertEquals("2.50B", StatText.format(2_500_000_000.0));
    }

    @Test
    void titleCasesDragonMineZIdentifiers() {
        assertEquals("Saiyan", StatText.title("saiyan"));
        assertEquals("Namekian", StatText.title("NAMEKIAN"));
        assertEquals("Base", StatText.title("  base  "));
    }

    @Test
    void titleOfNothingIsEmptyRatherThanAnError() {
        assertEquals("", StatText.title(null));
        assertEquals("", StatText.title("   "));
    }

    @Test
    void multiplierAlwaysCarriesItsX() {
        assertEquals("x2.5", StatText.multiplier(2.5));
        assertEquals("x3.9", StatText.multiplier(3.9));
    }

    @Test
    void wholeMultipliersDropTheirTrailingZero() {
        // The bundle art writes these as x1 and x2, not x1.0 -- its README lists the example rows
        // as x3.9 and x1 -- so the column matches the art it was drawn from.
        assertEquals("x1", StatText.multiplier(1.0));
        assertEquals("x2", StatText.multiplier(2.0));
        assertEquals("x12", StatText.multiplier(12.0));
    }

    @Test
    void neutralMatchesDragonMineZsOwnTolerance() {
        assertTrue(StatText.isNeutral(1.0));
        assertTrue(StatText.isNeutral(1.005));
        assertTrue(StatText.isNeutral(0.995));
        assertFalse(StatText.isNeutral(1.5));
        assertFalse(StatText.isNeutral(1.02));
    }

    @Test
    void condenseShortensAGroupedNumber() {
        // The value DMZ printed on the RES row, which was colliding with its multiplier.
        assertEquals("10.00M", StatText.condense("10,004,999"));
        assertEquals("32.1K", StatText.condense("32,090"));
    }

    @Test
    void condenseIgnoresWhicheverSeparatorTheLocaleUses() {
        // These stat values are always whole, so every separator can be dropped without ambiguity.
        assertEquals("10.00M", StatText.condense("10.004.999"));
        assertEquals("10.00M", StatText.condense("10 004 999"));
    }

    @Test
    void condenseLeavesSomethingUnparseableAlone() {
        assertEquals("", StatText.condense(""));
        assertEquals("--", StatText.condense("--"));
    }

    @Test
    void splitSeparatesTheMultiplierDmzAppended() {
        StatText.Split split = StatText.split("1240 x1.5");
        assertEquals("1240", split.number());
        assertEquals("x1.5", split.mult());
        assertFalse(split.neutral());
    }

    @Test
    void splitSurvivesGroupedNumbers() {
        // A grouping separator must never be mistaken for the suffix.
        StatText.Split split = StatText.split("1,240,500 x2.3");
        assertEquals("1,240,500", split.number());
        assertEquals("x2.3", split.mult());
    }

    @Test
    void splitOfAPlainNumberReportsTheNeutralMultiplier() {
        // DMZ omits the suffix entirely when the multiplier is within 0.01 of 1.0, so a bare number
        // is not "no multiplier" -- it is exactly x1, and the column says so.
        StatText.Split split = StatText.split("980");
        assertEquals("980", split.number());
        assertEquals("x1", split.mult());
        assertTrue(split.neutral());
    }

    @Test
    void splitTakesTheLastSuffixNotTheFirst() {
        // Defensive: if a number ever contained " x" the trailing multiplier still wins.
        StatText.Split split = StatText.split("1 x2 x3.0");
        assertEquals("1 x2", split.number());
        assertEquals("x3.0", split.mult());
    }

    @Test
    void splitOfNothingDoesNotThrow() {
        StatText.Split split = StatText.split(null);
        assertEquals("", split.number());
        assertEquals("x1", split.mult());
        assertTrue(split.neutral());
    }

    @Test
    void aValueJustUnderAUnitRollsIntoItRatherThanReadingAsAThousandOfTheLast() {
        // A DragonMineZ server with the stat cap at 999,999,999 hands us exactly this, and the
        // themed panel abbreviates it to fit beside the multiplier column. It used to come out as
        // "1000.00M", which sits next to the stock panel's "999,999,999" and reads as a bigger,
        // different number.
        assertEquals("1.00B", StatText.format(999_999_999));
        assertEquals("1.00M", StatText.format(999_999));
        assertEquals("1.00M", StatText.format(999_990));
        assertEquals("-1.00B", StatText.format(-999_999_999));
        // Just below the rounding boundary, the unit is unchanged.
        assertEquals("999.99M", StatText.format(999_994_999));
        assertEquals("999.9K", StatText.format(999_949));
    }

    @Test
    void condenseRollsOverTheSameWay() {
        // This is the path the themed DragonMineZ panel actually takes: it re-abbreviates the
        // string DragonMineZ already drew, never the raw stat.
        assertEquals("1.00B", StatText.condense("999,999,999"));
    }
}
