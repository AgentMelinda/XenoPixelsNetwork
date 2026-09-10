package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.client.combat.SparkingChargeClientState;
import net.bullettrain.xenopixelsmod.client.combat.SparkingClientState;
import net.bullettrain.xenopixelsmod.combat.Bt3SparkingCharge;
import net.bullettrain.xenopixelsmod.effect.ModEffects;
import net.minecraft.client.Minecraft;

/**
 * The ki bar's Sparking colours.
 *
 * <p>At full ki, a committed Max Power charge turns the bar red and converts its segments to gold.
 * Once Sparking starts, the remaining gold ki is the timer. Both HUD views ask here rather than
 * each deciding for itself.
 *
 * <p>Gold, not blue. The resting ki bar is already blue, so a blue Sparking state would have had to
 * lean on brightness alone to be legible; gold separates the two by hue and reads instantly. Change
 * the two constants to retune it; nothing else depends on the values.
 *
 * <p>Active state follows the server's effect/tracking signal; precharge progress is owner-only and
 * arrives through {@code SparkingChargePacket}.
 */
public final class SparkingKiBar {

    /** Gold fill while Sparking is up. */
    public static final int FILL = 0xFFFFC93C;
    /** Brighter gold highlight for the segmented view. */
    public static final int HIGHLIGHT = 0xFFFFF0A8;
    /** Full-ki segments waiting to be converted during the Max Power charge. */
    public static final int CHARGE_RED = 0xFFD32F2F;
    /** Warm edge on the waiting red segments. */
    public static final int CHARGE_RED_HIGHLIGHT = 0xFFFF8A80;

    private SparkingKiBar() {
    }

    /** Whether the local player is Sparking right now. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (mc.player.hasEffect(ModEffects.SPARKING)
                || SparkingClientState.isSparking(mc.player));
    }

    public static boolean charging() {
        return !active() && SparkingChargeClientState.isCharging();
    }

    public static int segmentCount() {
        return Bt3SparkingCharge.SEGMENTS;
    }

    public static int litSegments() {
        return charging() ? SparkingChargeClientState.litSegments() : 0;
    }

    public static int chargeSegmentFill(int index) {
        return index < litSegments() ? FILL : CHARGE_RED;
    }

    public static int chargeSegmentHighlight(int index) {
        return index < litSegments() ? HIGHLIGHT : CHARGE_RED_HIGHLIGHT;
    }

    /** {@code sparking} in place of {@code normal} while Sparking is up. */
    public static int fill(int normal) {
        return active() ? FILL : normal;
    }

    /** {@code sparking} in place of {@code normal} while Sparking is up. */
    public static int highlight(int normal) {
        return active() ? HIGHLIGHT : normal;
    }
}
