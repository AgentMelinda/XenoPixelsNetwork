package net.bullettrain.xenopixelsmod.aero;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aero flight-controller tuning, written to {@code config/xenopixelsmod-aero.json}.
 *
 * <p>Kept separate from {@code XenoServerConfig} deliberately: that class requires every field
 * to be repeated across its {@code Data} holder, {@code snapshot()} and {@code apply()}, and
 * Aero has enough knobs that the boilerplate would dominate. This follows the lighter
 * {@code OrbitalGravityConfig} shape instead.
 *
 * <p>Defaults reproduce the power budget from the design notes: a 1,200 FE/t baseline sized
 * around ten Mekanism Advanced Solar Generators, with engaged flight, the terrain map and
 * advanced cooling adding on top.
 */
public final class AeroConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-aero.json");

    /**
     * Master switch for the power requirement.
     *
     * <p>When false the controller runs for free and always reports
     * {@link AeroBus.PowerTier#NOMINAL}. Servers that do not want an FE dependency for flight
     * can turn this off without losing any other Aero behavior. Defaults off so flight works out
     * of the box with no FE infrastructure; also toggleable at runtime via
     * {@code /xenoaeropower <on|off|status>} ({@code command/AeroPowerCommands.java}), which
     * persists the change through {@link #save()}.
     */
    public static boolean requirePower = false;

    /** Buffer size in FE. Large enough that the combined load does not empty it instantly. */
    public static int energyCapacity = 5_000_000;

    /** Maximum FE/t accepted from adjacent power, sized so real infrastructure can saturate it. */
    public static int maxReceiveFePerTick = 12_000;

    /** Draw while the controller is in flight mode, before any subsystem is enabled. */
    public static int baselineDrawFePerTick = 1_200;

    // ---- Lift/drag aerodynamic model (see AeroAeroModel) ----
    // Game-tuning values, not physical constants. Sized so lift offsets gravity near a sensible
    // cruise speed. A bad value only makes a ship fly oddly — output is clamped in the model.

    /** Master switch. Off keeps ships as pure fly-by-attitude thrust vectoring (legacy feel). */
    public static boolean aeroModelEnabled = true;

    /** Lift/drag pressure scale (rho * S/m lumped). Lift ≈ gravity at cruise when tuned to taste. */
    public static double airDensity = 0.012;

    /** Effective wing area per unit mass; higher = floatier, more aero authority. */
    public static double wingAreaPerMass = 1.0;

    /** Baseline lift coefficient. */
    public static double clBase = 0.9;
    /** Extra lift per unit of full flap extension. */
    public static double clFlapPerExt = 0.7;
    /** Lift retained at zero angle of attack (0..1) so a level ship can hold altitude. */
    public static double clZeroFloor = 0.35;

    /** Baseline drag coefficient. */
    public static double cdBase = 0.02;
    /** Induced drag factor, scaled by sin(AoA)^2. */
    public static double cdAoa = 0.10;
    /** Extra drag per unit of full flap extension. */
    public static double cdFlapPerExt = 0.12;
    /** Additional drag once stalled. */
    public static double stallDragPenalty = 0.25;

    /** Stall onset, degrees of angle of attack. */
    public static double stallAoADeg = 12.0;
    /** AoA span over which lift rolls off from full to zero after onset. */
    public static double stallDropDeg = 8.0;

    /** Hard clamp on the aerodynamic acceleration magnitude (m/s²). Prevents coefficient blowups. */
    public static double maxAeroAccel = 40.0;

    /** How fast flaps travel, as a fraction of full extension per second. */
    public static double flapSpeedPerSec = 0.6;

    /**
     * Gain on {@link net.bullettrain.xenopixelsmod.aero.control.AeroControlSurfaceTorque}'s
     * per-panel impulse: {@code dynamicPressure * (deflectionDeg / maxDeflectDeg) * this * step}.
     * Kept proportional to
     * {@link net.bullettrain.xenopixelsmod.block.entity.ShipThrusterBlockEntity#DEFAULT_MAX_FORCE}
     * at a typical cruise dynamic pressure — when that force was corrected down (it was producing
     * ship-destroying impulses against this project's real block masses, see that field's own
     * javadoc), this was cut by the same ratio. Still a tunable game-feel constant, not a derived
     * physical value; expect to adjust it against how a real ship actually rolls/pitches in play.
     */
    public static double controlSurfaceTorqueScale = 125.0;

    /**
     * Keep the lumped hull lift even on a craft built with real wing panels.
     *
     * <p>Off by default: wing panels already produce lift through Sable's own per-block pass, so
     * leaving the hull term on as well counts the same lift twice. Turn this on only to compare
     * the two models side by side.
     */
    public static boolean aeroModelBaseLiftWithWings = false;

    /** Auto-flap: at or below this airspeed (blocks/s) flaps are fully extended. */
    public static double autoFlapExtendSpeed = 8.0;
    /** Auto-flap: at or above this airspeed (blocks/s) flaps are fully retracted. */
    public static double autoFlapRetractSpeed = 20.0;

    /**
     * Per-subsystem draw overrides, keyed by {@link AeroSubsystem#name()}.
     *
     * <p>Absent or unparsable entries fall back to the enum's own
     * {@link AeroSubsystem#drawFePerTick()}, so a typo degrades to the default rather than
     * zeroing a subsystem's cost.
     */
    public static Map<String, Integer> subsystemDraw = defaultSubsystemDraw();

    private AeroConfig() {
    }

    private static Map<String, Integer> defaultSubsystemDraw() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (AeroSubsystem subsystem : AeroSubsystem.values()) {
            map.put(subsystem.name(), subsystem.drawFePerTick());
        }
        return map;
    }

    /** Configured draw for a subsystem, falling back to its declared default. */
    public static int drawFor(AeroSubsystem subsystem) {
        if (subsystem == null) return 0;
        Integer configured = subsystemDraw.get(subsystem.name());
        if (configured == null || configured < 0) return subsystem.drawFePerTick();
        return configured;
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            requirePower = data.requirePower;
            energyCapacity = Math.max(1_000, data.energyCapacity);
            maxReceiveFePerTick = Math.max(1, data.maxReceiveFePerTick);
            baselineDrawFePerTick = Math.max(0, data.baselineDrawFePerTick);
            aeroModelEnabled = data.aeroModelEnabled;
            airDensity = Math.max(0.0, data.airDensity);
            wingAreaPerMass = Math.max(0.0, data.wingAreaPerMass);
            clBase = data.clBase;
            clFlapPerExt = data.clFlapPerExt;
            clZeroFloor = Math.max(0.0, Math.min(1.0, data.clZeroFloor));
            cdBase = data.cdBase;
            cdAoa = data.cdAoa;
            cdFlapPerExt = data.cdFlapPerExt;
            stallDragPenalty = data.stallDragPenalty;
            stallAoADeg = Math.max(1.0, data.stallAoADeg);
            stallDropDeg = Math.max(1.0, data.stallDropDeg);
            maxAeroAccel = Math.max(0.0, data.maxAeroAccel);
            flapSpeedPerSec = Math.max(0.05, data.flapSpeedPerSec);
            controlSurfaceTorqueScale = Math.max(0.0, data.controlSurfaceTorqueScale);
            aeroModelBaseLiftWithWings = data.aeroModelBaseLiftWithWings;
            autoFlapExtendSpeed = Math.max(0.0, data.autoFlapExtendSpeed);
            // Retract must sit above extend or the interpolation between them inverts.
            autoFlapRetractSpeed = Math.max(autoFlapExtendSpeed + 0.5, data.autoFlapRetractSpeed);
            if (data.subsystemDraw != null && !data.subsystemDraw.isEmpty()) {
                subsystemDraw = new LinkedHashMap<>(data.subsystemDraw);
            }
        } catch (Exception e) {
            // A malformed file must never stop the server booting.
            XenoPixelsMod.LOGGER.warn("Failed to load Aero config; using defaults", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                Data data = new Data();
                data.requirePower = requirePower;
                data.energyCapacity = energyCapacity;
                data.maxReceiveFePerTick = maxReceiveFePerTick;
                data.baselineDrawFePerTick = baselineDrawFePerTick;
                data.aeroModelEnabled = aeroModelEnabled;
                data.airDensity = airDensity;
                data.wingAreaPerMass = wingAreaPerMass;
                data.clBase = clBase;
                data.clFlapPerExt = clFlapPerExt;
                data.clZeroFloor = clZeroFloor;
                data.cdBase = cdBase;
                data.cdAoa = cdAoa;
                data.cdFlapPerExt = cdFlapPerExt;
                data.stallDragPenalty = stallDragPenalty;
                data.stallAoADeg = stallAoADeg;
                data.stallDropDeg = stallDropDeg;
                data.maxAeroAccel = maxAeroAccel;
                data.flapSpeedPerSec = flapSpeedPerSec;
                data.controlSurfaceTorqueScale = controlSurfaceTorqueScale;
                data.aeroModelBaseLiftWithWings = aeroModelBaseLiftWithWings;
                data.autoFlapExtendSpeed = autoFlapExtendSpeed;
                data.autoFlapRetractSpeed = autoFlapRetractSpeed;
                data.subsystemDraw = new LinkedHashMap<>(subsystemDraw);
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save Aero config", e);
        }
    }

    public static final class Data {
        public boolean requirePower = false;
        public int energyCapacity = 5_000_000;
        public int maxReceiveFePerTick = 12_000;
        public int baselineDrawFePerTick = 1_200;
        public boolean aeroModelEnabled = true;
        public double airDensity = 0.012;
        public double wingAreaPerMass = 1.0;
        public double clBase = 0.9;
        public double clFlapPerExt = 0.7;
        public double clZeroFloor = 0.35;
        public double cdBase = 0.02;
        public double cdAoa = 0.10;
        public double cdFlapPerExt = 0.12;
        public double stallDragPenalty = 0.25;
        public double stallAoADeg = 12.0;
        public double stallDropDeg = 8.0;
        public double maxAeroAccel = 40.0;
        public double flapSpeedPerSec = 0.6;
        public double controlSurfaceTorqueScale = 125.0;
        public boolean aeroModelBaseLiftWithWings = false;
        public double autoFlapExtendSpeed = 8.0;
        public double autoFlapRetractSpeed = 20.0;
        public Map<String, Integer> subsystemDraw;
    }
}
