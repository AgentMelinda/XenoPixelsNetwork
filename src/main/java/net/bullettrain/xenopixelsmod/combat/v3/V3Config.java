package net.bullettrain.xenopixelsmod.combat.v3;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Server-owned V3 settings. Saves preserve unknown top-level addon data.
 *
 * <p>Every key is server authority: the client receives a read-only snapshot through
 * {@code CombatV3ConfigPacket} for presentation only. Client-only settings (music, HD visuals)
 * live in {@code XenoClientConfig} and are not here.
 */
public final class V3Config {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Values values = new Values();
    private V3Config() {}
    public static Values get() { return values; }
    public static void apply(Values next) { values = next == null ? new Values() : next; }
    private static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-combat-v3.json");
    }
    public static void load() {
        try { load(path()); }
        catch (Exception exception) {
            XenoPixelsMod.LOGGER.warn("Failed to load combat v3 config; retaining effective values and file", exception);
        }
    }
    public static void save() {
        try { save(path()); }
        catch (Exception exception) {
            XenoPixelsMod.LOGGER.warn("Failed to save combat v3 config; preserving original file", exception);
        }
    }
    /** Command persistence must propagate refusal instead of claiming a successful save. */
    public static void persist() throws IOException { persist(path()); }
    public static void persist(Path path) throws IOException { save(path); }
    static void load(Path path) throws IOException {
        JsonObject json = readObject(path);
        checkVersion(json);
        Values defaults = new Values();
        Values loaded = new Values(number(json, "dragonDashRange", defaults.dragonDashRange()),
                number(json, "heavyAttackerStaminaCost", defaults.heavyAttackerStaminaCost()),
                number(json, "heavyVictimStaminaDrain", defaults.heavyVictimStaminaDrain()),
                number(json, "dragonDashSpeed", defaults.dragonDashSpeed()),
                number(json, "dragonDashLaunchDistance", defaults.dragonDashLaunchDistance()),
                number(json, "dragonDashFollowDistance", defaults.dragonDashFollowDistance()),
                (int) number(json, "dragonDashFollowCooldownTicks", defaults.dragonDashFollowCooldownTicks()),
                (int) number(json, "dragonDashFollowWindowTicks", defaults.dragonDashFollowWindowTicks()),
                number(json, "strikeLaunchDistance", defaults.strikeLaunchDistance()),
                number(json, "strikeApproachRange", defaults.strikeApproachRange()),
                bool(json, "strikeCinematicCamera", defaults.strikeCinematicCamera()),
                bool(json, "dashCamera", defaults.dashCamera()),
                bool(json, "attackSounds", defaults.attackSounds()),
                number(json, "heavyChargeLaunchDistance", defaults.heavyChargeLaunchDistance()),
                number(json, "attackSoundVolume", defaults.attackSoundVolume()),
                bool(json, "strikeKiRequireLock", defaults.strikeKiRequireLock()),
                number(json, "strikeKiRange", defaults.strikeKiRange()),
                number(json, "grabThrowDistance", defaults.grabThrowDistance()),
                (int) number(json, "strikeCameraHoldTicks", defaults.strikeCameraHoldTicks()),
                (int) number(json, "strikeCinematicCameraHoldTicks",
                        defaults.strikeCinematicCameraHoldTicks()),
                bool(json, "strikeRequireLock", defaults.strikeRequireLock()));
        apply(loaded);
        save(path);
    }
    static void save(Path path) throws IOException {
        JsonObject json = readObject(path);
        checkVersion(json);
        Values snapshot = get();
        json.addProperty("version", VERSION);
        json.addProperty("dragonDashRange", snapshot.dragonDashRange());
        json.addProperty("heavyAttackerStaminaCost", snapshot.heavyAttackerStaminaCost());
        json.addProperty("heavyVictimStaminaDrain", snapshot.heavyVictimStaminaDrain());
        json.addProperty("dragonDashSpeed", snapshot.dragonDashSpeed());
        json.addProperty("dragonDashLaunchDistance", snapshot.dragonDashLaunchDistance());
        json.addProperty("dragonDashFollowDistance", snapshot.dragonDashFollowDistance());
        json.addProperty("dragonDashFollowCooldownTicks", snapshot.dragonDashFollowCooldownTicks());
        json.addProperty("dragonDashFollowWindowTicks", snapshot.dragonDashFollowWindowTicks());
        json.addProperty("strikeLaunchDistance", snapshot.strikeLaunchDistance());
        json.addProperty("strikeApproachRange", snapshot.strikeApproachRange());
        json.addProperty("strikeCinematicCamera", snapshot.strikeCinematicCamera());
        json.addProperty("dashCamera", snapshot.dashCamera());
        json.addProperty("attackSounds", snapshot.attackSounds());
        json.addProperty("heavyChargeLaunchDistance", snapshot.heavyChargeLaunchDistance());
        json.addProperty("attackSoundVolume", snapshot.attackSoundVolume());
        json.addProperty("strikeKiRequireLock", snapshot.strikeKiRequireLock());
        json.addProperty("strikeKiRange", snapshot.strikeKiRange());
        json.addProperty("grabThrowDistance", snapshot.grabThrowDistance());
        // Post-END freeze hold (original key) and opening close-camera hold (separate).
        json.addProperty("strikeCameraHoldTicks", snapshot.strikeCameraHoldTicks());
        json.addProperty("strikeCinematicCameraHoldTicks", snapshot.strikeCinematicCameraHoldTicks());
        json.addProperty("strikeRequireLock", snapshot.strikeRequireLock());
        Path absolute = path.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), absolute.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(json));
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void checkVersion(JsonObject json) {
        if (!json.has("version")) return;
        var version = json.get("version");
        if (!version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Unsupported combat v3 config version");
        }
        try {
            if (new BigDecimal(version.getAsString()).intValueExact() != VERSION) {
                throw new IllegalArgumentException("Unsupported combat v3 config version");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Unsupported combat v3 config version", exception);
        }
    }
    private static JsonObject readObject(Path path) throws IOException {
        return Files.exists(path) ? JsonParser.parseString(Files.readString(path)).getAsJsonObject() : new JsonObject();
    }
    private static double number(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }
    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    /**
     * Immutable normalized snapshot; zero independently disables either heavy stamina cost.
     *
     * <p>Distances are blocks, timings are server ticks. Every value is clamped to its documented
     * bound, so a command or file value outside it becomes the nearest effective setting.
     */
    public record Values(double dragonDashRange, double heavyAttackerStaminaCost, double heavyVictimStaminaDrain,
                         double dragonDashSpeed, double dragonDashLaunchDistance, double dragonDashFollowDistance,
                         int dragonDashFollowCooldownTicks, int dragonDashFollowWindowTicks,
                         double strikeLaunchDistance, double strikeApproachRange,
                         boolean strikeCinematicCamera, boolean dashCamera, boolean attackSounds,
                         double heavyChargeLaunchDistance, double attackSoundVolume,
                         boolean strikeKiRequireLock, double strikeKiRange, double grabThrowDistance,
                         int strikeCameraHoldTicks, int strikeCinematicCameraHoldTicks,
                         boolean strikeRequireLock) {
        public static final double DEFAULT_DASH_RANGE = 24;
        /** Melee/mixed Strike needs approved lock when true; false allows look-aim without lock. */
        public static final boolean DEFAULT_STRIKE_REQUIRE_LOCK = true;
        /** Owner 2026-10-08: the held right-click punch launches the target this far and pops Dragon Dash. */
        public static final double DEFAULT_HEAVY_CHARGE_LAUNCH = 15;
        public static final double DEFAULT_DASH_SPEED = 3.0;
        public static final double DEFAULT_DASH_LAUNCH = 20;
        public static final double DEFAULT_DASH_FOLLOW_DISTANCE = 2.5;
        public static final int DEFAULT_DASH_FOLLOW_COOLDOWN = 8;
        public static final int DEFAULT_DASH_FOLLOW_WINDOW = 60;
        /** Owner: Dodoria Head Breaker / Strike shove travel distance (blocks). */
        public static final double DEFAULT_STRIKE_LAUNCH = 20;
        public static final double DEFAULT_STRIKE_APPROACH = 128;
        public static final double DEFAULT_STRIKE_KI_RANGE = 128;
        /** Owner: grab throw travels farther than the old 1.3 impulse; default 12 blocks. */
        public static final double DEFAULT_GRAB_THROW_DISTANCE = 12;
        /** Post-END cinematic + victim freeze (~4s). Separate from the opening close-angle hold. */
        public static final int DEFAULT_STRIKE_CAMERA_HOLD = 80;
        /**
         * Opening close-camera lengthen for ki/charge openers (0 = authored beat length).
         * Not applied to APPROACH/rush timelines — those were thrashing with lock-on.
         */
        public static final int DEFAULT_STRIKE_CINEMATIC_OPENING_HOLD = 0;

        public Values() { this(DEFAULT_DASH_RANGE, 10, 10); }
        /** Existing snapshots retain prior grab-throw default and zero opening hold. */
        public Values(double dragonDashRange, double heavyAttackerStaminaCost, double heavyVictimStaminaDrain,
                      double dragonDashSpeed, double dragonDashLaunchDistance, double dragonDashFollowDistance,
                      int dragonDashFollowCooldownTicks, int dragonDashFollowWindowTicks,
                      double strikeLaunchDistance, double strikeApproachRange,
                      boolean strikeCinematicCamera, boolean dashCamera, boolean attackSounds,
                      double heavyChargeLaunchDistance, double attackSoundVolume,
                      boolean strikeKiRequireLock, double strikeKiRange) {
            this(dragonDashRange, heavyAttackerStaminaCost, heavyVictimStaminaDrain, dragonDashSpeed,
                    dragonDashLaunchDistance, dragonDashFollowDistance, dragonDashFollowCooldownTicks,
                    dragonDashFollowWindowTicks, strikeLaunchDistance, strikeApproachRange,
                    strikeCinematicCamera, dashCamera, attackSounds, heavyChargeLaunchDistance, attackSoundVolume,
                    strikeKiRequireLock, strikeKiRange, DEFAULT_GRAB_THROW_DISTANCE, DEFAULT_STRIKE_CAMERA_HOLD,
                    DEFAULT_STRIKE_CINEMATIC_OPENING_HOLD, DEFAULT_STRIKE_REQUIRE_LOCK);
        }
        /** Prior full form before opening-hold was split out. */
        public Values(double dragonDashRange, double heavyAttackerStaminaCost, double heavyVictimStaminaDrain,
                      double dragonDashSpeed, double dragonDashLaunchDistance, double dragonDashFollowDistance,
                      int dragonDashFollowCooldownTicks, int dragonDashFollowWindowTicks,
                      double strikeLaunchDistance, double strikeApproachRange,
                      boolean strikeCinematicCamera, boolean dashCamera, boolean attackSounds,
                      double heavyChargeLaunchDistance, double attackSoundVolume,
                      boolean strikeKiRequireLock, double strikeKiRange, double grabThrowDistance,
                      int strikeCameraHoldTicks) {
            this(dragonDashRange, heavyAttackerStaminaCost, heavyVictimStaminaDrain, dragonDashSpeed,
                    dragonDashLaunchDistance, dragonDashFollowDistance, dragonDashFollowCooldownTicks,
                    dragonDashFollowWindowTicks, strikeLaunchDistance, strikeApproachRange,
                    strikeCinematicCamera, dashCamera, attackSounds, heavyChargeLaunchDistance, attackSoundVolume,
                    strikeKiRequireLock, strikeKiRange, grabThrowDistance, strikeCameraHoldTicks,
                    DEFAULT_STRIKE_CINEMATIC_OPENING_HOLD, DEFAULT_STRIKE_REQUIRE_LOCK);
        }
        /** Prior form before strikeRequireLock was split out. */
        public Values(double dragonDashRange, double heavyAttackerStaminaCost, double heavyVictimStaminaDrain,
                      double dragonDashSpeed, double dragonDashLaunchDistance, double dragonDashFollowDistance,
                      int dragonDashFollowCooldownTicks, int dragonDashFollowWindowTicks,
                      double strikeLaunchDistance, double strikeApproachRange,
                      boolean strikeCinematicCamera, boolean dashCamera, boolean attackSounds,
                      double heavyChargeLaunchDistance, double attackSoundVolume,
                      boolean strikeKiRequireLock, double strikeKiRange, double grabThrowDistance,
                      int strikeCameraHoldTicks, int strikeCinematicCameraHoldTicks) {
            this(dragonDashRange, heavyAttackerStaminaCost, heavyVictimStaminaDrain, dragonDashSpeed,
                    dragonDashLaunchDistance, dragonDashFollowDistance, dragonDashFollowCooldownTicks,
                    dragonDashFollowWindowTicks, strikeLaunchDistance, strikeApproachRange,
                    strikeCinematicCamera, dashCamera, attackSounds, heavyChargeLaunchDistance, attackSoundVolume,
                    strikeKiRequireLock, strikeKiRange, grabThrowDistance, strikeCameraHoldTicks,
                    strikeCinematicCameraHoldTicks, DEFAULT_STRIKE_REQUIRE_LOCK);
        }
        /** Existing snapshots retain their original volume and lockless-Ki defaults. */
        public Values(double dragonDashRange, double heavyAttackerStaminaCost, double heavyVictimStaminaDrain,
                      double dragonDashSpeed, double dragonDashLaunchDistance, double dragonDashFollowDistance,
                      int dragonDashFollowCooldownTicks, int dragonDashFollowWindowTicks,
                      double strikeLaunchDistance, double strikeApproachRange,
                      boolean strikeCinematicCamera, boolean dashCamera, boolean attackSounds,
                      double heavyChargeLaunchDistance, double attackSoundVolume) {
            this(dragonDashRange, heavyAttackerStaminaCost, heavyVictimStaminaDrain, dragonDashSpeed,
                    dragonDashLaunchDistance, dragonDashFollowDistance, dragonDashFollowCooldownTicks,
                    dragonDashFollowWindowTicks, strikeLaunchDistance, strikeApproachRange,
                    strikeCinematicCamera, dashCamera, attackSounds, heavyChargeLaunchDistance, attackSoundVolume,
                    false, DEFAULT_STRIKE_KI_RANGE);
        }
        /** Existing snapshots retain their original volume. */
        public Values(double dragonDashRange, double heavyAttackerStaminaCost, double heavyVictimStaminaDrain,
                      double dragonDashSpeed, double dragonDashLaunchDistance, double dragonDashFollowDistance,
                      int dragonDashFollowCooldownTicks, int dragonDashFollowWindowTicks,
                      double strikeLaunchDistance, double strikeApproachRange,
                      boolean strikeCinematicCamera, boolean dashCamera, boolean attackSounds,
                      double heavyChargeLaunchDistance) {
            this(dragonDashRange, heavyAttackerStaminaCost, heavyVictimStaminaDrain, dragonDashSpeed,
                    dragonDashLaunchDistance, dragonDashFollowDistance, dragonDashFollowCooldownTicks,
                    dragonDashFollowWindowTicks, strikeLaunchDistance, strikeApproachRange,
                    strikeCinematicCamera, dashCamera, attackSounds, heavyChargeLaunchDistance, 1);
        }
        /** The original three-field snapshot; everything else keeps its default. */
        public Values(double dragonDashRange, double heavyAttackerStaminaCost, double heavyVictimStaminaDrain) {
            this(dragonDashRange, heavyAttackerStaminaCost, heavyVictimStaminaDrain, DEFAULT_DASH_SPEED,
                    DEFAULT_DASH_LAUNCH, DEFAULT_DASH_FOLLOW_DISTANCE, DEFAULT_DASH_FOLLOW_COOLDOWN,
                    DEFAULT_DASH_FOLLOW_WINDOW, DEFAULT_STRIKE_LAUNCH, DEFAULT_STRIKE_APPROACH, true, false, true,
                    DEFAULT_HEAVY_CHARGE_LAUNCH);
        }
        /** The 2026-10-08 thirteen-field form, before the heavy-charge launch distance was appended. */
        public Values(double dragonDashRange, double heavyAttackerStaminaCost, double heavyVictimStaminaDrain,
                      double dragonDashSpeed, double dragonDashLaunchDistance, double dragonDashFollowDistance,
                      int dragonDashFollowCooldownTicks, int dragonDashFollowWindowTicks,
                      double strikeLaunchDistance, double strikeApproachRange,
                      boolean strikeCinematicCamera, boolean dashCamera, boolean attackSounds) {
            this(dragonDashRange, heavyAttackerStaminaCost, heavyVictimStaminaDrain, dragonDashSpeed,
                    dragonDashLaunchDistance, dragonDashFollowDistance, dragonDashFollowCooldownTicks,
                    dragonDashFollowWindowTicks, strikeLaunchDistance, strikeApproachRange,
                    strikeCinematicCamera, dashCamera, attackSounds, DEFAULT_HEAVY_CHARGE_LAUNCH);
        }
        public Values {
            dragonDashRange = bounded(dragonDashRange, 2, 999);
            heavyAttackerStaminaCost = bounded(heavyAttackerStaminaCost, 0, 100000);
            heavyVictimStaminaDrain = bounded(heavyVictimStaminaDrain, 0, 100000);
            dragonDashSpeed = bounded(dragonDashSpeed, 0.5, 12);
            dragonDashLaunchDistance = bounded(dragonDashLaunchDistance, 4, 64);
            dragonDashFollowDistance = bounded(dragonDashFollowDistance, 1.5, 8);
            dragonDashFollowCooldownTicks = (int) bounded(dragonDashFollowCooldownTicks, 0, 200);
            dragonDashFollowWindowTicks = (int) bounded(dragonDashFollowWindowTicks, 10, 1200);
            strikeLaunchDistance = bounded(strikeLaunchDistance, 4, 64);
            strikeApproachRange = bounded(strikeApproachRange, 4, 128);
            heavyChargeLaunchDistance = bounded(heavyChargeLaunchDistance, 4, 64);
            attackSoundVolume = bounded(attackSoundVolume, 0, 2);
            strikeKiRange = bounded(strikeKiRange, 4, 128);
            grabThrowDistance = bounded(grabThrowDistance, 4, 64);
            strikeCameraHoldTicks = (int) bounded(strikeCameraHoldTicks, 0, 400);
            strikeCinematicCameraHoldTicks = (int) bounded(strikeCinematicCameraHoldTicks, 0, 400);
        }
        private static double bounded(double value, double min, double max) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("V3 config value must be finite");
            return Math.max(min, Math.min(max, value));
        }

        /** Mutable scratch copy for one-key command edits; {@link #build()} normalizes again. */
        public static final class Edit {
            public double dragonDashRange, heavyAttackerStaminaCost, heavyVictimStaminaDrain, dragonDashSpeed,
                    dragonDashLaunchDistance, dragonDashFollowDistance, strikeLaunchDistance, strikeApproachRange,
                    heavyChargeLaunchDistance, attackSoundVolume, strikeKiRange, grabThrowDistance;
            public int strikeCameraHoldTicks, strikeCinematicCameraHoldTicks;
            public int dragonDashFollowCooldownTicks, dragonDashFollowWindowTicks;
            public boolean strikeCinematicCamera, dashCamera, attackSounds, strikeKiRequireLock, strikeRequireLock;
            public Edit(Values from) {
                dragonDashRange = from.dragonDashRange;
                heavyAttackerStaminaCost = from.heavyAttackerStaminaCost;
                heavyVictimStaminaDrain = from.heavyVictimStaminaDrain;
                dragonDashSpeed = from.dragonDashSpeed;
                dragonDashLaunchDistance = from.dragonDashLaunchDistance;
                dragonDashFollowDistance = from.dragonDashFollowDistance;
                dragonDashFollowCooldownTicks = from.dragonDashFollowCooldownTicks;
                dragonDashFollowWindowTicks = from.dragonDashFollowWindowTicks;
                strikeLaunchDistance = from.strikeLaunchDistance;
                strikeApproachRange = from.strikeApproachRange;
                strikeCinematicCamera = from.strikeCinematicCamera;
                dashCamera = from.dashCamera;
                attackSounds = from.attackSounds;
                heavyChargeLaunchDistance = from.heavyChargeLaunchDistance;
                attackSoundVolume = from.attackSoundVolume;
                strikeKiRequireLock = from.strikeKiRequireLock;
                strikeKiRange = from.strikeKiRange;
                grabThrowDistance = from.grabThrowDistance;
                strikeCameraHoldTicks = from.strikeCameraHoldTicks;
                strikeCinematicCameraHoldTicks = from.strikeCinematicCameraHoldTicks;
                strikeRequireLock = from.strikeRequireLock;
            }
            public Values build() {
                return new Values(dragonDashRange, heavyAttackerStaminaCost, heavyVictimStaminaDrain, dragonDashSpeed,
                        dragonDashLaunchDistance, dragonDashFollowDistance, dragonDashFollowCooldownTicks,
                        dragonDashFollowWindowTicks, strikeLaunchDistance, strikeApproachRange,
                        strikeCinematicCamera, dashCamera, attackSounds, heavyChargeLaunchDistance, attackSoundVolume,
                        strikeKiRequireLock, strikeKiRange, grabThrowDistance, strikeCameraHoldTicks,
                        strikeCinematicCameraHoldTicks, strikeRequireLock);
            }
        }
    }
}
