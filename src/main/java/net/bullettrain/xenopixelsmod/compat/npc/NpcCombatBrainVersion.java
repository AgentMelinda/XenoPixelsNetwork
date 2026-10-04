package net.bullettrain.xenopixelsmod.compat.npc;

/**
 * Per-NPC Combat Brain stack. {@link #V1} is the flag-driven loop.
 * {@link #V2} is the saga-style rewrite. {@link #V3} adds DMZ flight. {@link #V4} is the
 * legacy MyNPCs/CustomNPCs CombatContext engine. {@link #V5} is reserved for native Xeno NPCs.
 *
 * <p>{@link #V6}, {@link #V7}, {@link #V8} and {@link #V9} are the DragonMineZ-driven brains. They run the
 * same decision tree, ported from DMZ's {@code SagasCombatBrain.decide} and fed the same signals its
 * {@code CombatContext} carries. They differ on two axes:
 *
 * <ul>
 *   <li><b>Per-action toggles</b> ({@link #honoursToggles}) - V6 honours the Brain tab's switches;
 *       V7 and V8 hand every choice to the DMZ tree; V9 honors the switches.
 *   <li><b>Xeno specials</b> ({@link #usesXenoSpecials}) - the layer this mod added on top of the
 *       port: the BT3 combo choreography, and the repositioning moves (chase, vanish, backstep,
 *       teleportAbove) that are ours rather than DMZ's. V6 uses them, V7 never does, and V8 leaves
 *       it to the profile's own switch.
 * </ul>
 *
 * <p>So V7 is the clean room - the ported DMZ tree and nothing of ours - V8 preserves the existing
 * tree with the Xeno layer available, and V9 adds per-action switches without changing V8 saves.
 *
 * <p>Neither one <em>calls</em> DMZ's brain. It cannot be called: {@code CombatContext.self} is a
 * {@code final DBSagasEntity}, and a Xeno NPC is a {@code PathfinderMob}. They are ports, and the
 * constants they run on ({@link NpcCombatRanges}) are read from the DMZ jar rather than invented.
 */
public enum NpcCombatBrainVersion {
    V1,
    V2,
    V3,
    V4,
    V5,
    V6,
    V7,
    /**
     * The DMZ tree with the Xeno specials switchable.
     *
     * <p>Added when V7 was redefined. V7 previously meant "fully DragonMineZ toggles-wise" while
     * still firing BT3 combos, Hakai and the teleport moves; profiles stored that way migrate here
     * rather than silently losing those, which is the behaviour they have always had.
     */
    V8,
    /** DMZ saga tree with its action switches honored; this is additive to V8's fixed behavior. */
    V9;

    public static NpcCombatBrainVersion byName(String name) {
        if (name == null || name.isBlank()) {
            return V1;
        }
        String key = name.trim();
        if (key.startsWith("v") || key.startsWith("V")) {
            key = key.substring(1);
        }
        return switch (key) {
            case "2" -> V2;
            case "3" -> V3;
            case "4" -> V4;
            case "5" -> V5;
            case "6" -> V6;
            case "7" -> V7;
            case "8" -> V8;
            case "9" -> V9;
            default -> V1;
        };
    }

    public static NpcCombatBrainVersion byLegacyName(String name) {
        return byName(name).legacyCompatible();
    }

    public static NpcCombatBrainVersion fromStored(int raw) {
        return switch (raw) {
            case 2 -> V2;
            case 3 -> V3;
            case 4 -> V4;
            case 5 -> V5;
            case 6 -> V6;
            case 7 -> V7;
            case 8 -> V8;
            case 9 -> V9;
            default -> V1;
        };
    }

    public int stored() {
        return switch (this) {
            case V1 -> 1;
            case V2 -> 2;
            case V3 -> 3;
            case V4 -> 4;
            case V5 -> 5;
            case V6 -> 6;
            case V7 -> 7;
            case V8 -> 8;
            case V9 -> 9;
        };
    }

    /** Saga decision tree (v2 and v3). */
    public boolean isV2() {
        return this == V2 || this == V3;
    }

    public boolean isV3() {
        return this == V3;
    }

    public boolean isV4() {
        return this == V4;
    }

    public boolean isNativeV5() {
        return this == V5;
    }

    /**
     * Whether this version runs the saga decision tree rather than the v1 flag loop.
     *
     * <p>V2 and V3 always did. V6 and V7 are the same tree with a fuller signal set, so they run it
     * too - the difference between them is {@link #honoursToggles()}, not the tree.
     */
    public boolean usesSagaTree() {
        return isV2() || isDmzPort();
    }

    /** Either DragonMineZ-driven brain: the ported decision tree, toggles or not. */
    public boolean isDmzPort() {
        return this == V6 || this == V7 || this == V8 || this == V9;
    }

    /**
     * Whether this brain may fire the moves this mod added on top of the DMZ port.
     *
     * <p>Named precisely, because a toggle nobody can define is a toggle nobody can test. A "Xeno
     * special" is one of:
     *
     * <ul>
     *   <li>the BT3 combo choreography ({@code Bt3ComboChoreography} beats played through
     *       {@code NpcDmzAnim}),
     *   <li>the repositioning set in {@code NpcCombatMoves} - chase, vanish, backstep and
     *       teleportAbove - which land through {@code Bt3CombatPacket.chaseLanding} and are ours.
     * </ul>
     *
     * <p>The DMZ tree's own actions - everything in {@code NpcCombatProfile.BRAIN_ACTION_KEYS}:
     * strike, charge, flyingFist, the ki casts, ascend, the deflects - are ports of DMZ behaviour
     * and stay in every version. They are not what this gates.
     *
     * <p><b>Hakai is deliberately not on the list.</b> No brain casts it - {@code NpcCombatBrain}
     * only asks {@code NpcHakai.isChanneling} so it does not interrupt one - and the sole callers
     * are the two script APIs. A script that asks for Hakai is an operator asking for it by name,
     * which no brain-version switch should override.
     *
     * <p>False for V7 alone. V8 answers true here and then defers to the profile's own switch, so
     * that one field decides it rather than two places that could disagree.
     */
    public boolean usesXenoSpecials() {
        return this != V7;
    }

    /**
     * Whether the Brain tab's per-action toggles still apply.
     *
     * <p>False for {@link #V7} and {@link #V8}, which is the point of both: DMZ's logic decides
     * every action, and they differ only on whether the Xeno layer is available on top. The editor
     * must <em>hide</em> those toggles when this is false rather than leave them on screen doing
     * nothing - a visible control that is ignored is the dead-control bug all over again.
     */
    public boolean honoursToggles() {
        return this != V7 && this != V8;
    }

    public NpcCombatBrainVersion legacyCompatible() {
        return this == V5 ? V4 : this;
    }

    public String label() {
        return switch (this) {
            case V1 -> "v1";
            case V2 -> "v2";
            case V3 -> "v3";
            case V4 -> "v4";
            case V5 -> "v5";
            case V6 -> "v6";
            case V7 -> "v7";
            case V8 -> "v8";
            case V9 -> "v9";
        };
    }

    public NpcCombatBrainVersion next() {
        return NpcBrainPolicy.resolve(nextRaw());
    }

    private NpcCombatBrainVersion nextRaw() {
        return switch (this) {
            case V1 -> V2;
            case V2 -> V3;
            case V3 -> V4;
            case V4 -> V6;
            case V6 -> V7;
            case V7 -> V8;
            case V8 -> V9;
            case V5, V9 -> V1;
        };
    }

    public NpcCombatBrainVersion nextNative() {
        return NpcBrainPolicy.resolve(nextNativeRaw());
    }

    private NpcCombatBrainVersion nextNativeRaw() {
        return switch (this) {
            case V1 -> V2;
            case V2 -> V3;
            case V3 -> V4;
            case V4 -> V5;
            case V5 -> V6;
            case V6 -> V7;
            case V7 -> V8;
            case V8 -> V9;
            case V9 -> V1;
        };
    }
}
