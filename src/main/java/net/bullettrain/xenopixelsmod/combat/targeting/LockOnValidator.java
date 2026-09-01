package net.bullettrain.xenopixelsmod.combat.targeting;

import com.dragonminez.server.world.data.PartySavedData;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.bullettrain.xenopixelsmod.features.party.PartyManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Everything a lock request or an existing lock must satisfy. Runs on the server only, and is the
 * single place these rules live — used identically for the initial lock request and for the
 * continuous re-check every tick, so an established lock cannot be held under conditions a fresh
 * request would have been refused for.
 *
 * <p>Client input is never trusted beyond "which entity id did the player ask to lock" — every
 * other fact used here (position, party, game mode, invulnerability, blocks in the way) is read
 * fresh from server-side world state.
 */
public final class LockOnValidator {

    private LockOnValidator() {
    }

    public enum Reason {
        OK, DISABLED, NOT_SEATED, SELF, NOT_A_PLAYER, SAME_PARTY, SPECTATOR, CREATIVE,
        INVULNERABLE, OUT_OF_RANGE, TOO_CLOSE, OUTSIDE_CONE, NO_LINE_OF_SIGHT, DEAD, REMOVED
    }

    /** Eligibility a fresh lock request must pass: the full cone and every static rule. */
    public static Reason checkEligible(ServerPlayer locker, @Nullable Entity target) {
        Reason base = checkBase(locker, target);
        if (base != Reason.OK) return base;
        return checkAngle(locker, target, LockOnConfig.hardLockConeDeg);
    }

    /**
     * Whether an already-locked target may keep being locked this tick. Uses the wider soft cone
     * — a target already earned does not vanish the instant it edges past the hard cone, but
     * still cannot be re-acquired there from cold.
     */
    public static Reason checkStillValid(ServerPlayer locker, @Nullable Entity target) {
        Reason base = checkBase(locker, target);
        if (base != Reason.OK) return base;
        return checkAngle(locker, target, LockOnConfig.softLockConeDeg);
    }

    private static Reason checkBase(ServerPlayer locker, @Nullable Entity target) {
        if (!LockOnConfig.enabled) return Reason.DISABLED;
        if (!(locker.getVehicle() instanceof XenoPilotSeatEntity)) return Reason.NOT_SEATED;
        if (target == null || target.isRemoved()) return Reason.REMOVED;
        if (target == locker) return Reason.SELF;
        if (!target.isAlive()) return Reason.DEAD;
        if (!(target instanceof ServerPlayer targetPlayer)) return Reason.NOT_A_PLAYER;

        if (!LockOnConfig.lockSpectators && targetPlayer.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            return Reason.SPECTATOR;
        }
        if (!LockOnConfig.lockCreativePlayers && targetPlayer.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
            return Reason.CREATIVE;
        }
        if (!LockOnConfig.lockInvulnerablePlayers && targetPlayer.isInvulnerable()) {
            return Reason.INVULNERABLE;
        }

        UUID lockerParty = PartyManager.partyOf(locker);
        UUID targetParty = PartyManager.partyOf(targetPlayer);
        if (lockerParty != null && lockerParty.equals(targetParty)) {
            if (LockOnConfig.respectPartyFriendlyFire) {
                PartySavedData.PartyInstance party = PartySavedData.get(locker.getServer()).getParty(lockerParty);
                if (party == null || !party.isPvpEnabled()) return Reason.SAME_PARTY;
                // Friendly fire is on for this party: fall through, the party member is lockable.
            } else {
                return Reason.SAME_PARTY;
            }
        }

        double distSqr = locker.distanceToSqr(target);
        if (distSqr > LockOnConfig.maxLockRangeBlocks * LockOnConfig.maxLockRangeBlocks) return Reason.OUT_OF_RANGE;
        if (distSqr < LockOnConfig.minLockRangeBlocks * LockOnConfig.minLockRangeBlocks) return Reason.TOO_CLOSE;

        if (LockOnConfig.requireLineOfSight && !hasLineOfSight(locker, target)) return Reason.NO_LINE_OF_SIGHT;

        return Reason.OK;
    }

    private static Reason checkAngle(ServerPlayer locker, Entity target, double coneDeg) {
        Vec3 toTarget = target.position().subtract(eyePosition(locker));
        if (toTarget.lengthSqr() < 1.0e-6) return Reason.OK;
        Vec3 look = locker.getLookAngle();
        double cos = look.dot(toTarget.normalize());
        double angleDeg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
        return angleDeg <= coneDeg ? Reason.OK : Reason.OUTSIDE_CONE;
    }

    private static boolean hasLineOfSight(ServerPlayer locker, Entity target) {
        Vec3 from = eyePosition(locker);
        Vec3 to = target.getEyePosition();
        HitResult hit = locker.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, locker));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static Vec3 eyePosition(ServerPlayer player) {
        // The seat itself has no eye height; the pilot's own eye position is the correct origin
        // for both the cone and the line-of-sight check regardless of what they are riding.
        return player.getEyePosition();
    }
}
