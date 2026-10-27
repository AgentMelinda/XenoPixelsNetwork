package net.bullettrain.xenopixelsmod.space.chart;

import java.util.ArrayList;
import java.util.List;

/**
 * The handoff settings one chart body actually runs with, and the layer each value came from, for
 * {@code /xenospace config dump} (design §19.3). Read-only and pure: the caller passes the same inputs
 * {@code SpaceHandoffPass} reads (the live chart exit, the global space config and the body's dimension
 * sidecar), and every value goes through the same {@link DimensionSpaceSettings} accessor the pass calls.
 *
 * <p>Only keys with a live handoff consumer are listed. Where values come from, as the loaders build them:
 * <ul>
 *   <li>{@code exit_altitude}: {@code ChartRegistry.applyExitOverrides} puts a valid space-config override
 *       ({@code /xenospace handoff exit}) over the authored value; the authored value is the sidecar's when it
 *       sets one ({@code DimensionOverrides} copies it into the chart definition), otherwise the chart's.</li>
 *   <li>{@code warm_up_altitude}: copied from the sidecar the same way, otherwise the chart's.</li>
 *   <li>band, re-entry, overlay length, re-entry FX and the per-dimension switch: the sidecar's value, otherwise
 *       the global or built-in fallback the pass uses.</li>
 * </ul>
 * "Chart definition" covers the bundled or datapack JSON and the loader's own defaults (exit 700, warm-up
 * exit - 300); which of those supplied a value is not recorded after loading, so it is not claimed here.
 */
public final class EffectiveSpaceConfig {
    private EffectiveSpaceConfig() {}

    public enum Layer {
        SPACE_CONFIG("space config"),
        CHART("chart definition"),
        SIDECAR("dimension sidecar"),
        BUILT_IN("built-in default"),
        DERIVED("derived");

        private final String label;

        Layer(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** One resolved key. {@code note} explains a fallback or a value that is not in effect; may be empty. */
    public record Entry(String key, String value, Layer layer, String note) {}

    /** The chart fields the handoff reads for one body. */
    public record Body(String id, String dimensionId, boolean enterable, double authoredExitAltitude,
                       double liveExitAltitude, double warmUpAltitude) {
        public static Body of(CelestialBody body, double liveExitAltitude) {
            return new Body(body.id(), body.dimensionId(), body.enterable(), body.exitAltitude(), liveExitAltitude,
                    body.warmUpAltitude());
        }
    }

    /**
     * Global space-config values the handoff reads.
     *
     * @param exitOverride          this body's entry in the space config's exit overrides, or null
     * @param overlayFallbackMillis the built-in fade length used when no sidecar sets {@code handoff_overlay_ms}
     */
    public record Globals(boolean spaceEnabled, boolean handoffEnabled, boolean velocityCarryover,
                          double bandThickness, Double exitOverride, int overlayFallbackMillis) {}

    /**
     * @param sidecar the body's dimension sidecar, or null. Ignored for a body with no dimension, as in the pass,
     *                which only looks a sidecar up by {@code dimensionId}.
     */
    public static List<Entry> resolve(Body body, Globals globals, DimensionSpaceSettings sidecar) {
        DimensionSpaceSettings settings = body.dimensionId() == null ? null : sidecar;
        List<Entry> out = new ArrayList<>();

        boolean sidecarOn = settings == null || settings.enabledOrDefault(true);
        boolean active = globals.spaceEnabled() && globals.handoffEnabled() && body.enterable() && sidecarOn;
        if (!globals.spaceEnabled()) out.add(entry("handoff", active, Layer.SPACE_CONFIG, "space system is off"));
        else if (!globals.handoffEnabled()) out.add(entry("handoff", active, Layer.SPACE_CONFIG, "handoff is switched off"));
        else if (!body.enterable()) out.add(entry("handoff", active, Layer.CHART, "body is not enterable"));
        else if (!sidecarOn) out.add(entry("handoff", active, Layer.SIDECAR, "space.enabled=false"));
        else out.add(entry("handoff", active, Layer.SPACE_CONFIG, ""));

        boolean overridden = globals.exitOverride() != null && globals.exitOverride() == body.liveExitAltitude();
        if (overridden) {
            out.add(new Entry("exit_altitude", number(body.liveExitAltitude()), Layer.SPACE_CONFIG,
                    "override; authored " + number(body.authoredExitAltitude())));
        } else {
            String note = globals.exitOverride() == null ? ""
                    : "override " + number(globals.exitOverride()) + " rejected (not above warm-up)";
            out.add(new Entry("exit_altitude", number(body.liveExitAltitude()),
                    settings != null && settings.exitAltitude() != null ? Layer.SIDECAR : Layer.CHART, note));
        }

        out.add(new Entry("warm_up_altitude", number(body.warmUpAltitude()),
                settings != null && settings.warmUpAltitude() != null ? Layer.SIDECAR : Layer.CHART, ""));

        double band = settings == null ? globals.bandThickness() : settings.bandThicknessOr(globals.bandThickness());
        out.add(new Entry("band_thickness", number(band),
                settings != null && settings.bandThickness() != null ? Layer.SIDECAR : Layer.SPACE_CONFIG, ""));

        double reentry = settings == null ? body.liveExitAltitude() : settings.reentryAltitudeOr(body.liveExitAltitude());
        boolean reentrySet = settings != null && settings.reentryAltitude() != null;
        out.add(new Entry("reentry_altitude", number(reentry), reentrySet ? Layer.SIDECAR : Layer.DERIVED,
                reentrySet ? "" : "equals exit_altitude"));

        Double speed = settings == null ? null : settings.safeEntrySpeed();
        out.add(speed == null
                ? new Entry("safe_entry_speed", "unset", Layer.BUILT_IN, "no descent-speed prompt")
                : new Entry("safe_entry_speed", number(speed), Layer.SIDECAR, "blocks/tick"));

        int overlay = settings == null ? globals.overlayFallbackMillis()
                : settings.handoffOverlayMillisOr(globals.overlayFallbackMillis());
        out.add(new Entry("handoff_overlay_ms", Integer.toString(overlay),
                settings != null && settings.handoffOverlayMs() != null ? Layer.SIDECAR : Layer.BUILT_IN, ""));

        boolean plasma = settings != null && settings.reentryPlasmaShellEnabled();
        out.add(new Entry("reentry_fx", plasma ? "plasma_shell" : "off",
                settings != null && settings.flags().containsKey("reentry_fx") ? Layer.SIDECAR : Layer.BUILT_IN,
                "inbound handoffs only"));

        out.add(entry("velocity_carryover", globals.velocityCarryover(), Layer.SPACE_CONFIG, ""));
        return List.copyOf(out);
    }

    /** One line per entry: {@code key = value  [layer] note}. */
    public static String format(Body body, List<Entry> entries) {
        StringBuilder out = new StringBuilder(body.id());
        out.append(body.dimensionId() == null ? " (no dimension)" : " (" + body.dimensionId() + ")");
        for (Entry entry : entries) {
            out.append("\n  ").append(entry.key()).append(" = ").append(entry.value())
                    .append("  [").append(entry.layer().label()).append(']');
            if (!entry.note().isEmpty()) out.append(' ').append(entry.note());
        }
        return out.toString();
    }

    private static Entry entry(String key, boolean value, Layer layer, String note) {
        return new Entry(key, value ? "on" : "off", layer, note);
    }

    private static String number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1.0E15 ? Long.toString((long) value) : Double.toString(value);
    }
}
