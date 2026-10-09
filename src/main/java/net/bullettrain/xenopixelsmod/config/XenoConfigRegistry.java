package net.bullettrain.xenopixelsmod.config;

import net.bullettrain.xenopixelsmod.combat.targeting.LockOnConfig;
import net.bullettrain.xenopixelsmod.combat.v3.V3Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Unified last-session knobs for {@code /xenoconfig}. Combat keys stay on
 * {@link XenoServerConfigKeys}; party / lock-on / perf write their own JSON files.
 */
public final class XenoConfigRegistry {

    public enum Store {
        COMBAT, PARTY, LOCKON, PERF, V3
    }

    public enum Kind {
        BOOL, INT, FLOAT, DOUBLE
    }

    public static final class Result {
        public final boolean ok;
        public final String message;
        public final Store store;

        private Result(boolean ok, String message, Store store) {
            this.ok = ok;
            this.message = message;
            this.store = store;
        }

        public static Result ok(String message, Store store) {
            return new Result(true, message, store);
        }

        public static Result fail(String message) {
            return new Result(false, message, null);
        }
    }

    public static final class Entry {
        public final String id;
        public final Store store;
        public final Kind kind;
        public final String help;
        private final Supplier<String> getter;
        private final Consumer<String> applyRaw;

        private Entry(String id, Store store, Kind kind, String help,
                      Supplier<String> getter, Consumer<String> applyRaw) {
            this.id = id;
            this.store = store;
            this.kind = kind;
            this.help = help;
            this.getter = getter;
            this.applyRaw = applyRaw;
        }

        public String get() {
            return getter.get();
        }
    }

    private static final Map<String, Entry> BY_ID = new LinkedHashMap<>();
    private static final Map<String, String> ALIAS = new LinkedHashMap<>();

    static {
        register("v3.dragonDashRange", Store.V3, Kind.DOUBLE, "V3 Dragon Dash range (2-999 blocks)",
                () -> formatDouble(V3Config.get().dragonDashRange()), raw -> {
                    var previous = V3Config.get();
                    var edit = new V3Config.Values.Edit(previous);
                    edit.dragonDashRange = Double.parseDouble(raw);
                    V3Config.apply(edit.build());
                }, "dragonDashRange");
        register("v3.heavyAttackerStaminaCost", Store.V3, Kind.DOUBLE, "Heavy attacker stamina cost (0-100000; 0 disables)",
                () -> formatDouble(V3Config.get().heavyAttackerStaminaCost()), raw -> {
                    var previous = V3Config.get();
                    var edit = new V3Config.Values.Edit(previous);
                    edit.heavyAttackerStaminaCost = Double.parseDouble(raw);
                    V3Config.apply(edit.build());
                });
        register("v3.heavyVictimStaminaDrain", Store.V3, Kind.DOUBLE, "Heavy victim stamina drain (0-100000; 0 disables)",
                () -> formatDouble(V3Config.get().heavyVictimStaminaDrain()), raw -> {
                    var previous = V3Config.get();
                    var edit = new V3Config.Values.Edit(previous);
                    edit.heavyVictimStaminaDrain = Double.parseDouble(raw);
                    V3Config.apply(edit.build());
                });
        // Server-owned V3 tuning added 2026-10-08. Every key here is validated/clamped by V3Config.Values
        // and synced to clients read-only; none of them is client-writable.
        v3Double("v3.dragonDashSpeed", "V3 Dragon Dash flight speed, blocks per tick (0.5-12)",
                () -> V3Config.get().dragonDashSpeed(), (edit, value) -> edit.dragonDashSpeed = value, "dragonDashSpeed");
        v3Double("v3.dragonDashLaunchDistance", "Blocks the first Dragon Dash hit launches the target (4-64)",
                () -> V3Config.get().dragonDashLaunchDistance(), (edit, value) -> edit.dragonDashLaunchDistance = value,
                "dragonDashLaunchDistance", "dashLaunch");
        v3Double("v3.dragonDashFollowDistance", "Melee gap left behind the target by a follow-up dash teleport (1.5-8)",
                () -> V3Config.get().dragonDashFollowDistance(), (edit, value) -> edit.dragonDashFollowDistance = value,
                "dragonDashFollowDistance", "dashFollowDistance");
        v3Int("v3.dragonDashFollowCooldownTicks", "Ticks between follow-up dash teleports (0-200)",
                () -> V3Config.get().dragonDashFollowCooldownTicks(),
                (edit, value) -> edit.dragonDashFollowCooldownTicks = value, "dashFollowCooldown");
        v3Int("v3.dragonDashFollowWindowTicks", "Ticks a dash chain stays open for the next follow press (10-1200)",
                () -> V3Config.get().dragonDashFollowWindowTicks(),
                (edit, value) -> edit.dragonDashFollowWindowTicks = value, "dashFollowWindow");
        v3Double("v3.strikeLaunchDistance",
                "Blocks a Strike finishing shove travels (4-64; default 20; Dodoria Head Breaker uses DOWN)",
                () -> V3Config.get().strikeLaunchDistance(), (edit, value) -> edit.strikeLaunchDistance = value,
                "strikeLaunchDistance", "strikeLaunch");
        v3Double("v3.strikeApproachRange", "Max distance a melee/mixed Strike attack may start its fly-in from (4-128)",
                () -> V3Config.get().strikeApproachRange(), (edit, value) -> edit.strikeApproachRange = value,
                "strikeApproachRange");
        v3Bool("v3.strikeCinematicCamera", "Wide cinematic camera on Strike attacks (true/false)",
                () -> V3Config.get().strikeCinematicCamera(), (edit, value) -> edit.strikeCinematicCamera = value,
                "strikeCamera");
        v3Bool("v3.dashCamera", "Legacy setting; Dragon Dash uses the normal gameplay camera",
                () -> V3Config.get().dashCamera(), (edit, value) -> edit.dashCamera = value, "dragonDashCamera");
        v3Bool("v3.attackSounds", "DragonMineZ hit/swing sounds on V3 heavy and charged attacks (true/false)",
                () -> V3Config.get().attackSounds(), (edit, value) -> edit.attackSounds = value, "v3Sounds");
        v3Double("v3.attackSoundVolume", "V3 attack sound volume multiplier (0-2; 0 mutes)",
                () -> V3Config.get().attackSoundVolume(), (edit, value) -> edit.attackSoundVolume = value,
                "v3SoundVolume");
        v3Bool("v3.strikeRequireLock",
                "Melee/mixed Strike requires an approved lock-on (true/false; default true; false = look-aim)",
                () -> V3Config.get().strikeRequireLock(), (edit, value) -> edit.strikeRequireLock = value,
                "strikeLock");
        v3Bool("v3.strikeKiRequireLock", "Pure Strike Ki requires an approved lock-on (true/false; default false)",
                () -> V3Config.get().strikeKiRequireLock(), (edit, value) -> edit.strikeKiRequireLock = value,
                "strikeKiLock");
        v3Double("v3.strikeKiRange", "Max look-aim range for lockless pure Strike Ki (4-128)",
                () -> V3Config.get().strikeKiRange(), (edit, value) -> edit.strikeKiRange = value,
                "strikeKiRange");
        v3Double("v3.grabThrowDistance", "Grab throw launch distance in blocks (4-64; default 12)",
                () -> V3Config.get().grabThrowDistance(), (edit, value) -> edit.grabThrowDistance = value,
                "grabThrow");
        v3Int("v3.strikeCameraHoldTicks",
                "Post-END Strike cinematic + freeze ticks after the technique finishes (0-400; default 80 ~4s)",
                () -> V3Config.get().strikeCameraHoldTicks(), (edit, value) -> edit.strikeCameraHoldTicks = value,
                "strikeCamHold");
        v3Int("v3.strikeCinematicCameraHoldTicks",
                "Opening close-camera hold for ki/charge Strikes (0-400; 0=authored; skipped on rush/APPROACH)",
                () -> V3Config.get().strikeCinematicCameraHoldTicks(),
                (edit, value) -> edit.strikeCinematicCameraHoldTicks = value,
                "strikeCinematicCamHold");
        partyInt("party.idleExpirySeconds", "Disband after this many idle seconds (0 = never)",
                () -> XenoPartyConfig.idleExpirySeconds,
                v -> XenoPartyConfig.idleExpirySeconds = Math.max(0, v),
                "party.idle", "party.expiry");
        partyInt("party.maxMembers", "Xeno invite / HUD party size cap (2-16)",
                () -> XenoPartyConfig.maxMembers,
                v -> XenoPartyConfig.maxMembers = Math.max(2, Math.min(16, v)),
                "party.max");
        partyBool("party.friendlyFireDefault", "Default PvP for newly created parties",
                () -> XenoPartyConfig.friendlyFireDefault,
                v -> XenoPartyConfig.friendlyFireDefault = v,
                "party.ff", "party.pvpdefault");
        partyBool("party.questShareDefault", "Default CNPC @dp quest sharing for new parties",
                () -> XenoPartyConfig.questShareDefault,
                v -> XenoPartyConfig.questShareDefault = v,
                "party.questshare");
        partyInt("party.pingDurationTicks", "How long a party ping marker lasts (20-1200)",
                () -> XenoPartyConfig.pingDurationTicks,
                v -> XenoPartyConfig.pingDurationTicks = Math.max(20, Math.min(1200, v)),
                "party.pingticks");
        partyFlt("party.pingRange", "Max /xenoparty ping distance (8-512)",
                () -> (float) XenoPartyConfig.pingRange,
                v -> XenoPartyConfig.pingRange = Math.max(8.0, Math.min(512.0, v)),
                "party.ping");

        lockBool("lockon.enabled", "Master lock-on switch",
                () -> LockOnConfig.enabled, v -> LockOnConfig.enabled = v);
        lockFlt("lockon.maxLockRangeBlocks", "Max lock distance",
                () -> (float) LockOnConfig.maxLockRangeBlocks,
                v -> LockOnConfig.maxLockRangeBlocks = Math.max(1.0, v),
                "lockon.range");
        lockFlt("lockon.minLockRangeBlocks", "Min lock distance",
                () -> (float) LockOnConfig.minLockRangeBlocks,
                v -> LockOnConfig.minLockRangeBlocks = Math.max(0.0, v),
                "lockon.minrange");
        lockFlt("lockon.hardLockConeDeg", "Hard lock cone half-angle",
                () -> (float) LockOnConfig.hardLockConeDeg,
                v -> LockOnConfig.hardLockConeDeg = Math.max(0.5, Math.min(90.0, v)),
                "lockon.hardcone");
        lockFlt("lockon.softLockConeDeg", "Soft lock cone half-angle",
                () -> (float) LockOnConfig.softLockConeDeg,
                v -> LockOnConfig.softLockConeDeg = Math.max(0.5, Math.min(90.0, v)),
                "lockon.softcone");
        lockFlt("lockon.lockTimeSeconds", "Seconds to acquire a lock",
                () -> (float) LockOnConfig.lockTimeSeconds,
                v -> LockOnConfig.lockTimeSeconds = Math.max(0.0, v),
                "lockon.locktime");
        lockFlt("lockon.graceSeconds", "Seconds a lock survives invalid conditions",
                () -> (float) LockOnConfig.graceSeconds,
                v -> LockOnConfig.graceSeconds = Math.max(0.0, v),
                "lockon.grace");
        lockBool("lockon.requireLineOfSight", "Require line of sight to lock",
                () -> LockOnConfig.requireLineOfSight, v -> LockOnConfig.requireLineOfSight = v,
                "lockon.los");
        lockBool("lockon.lockCreativePlayers", "Allow locking creative players",
                () -> LockOnConfig.lockCreativePlayers, v -> LockOnConfig.lockCreativePlayers = v,
                "lockon.creative");
        lockBool("lockon.lockInvulnerablePlayers", "Allow locking invulnerable players",
                () -> LockOnConfig.lockInvulnerablePlayers,
                v -> LockOnConfig.lockInvulnerablePlayers = v,
                "lockon.invuln");
        lockBool("lockon.lockSpectators", "Allow locking spectators",
                () -> LockOnConfig.lockSpectators, v -> LockOnConfig.lockSpectators = v,
                "lockon.spectators");
        lockBool("lockon.respectPartyFriendlyFire",
                "Block locking party members when that party's PvP is off",
                () -> LockOnConfig.respectPartyFriendlyFire,
                v -> LockOnConfig.respectPartyFriendlyFire = v,
                "lockon.partyff");
        lockInt("lockon.requestRateLimitTicks", "Min ticks between lock requests",
                () -> LockOnConfig.requestRateLimitTicks,
                v -> LockOnConfig.requestRateLimitTicks = Math.max(1, v),
                "lockon.ratelimit");
        lockInt("lockon.reacquireCooldownTicks", "Ticks after a break before a new lock",
                () -> LockOnConfig.reacquireCooldownTicks,
                v -> LockOnConfig.reacquireCooldownTicks = Math.max(0, v),
                "lockon.reacquire");
        lockInt("lockon.cycleRateLimitTicks", "Min ticks between target-cycle requests",
                () -> LockOnConfig.cycleRateLimitTicks,
                v -> LockOnConfig.cycleRateLimitTicks = Math.max(1, v),
                "lockon.cycle");
        lockBool("lockon.sendVelocityForLead", "Include target velocity for lead prediction",
                () -> LockOnConfig.sendVelocityForLead, v -> LockOnConfig.sendVelocityForLead = v,
                "lockon.lead");

        perfBool("perf.perfEnabled", "Master perf module switch",
                () -> XenoPerfConfig.perfEnabled, v -> XenoPerfConfig.perfEnabled = v,
                "perf.enabled", "perf.perf");
        perfBool("perf.forceChunksEnabled", "Missile / VLS chunk force-load",
                () -> XenoPerfConfig.forceChunksEnabled, v -> XenoPerfConfig.forceChunksEnabled = v,
                "perf.forcechunks");
        perfBool("perf.forceChunksTargetOnly", "Force-load only the target chunk",
                () -> XenoPerfConfig.forceChunksTargetOnly,
                v -> XenoPerfConfig.forceChunksTargetOnly = v,
                "perf.targetonly");
        perfInt("perf.forceChunksRadius", "Force-load chunk radius (0-2)",
                () -> XenoPerfConfig.forceChunksRadius,
                v -> XenoPerfConfig.forceChunksRadius = Math.max(0, Math.min(2, v)),
                "perf.radius");
        perfInt("perf.forceChunksDurationTicks", "Force-load duration ticks",
                () -> XenoPerfConfig.forceChunksDurationTicks,
                v -> XenoPerfConfig.forceChunksDurationTicks = v,
                "perf.duration");
        perfFlt("perf.forceChunksPlayerRange", "Force-load only when a player is this close",
                () -> (float) XenoPerfConfig.forceChunksPlayerRange,
                v -> XenoPerfConfig.forceChunksPlayerRange = Math.max(0.0, v),
                "perf.playerange");
        perfInt("perf.ballisticMaxRangeBlocks", "Ballistic guidance range (0 = unlimited)",
                () -> XenoPerfConfig.ballisticMaxRangeBlocks,
                v -> XenoPerfConfig.ballisticMaxRangeBlocks = v,
                "perf.maxrange");
        perfBool("perf.thrusterPhysForceEnabled", "Thruster applyModelForce",
                () -> XenoPerfConfig.thrusterPhysForceEnabled,
                v -> XenoPerfConfig.thrusterPhysForceEnabled = v,
                "perf.thrforce");
        perfBool("perf.thrusterForceAlways", "Thruster force even with no nearby player",
                () -> XenoPerfConfig.thrusterForceAlways, v -> XenoPerfConfig.thrusterForceAlways = v,
                "perf.thralways");
        perfFlt("perf.thrusterForcePlayerRange", "Thruster force player range",
                () -> (float) XenoPerfConfig.thrusterForcePlayerRange,
                v -> XenoPerfConfig.thrusterForcePlayerRange = v,
                "perf.thrrange");
        perfInt("perf.statsSyncIntervalTicks", "Stats HUD sync interval",
                () -> XenoPerfConfig.statsSyncIntervalTicks,
                v -> XenoPerfConfig.statsSyncIntervalTicks = v,
                "perf.statsync");
        perfInt("perf.statsSyncHeartbeatTicks", "Stats HUD heartbeat",
                () -> XenoPerfConfig.statsSyncHeartbeatTicks,
                v -> XenoPerfConfig.statsSyncHeartbeatTicks = v,
                "perf.statsheartbeat");
        perfBool("perf.statsSyncOnlyWhenDirty", "Skip clean stats syncs",
                () -> XenoPerfConfig.statsSyncOnlyWhenDirty,
                v -> XenoPerfConfig.statsSyncOnlyWhenDirty = v,
                "perf.statsdirty");
        perfBool("perf.missileFlightTickets", "Force-load live missile corridor",
                () -> XenoPerfConfig.missileFlightTickets,
                v -> XenoPerfConfig.missileFlightTickets = v,
                "perf.missiletickets");
        perfBool("perf.sableContraptionCullEnabled", "Clamp Sable+Create collideEntities queries",
                () -> XenoPerfConfig.sableContraptionCullEnabled,
                v -> XenoPerfConfig.sableContraptionCullEnabled = v,
                "perf.sablecull");
        perfFlt("perf.sableContraptionMaxQueryExtent", "Sable query half-extent (16-256)",
                () -> (float) XenoPerfConfig.sableContraptionMaxQueryExtent,
                v -> XenoPerfConfig.sableContraptionMaxQueryExtent = v,
                "perf.sableextent");
    }

    private XenoConfigRegistry() {}

    public static Entry resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String n = raw.trim().toLowerCase(Locale.ROOT);
        String id = ALIAS.getOrDefault(n, n);
        Entry extra = BY_ID.get(id);
        if (extra != null) return extra;
        for (Entry entry : BY_ID.values()) {
            if (entry.id.equalsIgnoreCase(n)) return entry;
        }
        return null;
    }

    public static Result set(String rawKey, String rawValue) {
        Entry extra = resolve(rawKey);
        if (extra != null) {
            if (rawValue == null || rawValue.isBlank()) {
                return Result.fail("Missing value for " + extra.id);
            }
            String value = rawValue.trim();
            try {
                switch (extra.kind) {
                    case BOOL -> {
                        Boolean parsed = parseBool(value);
                        if (parsed == null) {
                            return Result.fail("Expected true/false for " + extra.id);
                        }
                        extra.applyRaw.accept(parsed ? "true" : "false");
                    }
                    case INT -> extra.applyRaw.accept(Integer.toString(parseIntOrHex(value)));
                    case FLOAT -> {
                        float parsed = Float.parseFloat(value);
                        if (!Float.isFinite(parsed)) {
                            return Result.fail("Value must be finite for " + extra.id);
                        }
                        extra.applyRaw.accept(Float.toString(parsed));
                    }
                    case DOUBLE -> {
                        double parsed = Double.parseDouble(value);
                        if (!Double.isFinite(parsed)) return Result.fail("Value must be finite for " + extra.id);
                        extra.applyRaw.accept(Double.toString(parsed));
                    }
                }
            } catch (NumberFormatException e) {
                return Result.fail("Bad " + extra.kind.name().toLowerCase(Locale.ROOT)
                        + " for " + extra.id + ": " + value);
            }
            if (extra.store == Store.PERF) {
                XenoPerfConfig.apply(XenoPerfConfig.snapshot());
            }
            return Result.ok(extra.id + " = " + extra.get(), extra.store);
        }
        XenoServerConfigKeys.Result combat = XenoServerConfigKeys.set(rawKey, rawValue);
        if (!combat.ok) {
            return Result.fail(combat.message);
        }
        return Result.ok(combat.message, Store.COMBAT);
    }

    public static Result get(String rawKey) {
        Entry extra = resolve(rawKey);
        if (extra != null) {
            return Result.ok(extra.id + " = " + extra.get() + "  (" + extra.help + ")", extra.store);
        }
        XenoServerConfigKeys.Result combat = XenoServerConfigKeys.get(rawKey);
        if (!combat.ok) {
            return Result.fail(combat.message);
        }
        return Result.ok(combat.message, Store.COMBAT);
    }

    public static List<String> suggest(String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        for (Entry entry : BY_ID.values()) {
            if (entry.id.toLowerCase(Locale.ROOT).startsWith(p)) unique.add(entry.id);
        }
        for (Map.Entry<String, String> alias : ALIAS.entrySet()) {
            if (!alias.getKey().startsWith(p)) continue;
            Entry entry = BY_ID.get(alias.getValue());
            if (entry != null) unique.add(entry.id);
        }
        unique.addAll(XenoServerConfigKeys.suggest(prefix));
        List<String> out = new ArrayList<>(unique);
        Collections.sort(out);
        return out;
    }

    public static List<Entry> extras() {
        return List.copyOf(BY_ID.values());
    }

    public static boolean isHudPrefix(String prefix) {
        if (prefix == null) return false;
        String p = prefix.trim().toLowerCase(Locale.ROOT);
        return p.equals("hud") || p.startsWith("hud.") || p.equals("partyhud")
                || p.equals("xenohud") || p.equals("xenoclient");
    }

    public static String hudOutOfScope() {
        return "HUD / party-HUD layout is per-client. Use /xenohud or /xenoclient on that machine; "
                + "a dedicated server cannot write another player's xenopixelsmod-party-hud.json.";
    }

    private static void partyBool(String id, String help, Supplier<Boolean> get, Consumer<Boolean> set,
                                  String... aliases) {
        register(id, Store.PARTY, Kind.BOOL, help,
                () -> Boolean.toString(get.get()),
                raw -> set.accept(Boolean.parseBoolean(raw)), aliases);
    }

    private static void partyInt(String id, String help, Supplier<Integer> get, Consumer<Integer> set,
                                 String... aliases) {
        register(id, Store.PARTY, Kind.INT, help,
                () -> Integer.toString(get.get()),
                raw -> set.accept(Integer.parseInt(raw)), aliases);
    }

    private static void partyFlt(String id, String help, Supplier<Float> get, Consumer<Double> set,
                                 String... aliases) {
        register(id, Store.PARTY, Kind.FLOAT, help,
                () -> formatFloat(get.get()),
                raw -> set.accept((double) Float.parseFloat(raw)), aliases);
    }

    private static void lockBool(String id, String help, Supplier<Boolean> get, Consumer<Boolean> set,
                                 String... aliases) {
        register(id, Store.LOCKON, Kind.BOOL, help,
                () -> Boolean.toString(get.get()),
                raw -> set.accept(Boolean.parseBoolean(raw)), aliases);
    }

    private static void lockInt(String id, String help, Supplier<Integer> get, Consumer<Integer> set,
                                String... aliases) {
        register(id, Store.LOCKON, Kind.INT, help,
                () -> Integer.toString(get.get()),
                raw -> set.accept(Integer.parseInt(raw)), aliases);
    }

    private static void lockFlt(String id, String help, Supplier<Float> get, Consumer<Double> set,
                                String... aliases) {
        register(id, Store.LOCKON, Kind.FLOAT, help,
                () -> formatFloat(get.get()),
                raw -> set.accept((double) Float.parseFloat(raw)), aliases);
    }

    private static void perfBool(String id, String help, Supplier<Boolean> get, Consumer<Boolean> set,
                                 String... aliases) {
        register(id, Store.PERF, Kind.BOOL, help,
                () -> Boolean.toString(get.get()),
                raw -> set.accept(Boolean.parseBoolean(raw)), aliases);
    }

    private static void perfInt(String id, String help, Supplier<Integer> get, Consumer<Integer> set,
                                String... aliases) {
        register(id, Store.PERF, Kind.INT, help,
                () -> Integer.toString(get.get()),
                raw -> set.accept(Integer.parseInt(raw)), aliases);
    }

    private static void perfFlt(String id, String help, Supplier<Float> get, Consumer<Double> set,
                                String... aliases) {
        register(id, Store.PERF, Kind.FLOAT, help,
                () -> formatFloat(get.get()),
                raw -> set.accept((double) Float.parseFloat(raw)), aliases);
    }

    private interface V3DoubleEdit { void apply(V3Config.Values.Edit edit, double value); }
    private interface V3IntEdit { void apply(V3Config.Values.Edit edit, int value); }
    private interface V3BoolEdit { void apply(V3Config.Values.Edit edit, boolean value); }

    private static void v3Double(String id, String help, Supplier<Double> get, V3DoubleEdit set, String... aliases) {
        register(id, Store.V3, Kind.DOUBLE, help, () -> formatDouble(get.get()), raw -> {
            var edit = new V3Config.Values.Edit(V3Config.get());
            set.apply(edit, Double.parseDouble(raw));
            V3Config.apply(edit.build());
        }, aliases);
    }

    private static void v3Int(String id, String help, Supplier<Integer> get, V3IntEdit set, String... aliases) {
        register(id, Store.V3, Kind.INT, help, () -> Integer.toString(get.get()), raw -> {
            var edit = new V3Config.Values.Edit(V3Config.get());
            set.apply(edit, Integer.parseInt(raw.trim()));
            V3Config.apply(edit.build());
        }, aliases);
    }

    private static void v3Bool(String id, String help, Supplier<Boolean> get, V3BoolEdit set, String... aliases) {
        register(id, Store.V3, Kind.BOOL, help, () -> Boolean.toString(get.get()), raw -> {
            Boolean value = parseBool(raw.trim());
            if (value == null) throw new IllegalArgumentException("Expected true/false");
            var edit = new V3Config.Values.Edit(V3Config.get());
            set.apply(edit, value);
            V3Config.apply(edit.build());
        }, aliases);
    }

    private static void register(String id, Store store, Kind kind, String help,
                                 Supplier<String> getter, Consumer<String> applyRaw,
                                 String... aliases) {
        Entry entry = new Entry(id, store, kind, help, getter, applyRaw);
        BY_ID.put(id, entry);
        ALIAS.put(id.toLowerCase(Locale.ROOT), id);
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) continue;
            ALIAS.put(alias.toLowerCase(Locale.ROOT), id);
        }
    }

    private static Boolean parseBool(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1" -> Boolean.TRUE;
            case "false", "off", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static int parseIntOrHex(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("0x") || trimmed.startsWith("0X") || trimmed.startsWith("#")) {
            return Integer.decode(trimmed);
        }
        return Integer.parseInt(trimmed);
    }

    private static String formatFloat(float value) {
        if (value == (int) value) return Integer.toString((int) value);
        return Float.toString(value);
    }

    private static String formatDouble(double value) {
        return value == (long) value ? Long.toString((long) value) : Double.toString(value);
    }
}
