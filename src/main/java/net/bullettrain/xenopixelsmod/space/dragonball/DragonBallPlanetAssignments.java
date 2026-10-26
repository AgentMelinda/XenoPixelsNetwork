package net.bullettrain.xenopixelsmod.space.dragonball;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/** Resolves data-pack SDB host references without publishing assignments to missing chart bodies. */
public final class DragonBallPlanetAssignments {
    /** §16's first chunk-scale collider baseline; this is prototype geometry, not a final moon-size setting. */
    public static final double PROTOTYPE_BODY_RADIUS_BLOCKS = 16.0;

    /** Host geometry/orbit inputs needed to decide whether an assignment is safe to materialize. */
    public record HostOrbitInfo(double radiusBlocks, double gravitationalParameter) {
        public HostOrbitInfo {
            if (!(radiusBlocks > 0.0) || !Double.isFinite(radiusBlocks))
                throw new IllegalArgumentException("host radius must be finite and > 0");
            if (gravitationalParameter < 0.0 || !Double.isFinite(gravitationalParameter))
                throw new IllegalArgumentException("host gravitational parameter must be finite and >= 0");
        }
    }

    private DragonBallPlanetAssignments() {}

    /**
     * Applies a persisted player-authored track while retaining the current pack host and falling back to
     * the pack track if a later chart/size change makes the saved orbit overlap its host.
     */
    public static DragonBallPlanetDefinition effective(DragonBallPlanetDefinition authored,
                                                        DragonBallSavedData.ManualOrbit manual,
                                                        double hostRadiusBlocks, double bodyRadiusBlocks) {
        if (authored == null || manual == null
                || !(manual.radiusBlocks() > hostRadiusBlocks + bodyRadiusBlocks)) return authored;
        return new DragonBallPlanetDefinition(authored.slot(), authored.hostChartId(), manual.radiusBlocks(),
                manual.phaseRadians(), manual.periodSeconds());
    }

    public static Map<Integer, DragonBallPlanetDefinition> resolvable(
            Map<Integer, DragonBallPlanetDefinition> input, Predicate<String> hostExists, List<String> errors) {
        var resolved = new TreeMap<>(input);
        var skipped = new ArrayList<Integer>();
        resolved.forEach((slot, definition) -> {
            if (definition == null) {
                errors.add("Super Dragon Ball " + slot + ": missing host assignment (assignment skipped)");
                skipped.add(slot);
                return;
            }
            if (!hostExists.test(definition.hostChartId())) {
                errors.add("Super Dragon Ball " + slot + ": unavailable host chart body "
                        + definition.hostChartId() + " (assignment skipped)");
                skipped.add(slot);
            }
        });
        skipped.forEach(resolved::remove);
        return Collections.unmodifiableMap(resolved);
    }

    /**
     * Keeps only assignments that can produce a non-overlapping first-prototype Sable body and a defined orbit.
     * A positive host gravitational parameter is used when present; a data-pack-authored period is the explicit
     * alternative for static/zero-gravity hosts. One unsupported slot is diagnosed and skipped independently.
     */
    public static Map<Integer, DragonBallPlanetDefinition> physicallySupported(
            Map<Integer, DragonBallPlanetDefinition> resolved,
            java.util.function.Function<String, HostOrbitInfo> hostInfo,
            double bodyRadiusBlocks,
            List<String> errors) {
        if (resolved == null || resolved.isEmpty()) return Map.of();
        if (hostInfo == null || errors == null) throw new IllegalArgumentException("host resolver and errors are required");
        if (!(bodyRadiusBlocks > 0.0) || !Double.isFinite(bodyRadiusBlocks))
            throw new IllegalArgumentException("SDB body radius must be finite and > 0");
        var supported = new TreeMap<Integer, DragonBallPlanetDefinition>();
        for (var entry : resolved.entrySet()) {
            int slot = entry.getKey();
            DragonBallPlanetDefinition definition = entry.getValue();
            if (definition == null || definition.slot() != slot) {
                errors.add("Super Dragon Ball " + slot + ": invalid assignment (physical body skipped)");
                continue;
            }
            HostOrbitInfo host = hostInfo.apply(definition.hostChartId());
            if (host == null) {
                errors.add("Super Dragon Ball " + slot + ": unavailable host " + definition.hostChartId()
                        + " (physical body skipped)");
                continue;
            }
            // The assignment's orbit radius is centre-to-centre; requiring strict separation keeps the two
            // colliders from touching even before any orbit controller or player interaction runs.
            if (!(definition.orbitRadiusBlocks() > host.radiusBlocks() + bodyRadiusBlocks)) {
                errors.add("Super Dragon Ball " + slot + ": orbit radius " + definition.orbitRadiusBlocks()
                        + " does not clear host radius " + host.radiusBlocks() + " plus SDB radius "
                        + bodyRadiusBlocks + " (physical body skipped)");
                continue;
            }
            try {
                if (definition.orbitPeriodSeconds() > 0.0)
                    DragonBallPlanetOrbit.createWithPeriod(definition.orbitRadiusBlocks(),
                            definition.orbitPhaseRadians(), definition.orbitPeriodSeconds());
                else if (host.gravitationalParameter() > 0.0)
                    DragonBallPlanetOrbit.create(definition.orbitRadiusBlocks(),
                            definition.orbitPhaseRadians(), host.gravitationalParameter());
                else {
                    errors.add("Super Dragon Ball " + slot + ": host " + definition.hostChartId()
                            + " has no positive gravitational parameter and no authored orbit_period_seconds"
                            + " (physical body skipped)");
                    continue;
                }
            } catch (IllegalArgumentException invalidOrbit) {
                errors.add("Super Dragon Ball " + slot + ": invalid orbit: " + invalidOrbit.getMessage()
                        + " (physical body skipped)");
                continue;
            }
            supported.put(slot, definition);
        }
        return Collections.unmodifiableMap(supported);
    }

    /** Persist only validated host/orbit assignments; missing/invalid entries do not erase saved data. */
    public static boolean persistResolvedAssignments(DragonBallSavedData saved,
                                                     Map<Integer, DragonBallPlanetDefinition> resolved) {
        saved.requireGameplayStateValid();
        boolean changed = false;
        for (var entry : resolved.entrySet()) {
            int slot = entry.getKey();
            DragonBallPlanetDefinition definition = entry.getValue();
            if (slot < 1 || slot > CosmicDragonBalls.COUNT || definition == null || definition.slot() != slot)
                continue;
            DragonBallSavedData.Ball ball = saved.ball(slot);
            if (!definition.hostChartId().equals(ball.hostChartId) || !ball.hasPlanetOrbit
                    || Double.compare(definition.orbitRadiusBlocks(), ball.planetOrbitRadiusBlocks) != 0
                    || Double.compare(definition.orbitPhaseRadians(), ball.planetOrbitPhaseRadians) != 0
                    || Double.compare(definition.orbitPeriodSeconds(), ball.planetOrbitPeriodSeconds) != 0) {
                ball.hostChartId = definition.hostChartId();
                ball.planetOrbitRadiusBlocks = definition.orbitRadiusBlocks();
                ball.planetOrbitPhaseRadians = definition.orbitPhaseRadians();
                ball.planetOrbitPeriodSeconds = definition.orbitPeriodSeconds();
                ball.hasPlanetOrbit = true;
                changed = true;
            }
        }
        if (changed) saved.setDirty();
        return changed;
    }
}
