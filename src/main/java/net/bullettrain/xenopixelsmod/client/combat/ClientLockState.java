package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.combat.targeting.LockOnQuality;
import net.bullettrain.xenopixelsmod.combat.targeting.LockOnValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The last authoritative lock state this client was sent, kept for the crosshair HUD.
 *
 * <p>Same contract as {@link net.bullettrain.xenopixelsmod.client.flight.ClientFlightState}: the
 * HUD renders only what the server has actually granted, never an optimistic guess about a lock
 * request that has not been answered yet. Target and velocity are resolved fresh each read
 * rather than cached as references, since the underlying client entity can be removed or the
 * level can change out from under a stale one.
 */
public final class ClientLockState {

    private static final long STALE_MILLIS = 2_000L;

    private static int targetEntityId = -1;
    private static LockOnQuality quality = LockOnQuality.WEAK;
    private static int progressPercent;
    private static boolean hasVelocity;
    private static float velX, velY, velZ;
    private static long receivedAtMillis;

    private ClientLockState() {
    }

    public static void accept(int targetEntityId, LockOnQuality quality, int progressPercent,
                              boolean hasVelocity, float velX, float velY, float velZ) {
        ClientLockState.targetEntityId = targetEntityId;
        ClientLockState.quality = quality == null ? LockOnQuality.WEAK : quality;
        ClientLockState.progressPercent = progressPercent;
        ClientLockState.hasVelocity = hasVelocity;
        ClientLockState.velX = velX;
        ClientLockState.velY = velY;
        ClientLockState.velZ = velZ;
        ClientLockState.receivedAtMillis = System.currentTimeMillis();
    }

    public static void clear() {
        targetEntityId = -1;
        quality = LockOnQuality.WEAK;
        progressPercent = 0;
        hasVelocity = false;
        velX = velY = velZ = 0f;
        receivedAtMillis = 0L;
        errorReason = null;
        errorAtMillis = 0L;
    }

    private static @Nullable LockOnValidator.Reason errorReason;
    private static long errorAtMillis;
    private static final long ERROR_DISPLAY_MILLIS = 2_000L;

    public static void acceptError(LockOnValidator.Reason reason) {
        errorReason = reason;
        errorAtMillis = System.currentTimeMillis();
    }

    /** A short, still-fresh rejection reason to show on the crosshair, or null once it has faded. */
    public static @Nullable LockOnValidator.Reason recentError() {
        if (errorReason == null || System.currentTimeMillis() - errorAtMillis > ERROR_DISPLAY_MILLIS) return null;
        return errorReason;
    }

    public static String describe(LockOnValidator.Reason reason) {
        return switch (reason) {
            case OK -> "";
            case DISABLED -> "Lock-on disabled";
            case NOT_SEATED -> "Not in a seat";
            case SELF -> "Cannot lock yourself";
            case NOT_A_PLAYER -> "Target invalid";
            case SAME_PARTY -> "Target is an ally";
            case SPECTATOR -> "Target invalid";
            case CREATIVE -> "Target invalid";
            case INVULNERABLE -> "Target invalid";
            case OUT_OF_RANGE -> "Out of range";
            case TOO_CLOSE -> "Too close";
            case OUTSIDE_CONE -> "Not in view";
            case NO_LINE_OF_SIGHT -> "No line of sight";
            case DEAD -> "Target invalid";
            case REMOVED -> "Target invalid";
        };
    }

    private static boolean fresh() {
        return receivedAtMillis != 0L && System.currentTimeMillis() - receivedAtMillis <= STALE_MILLIS;
    }

    public static boolean hasLock() {
        return fresh() && targetEntityId >= 0;
    }

    public static LockOnQuality quality() {
        return quality;
    }

    /** 0..100. */
    public static int progressPercent() {
        return progressPercent;
    }

    /** The locked entity, resolved fresh from the client level; null if gone, unloaded, or no lock. */
    public static @Nullable Entity resolveTarget() {
        if (!hasLock()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = mc.level.getEntity(targetEntityId);
        return entity != null && entity.isAlive() ? entity : null;
    }

    /** World-space velocity in blocks/second, if the server included it this update; else null. */
    public static @Nullable Vec3 velocity() {
        return hasLock() && hasVelocity ? new Vec3(velX, velY, velZ) : null;
    }
}
