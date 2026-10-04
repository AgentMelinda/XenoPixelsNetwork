package net.bullettrain.xenopixelsmod.npc.job;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcTypes;
import net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcJob;
import net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/** Follows the nearest nearby NPC with the configured name, as the pinned MyNPCs job does. */
public final class NpcFollowerJob {
    public static final int RANGE = 20;
    public static final int CHECK_TICKS = 10;
    private static final double STOP_DISTANCE_SQR = 4.0;
    private static final Map<XenoNpcEntity, Long> BLOCKED_UNTIL = new WeakHashMap<>();

    private NpcFollowerJob() {
    }

    public static boolean active(NpcCombatProfile profile) {
        return profile != null && profile.jobEnabled
                && XenoNpcJob.byId(profile.job) == XenoNpcJob.FOLLOWER
                && profile.followerName != null && !profile.followerName.isBlank();
    }

    public static void tick(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (npc == null || npc.level().isClientSide) return;
        if (!active(profile)) {
            BLOCKED_UNTIL.remove(npc);
            stop(npc);
            return;
        }
        if (npc.getTarget() != null || net.bullettrain.xenopixelsmod.compat.npc.NpcFlightOwnership.brainDirectsFlight(npc.getUUID())) {
            stop(npc);
            return;
        }
        if ((npc.tickCount + npc.getId()) % CHECK_TICKS != 0) return;
        if (BLOCKED_UNTIL.getOrDefault(npc, 0L) > npc.level().getGameTime()) return;

        LivingEntity target = nearestNamedNpc(npc, profile.followerName);
        if (target == null || npc.distanceToSqr(target) <= STOP_DISTANCE_SQR) {
            stop(npc);
            return;
        }
        if (!XenoNpcBehaviour.claimMovement(npc, NpcMovementOwner.Claim.FOLLOW)) return;
        // Keep a cleanup marker even when movement arbitration is temporarily disabled.
        if (!NpcMovementOwner.claim(npc, NpcMovementOwner.Claim.FOLLOW)) return;
        if (NpcMovementOwner.stuck(npc)) {
            // A blocked route must not make the follower lurch against a wall indefinitely.
            BLOCKED_UNTIL.put(npc, npc.level().getGameTime() + 100);
            stop(npc);
            return;
        }
        if (NpcMovementOwner.progressing(npc)) {
            npc.getNavigation().moveTo(target, 1.0);
        }
    }

    /** The entity this follower is following right now, or null when the job is off or none is in range. */
    public static LivingEntity followed(XenoNpcEntity npc, NpcCombatProfile profile) {
        return npc == null || !active(profile) ? null : nearestNamedNpc(npc, profile.followerName);
    }

    private static LivingEntity nearestNamedNpc(XenoNpcEntity npc, String name) {
        LivingEntity nearest = null;
        double best = RANGE * RANGE;
        for (LivingEntity candidate : npc.level().getEntitiesOfClass(LivingEntity.class,
                npc.getBoundingBox().inflate(RANGE),
                entity -> entity != npc && entity.isAlive()
                        && (entity instanceof XenoNpcEntity || NpcTypes.isNpc(entity)))) {
            String visibleName = candidate instanceof XenoNpcEntity nativeNpc
                    ? nativeNpc.npcData().displayName() : candidate.getName().getString();
            if (!matchesName(name, visibleName)) continue;
            double distance = npc.distanceToSqr(candidate);
            if (distance < best) {
                nearest = candidate;
                best = distance;
            }
        }
        return nearest;
    }

    public static boolean matchesName(String configured, String visible) {
        return configured != null && visible != null && !configured.isBlank()
                && configured.trim().equalsIgnoreCase(visible.trim());
    }

    private static void stop(XenoNpcEntity npc) {
        if (NpcMovementOwner.current(npc) == NpcMovementOwner.Claim.FOLLOW) {
            npc.getNavigation().stop();
        }
        NpcMovementOwner.release(npc, NpcMovementOwner.Claim.FOLLOW);
    }
}
