package net.bullettrain.xenopixelsmod.combat.overcharge;

/**
 * Pure charge-overcharge maths. No Minecraft types, so the curve can be unit-tested
 * without a running game — same reason {@code BeamSurgeState} is Minecraft-free.
 *
 * <p>DMZ's own {@code KiAttackData.OVERCHARGE_MAX_PERCENT} is 175 and its cost
 * formula is {@code 1 + (percent - 100) / 75}. This class keeps that 75-point
 * denominator on purpose: widening the constant would make a stock 175% charge
 * almost free.
 */
public final class ChargeOverchargeMath {

    /** DMZ gameplay cap. Charge past this is ours. */
    public static final float DMZ_CAP = 175.0f;
    /** {@code OVERCHARGE_MAX_PERCENT - 100} as it exists in DragonMineZ. */
    public static final float COST_SPAN = 75.0f;
    /** {@code KiAttackData.OVERCHARGE_TIER_PERCENT}. */
    public static final float TIER_PERCENT = 25.0f;

    /** Camera / feedback steps. 200 is the first overcharge beat — a flash there fogged the view. */
    public static final float[] CAMERA_TIERS = {400.0f, 600.0f, 800.0f, 1000.0f};

    private ChargeOverchargeMath() {
    }

    /**
     * Mirrors {@code KiAttackData.costMultiplier} with the original 75-point span.
     * Do not substitute a widened cap here.
     */
    public static float costMultiplier(float percent) {
        if (!Float.isFinite(percent)) return 0.0f;
        if (percent <= 100.0f) return Math.max(0.0f, percent) / 100.0f;
        return 1.0f + (percent - 100.0f) / COST_SPAN;
    }

    /**
     * Incremental ki cost to move charge from {@code oldPercent} to {@code newPercent}.
     *
     * <p>Matches {@code TickHandler.handleTechniqueCharge}:
     * {@code 0.5 * base * (costMultiplier(new) - costMultiplier(old))}.
     */
    public static float chargeCostDelta(float oldPercent, float newPercent, float baseCost) {
        if (!Float.isFinite(oldPercent) || !Float.isFinite(newPercent) || !Float.isFinite(baseCost)) {
            return 0.0f;
        }
        if (newPercent <= oldPercent || baseCost <= 0.0f) return 0.0f;
        return 0.5f * baseCost * (costMultiplier(newPercent) - costMultiplier(oldPercent));
    }

    /** Percent points added per tick above 100%, matching DMZ's overcharge rate. */
    public static float incrementRate(int baseChargeTicks) {
        int ticks = Math.max(1, baseChargeTicks);
        return TIER_PERCENT / (float) ticks;
    }

    /**
     * Damage multiplier applied on top of the 1.75× DMZ already baked in at fire.
     * 175% is a no-op (1.0). 1000% is {@code 1000/175 ≈ 5.71}, then soft-capped.
     */
    public static float damageFactor(float percent, float maxScale, boolean fullGameplay) {
        if (!Float.isFinite(percent) || percent <= DMZ_CAP) return 1.0f;
        float raw = percent / DMZ_CAP;
        if (!Float.isFinite(raw) || raw < 1.0f) return 1.0f;
        return fullGameplay ? raw : Math.min(Math.max(1.0f, maxScale), raw);
    }

    /**
     * Size / speed / life / explosion growth from excess over 175.
     * 175% and below returns 1.0.
     */
    public static float excessScale(float percent, float perPercent) {
        if (!Float.isFinite(percent) || !Float.isFinite(perPercent) || perPercent < 0.0f) {
            return 1.0f;
        }
        float excess = Math.max(0.0f, percent - DMZ_CAP);
        float scale = 1.0f + excess * perPercent;
        return Float.isFinite(scale) ? Math.max(1.0f, scale) : 1.0f;
    }

    /**
     * Visual size growth that starts at 100% so a transformed normal charge is already
     * bigger, then keeps climbing through overcharge.
     */
    public static float chargeVisualScale(float percent, float perPercent) {
        if (!Float.isFinite(percent) || !Float.isFinite(perPercent) || perPercent < 0.0f) {
            return 1.0f;
        }
        float excess = Math.max(0.0f, percent - 100.0f);
        float scale = 1.0f + excess * perPercent;
        return Float.isFinite(scale) ? Math.max(1.0f, scale) : 1.0f;
    }

    /**
     * Dampened form size. A raw form multiplier can be tens or hundreds; this must
     * never return that number.
     *
     * <p>{@code 1 + factor * min(ln(max(formMult, 1)), logCap)}.
     */
    public static float formSizeScale(double formMult, float factor, float logCap) {
        if (!Double.isFinite(formMult) || formMult <= 1.0 || !Float.isFinite(factor) || factor <= 0.0f) {
            return 1.0f;
        }
        double logged = Math.log(formMult);
        if (!Double.isFinite(logged) || logged <= 0.0) return 1.0f;
        float cap = Float.isFinite(logCap) && logCap > 0.0f ? logCap : 4.0f;
        float extra = factor * (float) Math.min(logged, cap);
        float scale = 1.0f + extra;
        return Float.isFinite(scale) ? Math.max(1.0f, scale) : 1.0f;
    }

    /**
     * 0 below 400, then 1..4 at 400 / 600 / 800 / 1000.
     * Used only to decide when to fire a camera cue. 200 is intentionally quiet.
     */
    public static int cameraTier(float percent) {
        if (!Float.isFinite(percent)) return 0;
        int tier = 0;
        for (float step : CAMERA_TIERS) {
            if (percent + 0.01f >= step) tier++;
        }
        return tier;
    }

    public static boolean scalesSize(String kiType) {
        if (kiType == null) return false;
        return switch (kiType) {
            case "WAVE", "BEAM", "LASER", "DISK",
                 "GIANT_BALL", "MEDIUM_BALL", "SMALL_BALL", "EXPLOSION" -> true;
            default -> false;
        };
    }

    public static boolean scalesLife(String kiType) {
        if (kiType == null) return false;
        return switch (kiType) {
            case "WAVE", "BEAM", "LASER" -> true;
            default -> false;
        };
    }

    public static boolean isInstant(String kiType) {
        return "SMALL_BALL".equals(kiType) || "LASER".equals(kiType);
    }
}
