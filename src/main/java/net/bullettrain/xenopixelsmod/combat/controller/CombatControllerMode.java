package net.bullettrain.xenopixelsmod.combat.controller;

import java.util.Locale;

/**
 * Which combat controller the server runs. Persisted as {@code combatControllerMode} in the
 * server config and mirrored to clients over {@code SyncServerConfigPacket}.
 *
 * <p>{@link #LEGACY} is the pre-manual Xeno/DMZ controller and is the default forever: an owner
 * who never touches the config, or whose file is malformed, must get the combat they already
 * had. {@link #BT3_MANUAL} is the explicit opt-in for the server-authoritative manual BT3
 * controller and its choreography, and {@link #V2} for the rewritten v2 combat. No Minecraft dependency so it can be unit-tested and reused
 * by the addon API later.
 */
public enum CombatControllerMode {
    LEGACY("legacy"),
    BT3_MANUAL("bt3_manual"),
    /**
     * XenoCombat v2: the state-machine controller under {@code combat.v2}. Appended, and opt-in
     * exactly like {@link #BT3_MANUAL}; {@link #LEGACY} (v1) stays the default.
     */
    V2("v2"),
    /** Opt-in V3 controller. Existing persisted ids and default remain stable. */
    V3("v3");

    public static final CombatControllerMode DEFAULT = LEGACY;

    private final String id;

    CombatControllerMode(String id) {
        this.id = id;
    }

    /** Stable config/wire id. */
    public String id() {
        return id;
    }

    /**
     * Lenient read used for config files and the wire: anything that is not an explicit manual
     * opt-in resolves to {@link #LEGACY}.
     */
    public static CombatControllerMode fromId(String raw) {
        CombatControllerMode strict = parseStrict(raw);
        return strict == null ? DEFAULT : strict;
    }

    /**
     * Strict read for operator input. Returns {@code null} for anything that is not exactly one
     * of the known ids (case/whitespace-insensitive, {@code -} accepted for {@code _}) so a command
     * can report "unknown mode" instead of silently picking legacy.
     */
    public static CombatControllerMode parseStrict(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (key.isEmpty()) return null;
        for (CombatControllerMode mode : values()) {
            if (mode.id.equals(key)) return mode;
        }
        return null;
    }

    /**
     * Whether moving from {@code previous} to {@code next} must sweep all live combat state.
     * An unknown previous mode (first observation after boot) counts as a change so state left
     * over from a save written under the other controller is cleared too.
     */
    public static boolean requiresCleanup(CombatControllerMode previous, CombatControllerMode next) {
        return previous == null || previous != (next == null ? DEFAULT : next);
    }

    /** What an explicit switch request amounts to, given the current config and swept state. */
    public enum SwitchOutcome {
        /** Config already says this mode and it has been swept for: nothing to do. */
        UNCHANGED,
        /** Config already said this mode but the sweep had not run yet (drift via another path). */
        SWEPT_SAME_MODE,
        /** The mode actually changed. */
        SWITCHED;

        public boolean workDone() {
            return this != UNCHANGED;
        }
    }

    /**
     * Pure decision behind the service's switch: {@code previous} is the mode config holds now,
     * {@code applied} the mode the server last swept for ({@code null} before the first sweep),
     * {@code requested} what the operator asked for ({@code null} reads as legacy).
     */
    public static SwitchOutcome decide(CombatControllerMode previous, CombatControllerMode applied,
                                       CombatControllerMode requested) {
        CombatControllerMode next = requested == null ? DEFAULT : requested;
        if (previous == next && applied == next) return SwitchOutcome.UNCHANGED;
        return previous == next ? SwitchOutcome.SWEPT_SAME_MODE : SwitchOutcome.SWITCHED;
    }
}
