package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcVitalityMathTest {
    @Test
    void authoritativeHealthIsVanillaBasePlusScaledVitality() {
        // Player HP is 20 + getHealthBonus(); bonus is vit * VIT_scaling * form VIT mult.
        assertEquals(60.0, NpcVitalityMath.authoritativeMaxHealth(40, 1.0, 1.0));
        assertEquals(250_020.0, NpcVitalityMath.authoritativeMaxHealth(250_000, 1.0, 1.0));
    }

    @Test
    void tenThousandVitalityAtEffectiveOnePointEightMatchesRequestedPlayerHealth() {
        assertEquals(18_020.0,
                NpcVitalityMath.authoritativeMaxHealth(10_000, 1.8, 1.0));
    }

    @Test
    void authoritativeHealthAppliesFormMultiplierAndRaceVitScaling() {
        assertEquals(100.0, NpcVitalityMath.authoritativeMaxHealth(40, 2.0, 1.0));
        // Warrior VIT_scaling is 1.2 in dragonminez-2.1.3 race stats.
        assertEquals(20 + 12000, NpcVitalityMath.authoritativeMaxHealth(10_000, 1.0, 1.2));
        assertEquals(20 + 40 * 2 * 12 / 10, NpcVitalityMath.authoritativeMaxHealth(40, 2.0, 1.2));
        assertEquals(20 + 10_000, NpcVitalityMath.authoritativeMaxHealth(10_000, 1.0, 1.0),
                "HP is never 1:1 with VIT — vanilla 20 is always added");
    }

    /**
     * A max-vitality NPC reads exactly what a max-vitality player reads.
     *
     * <p>The number on the right is not a magic constant: it is what DragonMineZ's own character
     * screen shows, because {@code StatsData.getMaxHealth()} casts the max-health attribute to a
     * float and a float step at 3.8 billion is 256. This used to come out as {@code 3865470484} —
     * the same health, with the vanilla 20 still visible because our arithmetic stayed in double.
     * That read in game as the NPC being given 20 extra health it did not have.
     */
    @Test
    void maxVitReadsTheSameForAnNpcAsForAPlayer() {
        double npc = NpcVitalityMath.authoritativeMaxHealth(Integer.MAX_VALUE, 1.8, 1.0);
        double player = (float) (20 + (float) (Integer.MAX_VALUE * 1.8));
        assertEquals(player, npc);
        assertEquals("3865470464", NpcVitalityMath.displayedMaxHealth(npc));
        assertEquals(Integer.MAX_VALUE, NpcVitalityMath.npcStatsMaxHealth(npc));
    }

    /**
     * Every authoritative result survives a float round trip.
     *
     * <p>This is the property that makes an NPC and a player agree at <i>any</i> vitality rather
     * than only at the one that was tested by hand: DMZ displays a float, so a value that is not
     * exactly a float would display as some other number.
     */
    @Test
    void authoritativeHealthIsAlwaysExactlyRepresentableAsAFloat() {
        int[] vitalities = {0, 1, 10, 1_000, 250_000, 16_777_217, 100_000_000, Integer.MAX_VALUE};
        for (int vitality : vitalities) {
            double value = NpcVitalityMath.authoritativeMaxHealth(vitality, 1.8, 1.2);
            assertEquals(value, (double) (float) value,
                    "VIT " + vitality + " produced a value DragonMineZ's float readout cannot show");
        }
    }

    /**
     * The vanilla 20 is kept, not subtracted.
     *
     * <p>Dropping it would have made the max-vitality number match by coincidence while putting
     * every ordinary NPC 20 below an equivalent player, and a vitality-zero NPC on the 1 HP floor.
     */
    @Test
    void theVanillaBaseStillCountsWhereAFloatCanStillSeeIt() {
        assertEquals(20.0, NpcVitalityMath.authoritativeMaxHealth(0, 1.0, 1.0));
        assertEquals(30.0, NpcVitalityMath.authoritativeMaxHealth(10, 1.0, 1.0));
        assertEquals(1_020.0, NpcVitalityMath.authoritativeMaxHealth(1_000, 1.0, 1.0));
    }

    @Test
    void hybridHealthStillAddsTheConfiguredNativeBaseline() {
        assertEquals(60.0, NpcVitalityMath.hybridMaxHealth(20, 40, 1.0, 1.0));
        assertEquals(100.0, NpcVitalityMath.hybridMaxHealth(20, 40, 2.0, 1.0));
        assertEquals(20 + 48, NpcVitalityMath.hybridMaxHealth(20, 40, 1.0, 1.2));
    }

    @Test
    void invalidInputsFallBackSafely() {
        assertEquals(20.0, NpcVitalityMath.authoritativeMaxHealth(0, 1.0, 1.0));
        assertEquals(40.0, NpcVitalityMath.authoritativeMaxHealth(20, Double.NaN, 1.0));
        assertEquals(20.0, NpcVitalityMath.authoritativeMaxHealth(-100, 5.0, 1.0));
        assertEquals(21.0, NpcVitalityMath.hybridMaxHealth(0, 20, 1.0, 1.0));
    }

    @Test
    void calculationSaturatesInsteadOfOverflowing() {
        assertEquals(NpcVitalityMath.LIVING_MAX_HEALTH,
                NpcVitalityMath.authoritativeMaxHealth(Integer.MAX_VALUE, 1.0e20, 1.0e20));
        assertEquals(NpcVitalityMath.LIVING_MAX_HEALTH,
                NpcVitalityMath.hybridMaxHealth(Integer.MAX_VALUE, Integer.MAX_VALUE, 1.0e20, 1.0e20));
        assertEquals(NpcVitalityMath.NPC_STATS_MAX_HEALTH,
                NpcVitalityMath.npcStatsMaxHealth(NpcVitalityMath.LIVING_MAX_HEALTH));
    }

    @Test
    void fullHealthStaysFullWhenMaxIncreases() {
        assertEquals(100.0f,
                NpcVitalityMath.preserveHealthPercent(20.0f, 20.0f, 100.0f), 1.0e-6f);
    }

    @Test
    void damagedNpcKeepsItsHealthPercentage() {
        assertEquals(50.0f,
                NpcVitalityMath.preserveHealthPercent(10.0f, 20.0f, 100.0f), 1.0e-6f);
        assertEquals(25.0f,
                NpcVitalityMath.preserveHealthPercent(50.0f, 100.0f, 50.0f), 1.0e-6f);
    }

    @Test
    void zeroHealthDoesNotReviveAnNpc() {
        assertEquals(0.0f,
                NpcVitalityMath.preserveHealthPercent(0.0f, 20.0f, 100.0f), 1.0e-6f);
    }
}
