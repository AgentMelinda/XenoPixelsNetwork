package net.bullettrain.xenopixelsmod.space.dragonball;

/** Pure compatibility policy for the legacy collectible and planetary Sable SDB implementations. */
public final class DragonBallModePolicy {
    public enum Mode {
        PLANETARY("planetary"),
        LEGACY("legacy");

        private final String key;
        Mode(String key) { this.key = key; }
        public String key() { return key; }
    }

    public enum BodyCensus { EMPTY, PRESENT, UNAVAILABLE }

    public record Decision(Mode requested, Mode effective, String refusalReason) {
        public boolean refused() { return !refusalReason.isEmpty(); }
    }

    private DragonBallModePolicy() {}

    /** Unknown or absent config values fail safe to the planetary mode. */
    public static Mode parse(String key) {
        if (key == null) return Mode.PLANETARY;
        for (Mode mode : Mode.values()) if (mode.key.equalsIgnoreCase(key.trim())) return mode;
        return Mode.PLANETARY;
    }

    /**
     * Legacy mode is allowed only when a valid ledger and complete Sable census prove no planetary SDB incarnation
     * exists. This prevents a mode change from stranding bodies whose UUID owns orbit and renderer state.
     */
    public static Decision resolve(String requestedKey, boolean ledgerValid, boolean savedBodyBinding,
                                   BodyCensus census) {
        Mode requested = parse(requestedKey);
        if (requested == Mode.PLANETARY) return new Decision(requested, Mode.PLANETARY, "");
        if (!ledgerValid) return refused(requested, "SDB saved data is invalid or unavailable");
        if (savedBodyBinding) return refused(requested, "saved planetary SDB body bindings still exist");
        if (census == null || census == BodyCensus.UNAVAILABLE)
            return refused(requested, "Sable body census is unavailable");
        if (census == BodyCensus.PRESENT)
            return refused(requested, "planetary SDB bodies still exist in Sable");
        return new Decision(requested, Mode.LEGACY, "");
    }

    private static Decision refused(Mode requested, String reason) {
        return new Decision(requested, Mode.PLANETARY, reason);
    }
}
