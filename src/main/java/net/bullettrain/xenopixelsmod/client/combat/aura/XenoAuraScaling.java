package net.bullettrain.xenopixelsmod.client.combat.aura;

import com.dragonminez.common.stats.StatsData;
import net.bullettrain.xenopixelsmod.client.config.XenoAuraConfig;
import net.bullettrain.xenopixelsmod.combat.aura.AuraScaleCurve;
import net.bullettrain.xenopixelsmod.combat.aura.RampTable;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Resizes DragonMineZ's aura from the player's stats and from whether they are powering up.
 *
 * <p>Everything needed is already on the {@link StatsData} DragonMineZ hands to its own sizing
 * method: {@code getBattlePower()} for the resting size, {@code isActionCharging()} for a
 * transformation, and {@code isChargingKi()} for ordinary ki charging. So this needs no new state
 * on the wire and works for every player whose aura is drawn, not only the one holding the keyboard.
 *
 * <p>The power-up ramp needs somewhere to live between frames, keyed to whoever it belongs to. That
 * key comes from {@code StatsData.getPlayer()} — the very object DragonMineZ hands this method — and
 * not from the "who is being drawn" marker the colour override uses. That marker is only set inside
 * DMZ's aura <em>layer</em>, while this method is also called from the world aura, the first-person
 * aura and the character-screen preview, so reading it here made the ramp apply on some frames and
 * not others (the aura visibly jumped) and occasionally attributed one entity's charge to another.
 */
public final class XenoAuraScaling {

    /** Transformation and ki charge ease independently, then take the larger visible shape. */
    private static final RampTable<UUID> TRANSFORM_RAMPS = new RampTable<>();
    private static final RampTable<UUID> KI_RAMPS = new RampTable<>();

    private XenoAuraScaling() {
    }

    /**
     * The aura size DragonMineZ computed, grown by power and by powering up.
     *
     * @return a fresh array; DMZ stores the result field by field and may reuse the one it built
     */
    public static float[] apply(float[] base, StatsData stats) {
        if (base == null || base.length < 3 || stats == null || !XenoAuraConfig.enabled) {
            return base;
        }

        float power = AuraScaleCurve.fromBattlePower(stats.getBattlePower(),
                XenoAuraConfig.powerPivot, XenoAuraConfig.powerGain, XenoAuraConfig.powerMax);
        ChargeRamps ramps = ramps(stats);
        float height = Math.max(
                AuraScaleCurve.chargeHeight(ramps.transform, XenoAuraConfig.chargeHeight),
                AuraScaleCurve.chargeHeight(ramps.ki, XenoAuraConfig.kiChargeHeight));
        float width = Math.max(
                AuraScaleCurve.chargeWidth(ramps.transform, XenoAuraConfig.chargeWidth),
                AuraScaleCurve.chargeWidth(ramps.ki, XenoAuraConfig.kiChargeWidth));

        float[] out = base.clone();
        out[0] = base[0] * power * width;
        out[1] = base[1] * power * height;
        out[2] = base[2] * power * width;
        return out;
    }

    private static ChargeRamps ramps(StatsData stats) {
        boolean transforming;
        boolean chargingKi;
        Player owner;
        try {
            transforming = stats.getStatus() != null && stats.getStatus().isActionCharging();
            chargingKi = stats.getStatus() != null && stats.getStatus().isChargingKi();
            owner = stats.getPlayer();
        } catch (Throwable ignored) {
            return ChargeRamps.IDLE;
        }
        if (owner == null) {
            // Stats with no player behind them: the resting size is the right answer, and there is
            // nothing to key a ramp to.
            return ChargeRamps.IDLE;
        }
        UUID id = owner.getUUID();
        long now = System.nanoTime();
        float rampTicks = (float) XenoAuraConfig.rampTicks;
        return new ChargeRamps(
                TRANSFORM_RAMPS.advance(id, transforming, now, rampTicks),
                KI_RAMPS.advance(id, chargingKi, now, rampTicks));
    }

    private record ChargeRamps(float transform, float ki) {
        private static final ChargeRamps IDLE = new ChargeRamps(0.0f, 0.0f);
    }

    /** Forgets every ramp, so leaving a world does not carry one player's state into the next. */
    public static void clear() {
        TRANSFORM_RAMPS.clear();
        KI_RAMPS.clear();
    }
}
