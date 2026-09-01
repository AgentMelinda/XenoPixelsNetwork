package net.bullettrain.xenopixelsmod.combat.targeting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-authoritative tuning for seat-mounted target lock-on, written to
 * {@code config/xenopixelsmod-lockon.json}. Follows the same light Gson-JSON shape as
 * {@link net.bullettrain.xenopixelsmod.aero.AeroConfig} rather than the heavier
 * {@code XenoServerConfig} Data/snapshot/apply pattern.
 *
 * <p>Every value here is read only by {@link LockOnValidator} and {@link TargetLockManager} on
 * the server. Nothing on the client can widen these limits — a client only ever requests a lock;
 * the server decides whether it is granted.
 */
public final class LockOnConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-lockon.json");

    /** Master switch. Off refuses every lock request outright. */
    public static boolean enabled = true;

    /** Beyond this distance a target cannot be locked, and an existing lock breaks. */
    public static double maxLockRangeBlocks = 400.0;
    /** Below this distance locking is refused — a target must be worth tracking, not adjacent. */
    public static double minLockRangeBlocks = 2.0;

    /** Half-angle, in degrees, of the cone in front of the locker's look direction that can be locked at all. */
    public static double hardLockConeDeg = 4.0;
    /** Half-angle of the wider assist cone: a target already locked stays locked while inside this, even if it drifts out of the hard cone. */
    public static double softLockConeDeg = 12.0;

    /** Seconds of continuous valid conditions needed to go from 0 progress to a full lock. */
    public static double lockTimeSeconds = 1.25;
    /** Seconds a lock survives with conditions invalid (target out of cone / LOS lost) before it breaks. */
    public static double graceSeconds = 1.0;

    public static boolean requireLineOfSight = true;
    public static boolean lockCreativePlayers = false;
    public static boolean lockInvulnerablePlayers = false;
    public static boolean lockSpectators = false;

    /**
     * Whether a party can lock its own members when that party's own PvP/friendly-fire flag is
     * off. Locking a party member you cannot damage would be pointless combat feedback, so this
     * defaults to matching the party's own {@code isPvpEnabled} flag rather than a separate rule.
     */
    public static boolean respectPartyFriendlyFire = true;

    /** Minimum ticks between lock requests from the same player, independent of the cooldown below. */
    public static int requestRateLimitTicks = 4;
    /** Ticks a player must wait after a lock breaks (any reason) before requesting a new one. */
    public static int reacquireCooldownTicks = 10;
    /** Minimum ticks between target-cycle requests. */
    public static int cycleRateLimitTicks = 4;

    /**
     * Whether the sync packet includes the target's velocity for client-side lead prediction.
     *
     * <p>This is the only lead-related setting that belongs on the server, because it is the only
     * one that decides what information leaves it. The projectile speed the marker is drawn for
     * is deliberately <i>not</i> here: the marker is display-only, changes with whichever attack
     * the pilot is about to use, and moves nothing but a few pixels — so it lives on the client as
     * {@code XenoClientConfig.leadAssistProjectileSpeed}. A server-side copy existed here and was
     * read by nothing, which made the split look like an oversight rather than a decision.
     */
    public static boolean sendVelocityForLead = true;

    private LockOnConfig() {
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            enabled = data.enabled;
            maxLockRangeBlocks = Math.max(1.0, data.maxLockRangeBlocks);
            minLockRangeBlocks = Math.max(0.0, Math.min(maxLockRangeBlocks - 0.5, data.minLockRangeBlocks));
            hardLockConeDeg = Math.max(0.5, Math.min(90.0, data.hardLockConeDeg));
            softLockConeDeg = Math.max(hardLockConeDeg, Math.min(90.0, data.softLockConeDeg));
            lockTimeSeconds = Math.max(0.0, data.lockTimeSeconds);
            graceSeconds = Math.max(0.0, data.graceSeconds);
            requireLineOfSight = data.requireLineOfSight;
            lockCreativePlayers = data.lockCreativePlayers;
            lockInvulnerablePlayers = data.lockInvulnerablePlayers;
            lockSpectators = data.lockSpectators;
            respectPartyFriendlyFire = data.respectPartyFriendlyFire;
            requestRateLimitTicks = Math.max(1, data.requestRateLimitTicks);
            reacquireCooldownTicks = Math.max(0, data.reacquireCooldownTicks);
            cycleRateLimitTicks = Math.max(1, data.cycleRateLimitTicks);
            sendVelocityForLead = data.sendVelocityForLead;
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Failed to load lock-on config; using defaults", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                Data data = new Data();
                data.enabled = enabled;
                data.maxLockRangeBlocks = maxLockRangeBlocks;
                data.minLockRangeBlocks = minLockRangeBlocks;
                data.hardLockConeDeg = hardLockConeDeg;
                data.softLockConeDeg = softLockConeDeg;
                data.lockTimeSeconds = lockTimeSeconds;
                data.graceSeconds = graceSeconds;
                data.requireLineOfSight = requireLineOfSight;
                data.lockCreativePlayers = lockCreativePlayers;
                data.lockInvulnerablePlayers = lockInvulnerablePlayers;
                data.lockSpectators = lockSpectators;
                data.respectPartyFriendlyFire = respectPartyFriendlyFire;
                data.requestRateLimitTicks = requestRateLimitTicks;
                data.reacquireCooldownTicks = reacquireCooldownTicks;
                data.cycleRateLimitTicks = cycleRateLimitTicks;
                data.sendVelocityForLead = sendVelocityForLead;
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save lock-on config", e);
        }
    }

    public static final class Data {
        public boolean enabled = true;
        public double maxLockRangeBlocks = 400.0;
        public double minLockRangeBlocks = 2.0;
        public double hardLockConeDeg = 4.0;
        public double softLockConeDeg = 12.0;
        public double lockTimeSeconds = 1.25;
        public double graceSeconds = 1.0;
        public boolean requireLineOfSight = true;
        public boolean lockCreativePlayers = false;
        public boolean lockInvulnerablePlayers = false;
        public boolean lockSpectators = false;
        public boolean respectPartyFriendlyFire = true;
        public int requestRateLimitTicks = 4;
        public int reacquireCooldownTicks = 10;
        public int cycleRateLimitTicks = 4;
        public boolean sendVelocityForLead = true;
    }
}
