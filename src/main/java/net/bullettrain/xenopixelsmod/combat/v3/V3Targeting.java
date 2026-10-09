package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-approved UUID lock. Acquisition is bounded; retention never searches the level. */
public final class V3Targeting {
    private V3Targeting() {}
    public static boolean acquire(ServerPlayer player, UUID requested, int now) {
        V3Fighter fighter = V3FighterStore.get(player);
        if (now < fighter.acquireReadyTick) { fighter.targetRefusal = "Acquisition rate limited"; return false; }
        fighter.acquireReadyTick = (long) now + 5;
        var stats = V3Protection.sensingStats(player);
        if (stats == null) return refuse(player, "Ki sense unavailable");
        if (requested != null) {
            var candidate = player.serverLevel().getEntity(requested);
            if (!(candidate instanceof LivingEntity)) return refuse(player, "Requested target unloaded or in another dimension");
            if (!candidate.isAlive() || candidate.isRemoved()) return refuse(player, "Requested target dead or removed");
        }
        double range = V3TargetingRules.LOCK_RANGE;
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        if (!V3TargetingRules.finite(from) || !V3TargetingRules.finite(look)) return refuse(player, "Invalid eye ray");
        Vec3 to = from.add(look.scale(range));
        AABB search = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        List<LivingEntity> candidates = new ArrayList<>(64);
        // Pinned Level overload aborts spatial iteration at maxResults; no unbounded result list.
        player.serverLevel().getEntities(EntityTypeTest.forClass(LivingEntity.class), search,
                entity -> entity != player && entity.isAlive() && (entity.isPickable() || V3Protection.npc(entity)), candidates, 64);
        LivingEntity nearest = null;
        double nearestHit = range * range;
        for (LivingEntity entity : candidates) {
            if (!valid(player, entity, stats)) continue;
            AABB box = entity.getBoundingBox();
            if (V3Protection.npc(entity) && box.getXsize() < 0.2) box = box.inflate(0.4, 0, 0.4);
            box = box.inflate(entity.getPickRadius());
            double distance = V3TargetingRules.rayDistance(box, from, to);
            if (distance < nearestHit || (distance == 0 && nearest == null)) { nearest = entity; nearestHit = distance; }
        }
        if (nearest == null || (requested != null && !V3TargetingRules.matches(requested, nearest.getUUID()))) {
            return refuse(player, "No eligible loaded ray target");
        }
        // A new lock also cancels any gesture/window aimed at the previous identity.
        V3CombatServer.cancelLiveState(player);
        fighter.approvedTarget = nearest.getUUID();
        fighter.targetRefusal = null;
        update(fighter, nearest, now);
        V3CombatServer.syncState(player);
        return true;
    }
    /** Explicit cycling uses a loaded candidate UUID and every retention rule, with no invented cone. */
    public static boolean cycle(ServerPlayer player, UUID requested, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || fighter.approvedTarget == null) return false;
        if (now < fighter.acquireReadyTick) { fighter.targetRefusal = "Acquisition rate limited"; return false; }
        fighter.acquireReadyTick = (long) now + 5;
        // Revalidate the old lock first. Failure here legitimately clears a now-invalid lock.
        boolean currentValid = resolve(player) != null;
        var raw = requested == null ? null : player.serverLevel().getEntity(requested);
        boolean candidateValid = raw instanceof LivingEntity target
                && valid(player, target, V3Protection.sensingStats(player));
        if (!V3TargetingRules.cycleAllowed(fighter.approvedTarget, requested, currentValid, candidateValid)) {
            if (currentValid) fighter.targetRefusal = "Cycle candidate refused; current lock retained";
            return false;
        }
        LivingEntity target = (LivingEntity) raw;
        V3CombatServer.cancelLiveState(player);
        fighter.approvedTarget = target.getUUID();
        fighter.targetRefusal = null;
        update(fighter, target, now);
        V3CombatServer.syncState(player);
        return true;
    }
    private static boolean valid(ServerPlayer player, LivingEntity target, com.dragonminez.common.stats.StatsData stats) {
        return V3Protection.eligible(player, target, stats)
                && V3TargetingRules.inRange(player.distanceToSqr(target), V3TargetingRules.LOCK_RANGE)
                && V3TargetingRules.finite(target.position()) && V3TargetingRules.finite(target.getDeltaMovement())
                && (XenoServerConfig.lockOnThroughBlocks || V3LoadedVisibility.visible(player, target));
    }
    /**
     * The approved lock as a usable target, or null.
     *
     * <p>Owner rule (2026-10-08): a failed or refused action must never cost the lock. Only the
     * cases where the target is actually gone clear it: unloaded / other dimension, dead or removed.
     * Everything else that makes the target temporarily unusable (out of range, behind a block,
     * invisible for a vanish frame, protection rules, lost ki sense) refuses the action while the
     * approved identity, the client's DragonMineZ lock marker and the live state stay as they are.
     * The player drops the lock explicitly with {@code LOCK_CLEAR}.
     */
    public static LivingEntity resolve(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || fighter.approvedTarget == null) return null;
        // Exact server UUID lookup only returns a currently loaded entity in this dimension.
        var raw = player.serverLevel().getEntity(fighter.approvedTarget);
        if (!(raw instanceof LivingEntity target)) { refuse(player, "Target unloaded or in another dimension", true); return null; }
        if (!target.isAlive() || target.isRemoved()) { refuse(player, "Target dead or removed", true); return null; }
        if (target.level() != player.level()) { refuse(player, "Target changed dimension", true); return null; }
        if (!valid(player, target, V3Protection.sensingStats(player))) {
            fighter.targetRefusal = "Target not reachable right now";
            return null;
        }
        return target;
    }

    /** Pure form of the rule above, so a test can pin which outcomes drop the lock. */
    static boolean dropsLock(boolean loaded, boolean alive, boolean sameDimension, boolean eligible) {
        return !loaded || !alive || !sameDimension;
    }
    public static V3TargetSnapshot snapshot(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        return fighter == null ? null : fighter.target;
    }
    public static void clear(ServerPlayer player) { refuse(player, "Lock cleared", true); }
    public static String refusal(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        return fighter == null ? null : fighter.targetRefusal;
    }
    private static boolean refuse(ServerPlayer player, String reason) {
        return refuse(player, reason, false);
    }
    /**
     * Soft refuse keeps the approved lock. Hard refuse ({@code dropLock}) is only for explicit
     * unlock or a target that is actually gone.
     */
    private static boolean refuse(ServerPlayer player, String reason, boolean dropLock) {
        V3Fighter fighter = V3FighterStore.get(player);
        boolean hadLock = fighter.approvedTarget != null;
        boolean changed = hadLock || fighter.state != V3State.IDLE
                || fighter.chargeTicks != 0 || fighter.windowTicksTotal != 0;
        V3CombatServer.cancelLiveState(player);
        if (dropLock) fighter.clearApprovedLock();
        fighter.targetRefusal = reason;
        if (changed || (dropLock && hadLock)) V3CombatServer.syncState(player);
        return false;
    }
    private static void update(V3Fighter fighter, LivingEntity target, long now) {
        fighter.target = new V3TargetSnapshot(target.getUUID(), target.getId(), target.position(), target.getDeltaMovement(), ++fighter.targetRevision);
        fighter.targetSentTick = now;
    }
    static void tick(ServerPlayer player, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || fighter.approvedTarget == null) return;
        LivingEntity target = resolve(player);
        if (target != null && (fighter.target.entityId() != target.getId()
                || V3TargetingRules.motionDue(fighter.target, target.position(), target.getDeltaMovement(), now, fighter.targetSentTick))) {
            update(fighter, target, now);
            V3CombatServer.syncState(player);
        }
    }
}
