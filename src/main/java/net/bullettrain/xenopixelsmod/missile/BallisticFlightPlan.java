package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Versioned player-authored settings for the advanced ballistic planner. */
public final class BallisticFlightPlan {
    public static final int VERSION = 4;
    public static final int MAX_WAYPOINTS = 16;
    /** Manual points plus bounded AUTO/phase controller points. */
    public static final int MAX_CONTROLLER_WAYPOINTS = 36;

    public enum FlightMode { PURE_BALLISTIC, GUIDED_BOOST_GLIDE }
    public enum ArcPreference { AUTO, HIGH, LOW }
    public enum TrajectoryProfile {
        AUTO, PARABOLIC, HIGH_LOFT, LOW_ARC, DIRECT, CRUISE, TERRAIN_FOLLOWING, TOP_ATTACK
    }

    public record Waypoint(double x, double y, double z, boolean locked) {
        public Vec3 position() { return new Vec3(x, y, z); }
    }

    public record Settings(
            boolean autoEnabled,
            FlightMode mode,
            ArcPreference arc,
            TrajectoryProfile profile,
            boolean phaseLayerEnabled,
            boolean waypointLayerEnabled,
            boolean altitudeLayerEnabled,
            double motorCutoffFraction,
            double apexFraction,
            double terminalFraction,
            double minimumClearanceY,
            double ceilingY,
            double desiredAngleDeg,
            double angleCommandY,
            double angleCommandDeg,
            List<Waypoint> waypoints
    ) {
        public Settings {
            mode = mode == null ? FlightMode.GUIDED_BOOST_GLIDE : mode;
            arc = arc == null ? ArcPreference.AUTO : arc;
            profile = profile == null ? TrajectoryProfile.AUTO : profile;
            motorCutoffFraction = clamp(motorCutoffFraction, 0.01, 0.45);
            apexFraction = clamp(apexFraction, motorCutoffFraction + 0.01, 0.75);
            terminalFraction = clamp(terminalFraction, apexFraction + 0.05, 0.99);
            minimumClearanceY = finiteOr(minimumClearanceY, 0.0);
            ceilingY = finiteOr(ceilingY, 0.0);
            desiredAngleDeg = finiteOr(desiredAngleDeg, 0.0);
            if (desiredAngleDeg != 0.0) desiredAngleDeg = clamp(desiredAngleDeg, 1.0, 89.0);
            angleCommandY = finiteOr(angleCommandY, 0.0);
            angleCommandDeg = finiteOr(angleCommandDeg, 0.0);
            if (angleCommandY <= 0.0 || angleCommandDeg == 0.0) {
                angleCommandY = 0.0;
                angleCommandDeg = 0.0;
            } else {
                angleCommandDeg = clamp(angleCommandDeg, -89.0, 89.0);
            }
            List<Waypoint> safe = new ArrayList<>();
            if (waypoints != null) {
                for (Waypoint waypoint : waypoints) {
                    if (waypoint == null || safe.size() >= MAX_WAYPOINTS) break;
                    if (Double.isFinite(waypoint.x()) && Double.isFinite(waypoint.y())
                            && Double.isFinite(waypoint.z())) safe.add(waypoint);
                }
            }
            waypoints = List.copyOf(safe);
        }

        /** Source-compatible constructor for callers and legacy integrations. */
        public Settings(boolean autoEnabled, FlightMode mode, ArcPreference arc,
                        boolean phaseLayerEnabled, boolean waypointLayerEnabled, boolean altitudeLayerEnabled,
                        double motorCutoffFraction, double apexFraction, double terminalFraction,
                        double minimumClearanceY, double ceilingY, List<Waypoint> waypoints) {
            this(autoEnabled, mode, arc, TrajectoryProfile.AUTO, phaseLayerEnabled, waypointLayerEnabled,
                    altitudeLayerEnabled, motorCutoffFraction, apexFraction, terminalFraction,
                    minimumClearanceY, ceilingY, 0.0, 0.0, 0.0, waypoints);
        }

        /** Source-compatible constructor for version 2 full-profile settings. */
        public Settings(boolean autoEnabled, FlightMode mode, ArcPreference arc, TrajectoryProfile profile,
                        boolean phaseLayerEnabled, boolean waypointLayerEnabled, boolean altitudeLayerEnabled,
                        double motorCutoffFraction, double apexFraction, double terminalFraction,
                        double minimumClearanceY, double ceilingY, List<Waypoint> waypoints) {
            this(autoEnabled, mode, arc, profile, phaseLayerEnabled, waypointLayerEnabled,
                    altitudeLayerEnabled, motorCutoffFraction, apexFraction, terminalFraction,
                    minimumClearanceY, ceilingY, 0.0, 0.0, 0.0, waypoints);
        }

        /** Source-compatible constructor for version 3 settings with angle lock. */
        public Settings(boolean autoEnabled, FlightMode mode, ArcPreference arc, TrajectoryProfile profile,
                        boolean phaseLayerEnabled, boolean waypointLayerEnabled, boolean altitudeLayerEnabled,
                        double motorCutoffFraction, double apexFraction, double terminalFraction,
                        double minimumClearanceY, double ceilingY, double desiredAngleDeg,
                        List<Waypoint> waypoints) {
            this(autoEnabled, mode, arc, profile, phaseLayerEnabled, waypointLayerEnabled,
                    altitudeLayerEnabled, motorCutoffFraction, apexFraction, terminalFraction,
                    minimumClearanceY, ceilingY, desiredAngleDeg, 0.0, 0.0, waypoints);
        }

        public static Settings defaults() {
            return new Settings(true, FlightMode.GUIDED_BOOST_GLIDE, ArcPreference.AUTO, TrajectoryProfile.AUTO,
                    true, false, true, 0.08, 0.24, 0.82,
                    256.0, 0.0, 0.0, 0.0, 0.0, List.of());
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Version", VERSION);
            tag.putBoolean("Auto", autoEnabled);
            tag.putString("Mode", mode.name());
            tag.putString("Arc", arc.name());
            tag.putString("Profile", profile.name());
            tag.putBoolean("PhaseLayer", phaseLayerEnabled);
            tag.putBoolean("WaypointLayer", waypointLayerEnabled);
            tag.putBoolean("AltitudeLayer", altitudeLayerEnabled);
            tag.putDouble("MotorCutoffF", motorCutoffFraction);
            tag.putDouble("ApexF", apexFraction);
            tag.putDouble("TerminalF", terminalFraction);
            tag.putDouble("MinimumClearanceY", minimumClearanceY);
            tag.putDouble("CeilingY", ceilingY);
            tag.putDouble("DesiredAngleDeg", desiredAngleDeg);
            tag.putDouble("AngleCommandY", angleCommandY);
            tag.putDouble("AngleCommandDeg", angleCommandDeg);
            ListTag list = new ListTag();
            for (Waypoint waypoint : waypoints) {
                CompoundTag entry = new CompoundTag();
                entry.putDouble("X", waypoint.x());
                entry.putDouble("Y", waypoint.y());
                entry.putDouble("Z", waypoint.z());
                entry.putBoolean("Locked", waypoint.locked());
                list.add(entry);
            }
            tag.put("Waypoints", list);
            return tag;
        }

        public static Settings load(CompoundTag tag) {
            if (tag == null || tag.isEmpty()) return defaults();
            List<Waypoint> waypoints = new ArrayList<>();
            ListTag list = tag.getList("Waypoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_WAYPOINTS, list.size()); i++) {
                CompoundTag entry = list.getCompound(i);
                waypoints.add(new Waypoint(entry.getDouble("X"), entry.getDouble("Y"),
                        entry.getDouble("Z"), entry.getBoolean("Locked")));
            }
            return new Settings(
                    !tag.contains("Auto") || tag.getBoolean("Auto"),
                    enumOr(FlightMode.class, tag.getString("Mode"), FlightMode.GUIDED_BOOST_GLIDE),
                    enumOr(ArcPreference.class, tag.getString("Arc"), ArcPreference.AUTO),
                    enumOr(TrajectoryProfile.class, tag.getString("Profile"), TrajectoryProfile.AUTO),
                    !tag.contains("PhaseLayer") || tag.getBoolean("PhaseLayer"),
                    tag.getBoolean("WaypointLayer"),
                    !tag.contains("AltitudeLayer") || tag.getBoolean("AltitudeLayer"),
                    tag.contains("MotorCutoffF") ? tag.getDouble("MotorCutoffF") : 0.08,
                    tag.contains("ApexF") ? tag.getDouble("ApexF") : 0.24,
                    tag.contains("TerminalF") ? tag.getDouble("TerminalF") : 0.82,
                    tag.contains("MinimumClearanceY") ? tag.getDouble("MinimumClearanceY") : 256.0,
                    tag.getDouble("CeilingY"), tag.getDouble("DesiredAngleDeg"),
                    tag.getDouble("AngleCommandY"), tag.getDouble("AngleCommandDeg"), waypoints);
        }
    }

    public enum Phase { EJECT, BOOST, COAST, TERMINAL, HOLD }
    public record Sample(double fraction, double x, double y, double z, Phase phase,
                         boolean terrainKnown) {}

    public record Result(
            int revision,
            Settings settings,
            Vec3 launchPosition,
            Vec3 resolvedTarget,
            long targetShipId,
            boolean feasible,
            String status,
            double azimuthDeg,
            double elevationDeg,
            double requiredSpeedMps,
            double availableSpeedMps,
            double etaSeconds,
            double apexY,
            double impactSpeedMps,
            double predictedMiss,
            List<String> warnings,
            List<Sample> samples,
            List<Vec3> controllerWaypoints
    ) {
        public Result {
            settings = settings == null ? Settings.defaults() : settings;
            launchPosition = launchPosition == null ? Vec3.ZERO : launchPosition;
            resolvedTarget = resolvedTarget == null ? Vec3.ZERO : resolvedTarget;
            status = status == null ? "no solution" : status;
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            samples = samples == null ? List.of() : List.copyOf(samples.subList(0, Math.min(128, samples.size())));
            controllerWaypoints = controllerWaypoints == null ? List.of()
                    : List.copyOf(controllerWaypoints.subList(0, Math.min(MAX_CONTROLLER_WAYPOINTS, controllerWaypoints.size())));
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, finiteOr(value, min)));
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String value, E fallback) {
        try { return Enum.valueOf(type, value); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
