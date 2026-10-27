package net.bullettrain.xenopixelsmod.space.smallplanet;

/** Shared decision for the deterministic field's presentation and its optional physical Sable incarnations. */
public final class SmallPlanetActivationPolicy {
    private SmallPlanetActivationPolicy() {}

    public enum Gate {
        READY("on"),
        SPACE_DISABLED("space disabled"),
        PHYSICS_DISABLED("physicalization disabled"),
        INCREMENTAL_WRITER_DISABLED("waiting for incremental writer opt-in"),
        GLOBAL_CAP_ZERO("global activation cap is zero"),
        PLAYER_CAP_ZERO("per-player activation cap is zero");

        private final String label;
        Gate(String label) { this.label = label; }
        public boolean enabled() { return this == READY; }
        public String label() { return label; }
    }

    public static Gate gate(boolean spaceEnabled, boolean physicalEnabled, boolean incrementalWriterEnabled,
                            int globalCap, int perPlayerCap) {
        if (!spaceEnabled) return Gate.SPACE_DISABLED;
        if (!physicalEnabled) return Gate.PHYSICS_DISABLED;
        if (!incrementalWriterEnabled) return Gate.INCREMENTAL_WRITER_DISABLED;
        if (globalCap <= 0) return Gate.GLOBAL_CAP_ZERO;
        if (perPlayerCap <= 0) return Gate.PLAYER_CAP_ZERO;
        return Gate.READY;
    }

    public static String discoveryNote(Gate gate) {
        if (gate == null) throw new NullPointerException("gate");
        return gate.enabled()
                ? "; nearby small planets may activate within configured range and caps as players approach"
                : "; small-planet physical activation is unavailable (" + gate.label() + ")";
    }
}
