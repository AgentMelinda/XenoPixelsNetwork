package net.bullettrain.xenopixelsmod.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * How XenoPixels sizes DragonMineZ's auras.
 *
 * <p>Client-side, like the HUD layouts, because auras are drawn locally from stats DMZ already
 * syncs: this changes what you see, not what anyone else does, and it can therefore be tuned live
 * without a server round trip.
 *
 * <p>Every knob is here rather than hard-coded so the aura can be dialled in from the game instead
 * of from a rebuild — the shape of a good aura is a matter of taste, and taste needs iteration.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoAuraConfig {

    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-aura.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Master switch. Off restores DragonMineZ's own aura sizing exactly. */
    public static boolean enabled = true;

    /** Battle power at which the resting aura has grown noticeably. */
    public static double powerPivot = 1.0e6;
    /** How steeply the resting aura grows per decade of battle power. */
    public static double powerGain = 0.25;
    /** Ceiling on the resting multiplier, so a huge character does not fill the screen. */
    public static double powerMax = 2.5;

    /** Extra height at a full power-up: 1.8 means the column reaches 2.8x its resting height. */
    public static double chargeHeight = 1.8;
    /** Extra width at a full power-up. Deliberately small — the silhouette is a pillar. */
    public static double chargeWidth = 0.25;
    /** Extra height while charging ki, independent from the transformation column. */
    public static double kiChargeHeight = 1.8;
    /** Extra width while charging ki, independent from the transformation column. */
    public static double kiChargeWidth = 0.25;
    /** Ticks a full rise takes. The fall back takes twice as long. */
    public static double rampTicks = 14.0;

    private XenoAuraConfig() {}

    /**
     * Ceilings for the tunables, shared by the config and {@code /xenohud aura}.
     *
     * <p>Raised a long way past anything playable, because the old ceilings of 12 were reachable in
     * ordinary use and there was no way past them. They are not removed: aura size multiplies
     * DragonMineZ's own model scale and feeds a shader draw, so a value with an extra zero in it
     * costs a session rather than looking silly, and a mistyped number should not be able to do
     * that.
     *
     * <p>They live here rather than in the command because the command was never the real limit. The
     * values are loaded straight out of {@code xenopixelsmod-aura.json}, so anyone editing that file
     * bypassed every bound the command enforced; {@link #load} now clamps against these too.
     */
    public static final double MAX_POWER = 256.0;
    /** Extra height at a full power-up, the knob a transformation stretches. */
    public static final double MAX_CHARGE_HEIGHT = 256.0;
    public static final double MAX_CHARGE_WIDTH = 64.0;
    public static final double MAX_GAIN = 64.0;
    public static final double MAX_PIVOT = 1.0e12;
    public static final double MAX_RAMP = 200.0;

    /** A stored value, held inside its ceiling and never NaN. */
    private static double clamp(double value, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(0.0, Math.min(max, value));
    }

    public static void reset() {
        enabled = true;
        powerPivot = 1.0e6;
        powerGain = 0.25;
        powerMax = 2.5;
        chargeHeight = 1.8;
        chargeWidth = 0.25;
        kiChargeHeight = 1.8;
        kiChargeWidth = 0.25;
        rampTicks = 14.0;
        save();
    }

    public static void load() {
        if (!Files.exists(PATH)) { save(); return; }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data d = GSON.fromJson(reader, Data.class);
            if (d == null) return;
            enabled = d.enabled;
            // Clamped on the way in. These are read straight from a file a player can edit, and an
            // aura scale of 1e9 is a frozen client rather than a big aura.
            powerPivot = Math.max(1.0, clamp(d.powerPivot, MAX_PIVOT, 1.0e6));
            powerGain = clamp(d.powerGain, MAX_GAIN, 0.25);
            powerMax = Math.max(1.0, clamp(d.powerMax, MAX_POWER, 2.5));
            chargeHeight = clamp(d.chargeHeight, MAX_CHARGE_HEIGHT, 1.8);
            chargeWidth = clamp(d.chargeWidth, MAX_CHARGE_WIDTH, 0.25);
            kiChargeHeight = clamp(d.kiChargeHeight, MAX_CHARGE_HEIGHT, 1.8);
            kiChargeWidth = clamp(d.kiChargeWidth, MAX_CHARGE_WIDTH, 0.25);
            rampTicks = Math.max(1.0, clamp(d.rampTicks, MAX_RAMP, 14.0));
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load aura config", e);
        }
    }

    public static void save() {
        Data d = new Data();
        d.enabled = enabled;
        d.powerPivot = powerPivot;
        d.powerGain = powerGain;
        d.powerMax = powerMax;
        d.chargeHeight = chargeHeight;
        d.chargeWidth = chargeWidth;
        d.kiChargeHeight = kiChargeHeight;
        d.kiChargeWidth = kiChargeWidth;
        d.rampTicks = rampTicks;
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) { GSON.toJson(d, writer); }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save aura config", e);
        }
    }

    private static final class Data {
        boolean enabled = true;
        double powerPivot = 1.0e6;
        double powerGain = 0.25;
        double powerMax = 2.5;
        double chargeHeight = 1.8;
        double chargeWidth = 0.25;
        double kiChargeHeight = 1.8;
        double kiChargeWidth = 0.25;
        double rampTicks = 14.0;
    }
}
