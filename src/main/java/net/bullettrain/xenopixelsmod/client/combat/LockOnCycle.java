package net.bullettrain.xenopixelsmod.client.combat;

import com.dragonminez.client.events.LockOnEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cycles DMZ lock-on target to the next/previous nearby living entity.
 * Uses reflection to write {@link LockOnEvent}'s private lockedTarget field.
 */
public final class LockOnCycle {
    private static Field lockedTargetField;
    private static boolean fieldResolved;

    private LockOnCycle() {}

    public static void cycle(int direction) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (net.bullettrain.xenopixelsmod.client.XenoServerClientState.v3Controller()) {
            cycleV3(player, direction);
            return;
        }

        List<LivingEntity> candidates = gather(player, 48.0);
        if (candidates.isEmpty()) {
            unlock();
            return;
        }

        LivingEntity current = LockOnEvent.getLockedTarget();
        int idx = -1;
        if (current != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).getId() == current.getId()) {
                    idx = i;
                    break;
                }
            }
        }
        int next;
        if (idx < 0) {
            next = direction >= 0 ? 0 : candidates.size() - 1;
        } else {
            next = Math.floorMod(idx + (direction >= 0 ? 1 : -1), candidates.size());
        }
        setLocked(candidates.get(next));
    }

    private static void cycleV3(LocalPlayer player, int direction) {
        var mirror = net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.target();
        if (mirror == null) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = net.bullettrain.xenopixelsmod.combat.v3.V3TargetingRules.LOCK_RANGE;
        List<LivingEntity> candidates = new ArrayList<>(64);
        player.level().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class),
                player.getBoundingBox().inflate(range),
                entity -> entity != player && entity.isAlive() && player.distanceToSqr(entity) >= 0.25
                        && player.distanceToSqr(entity) <= range * range && player.hasLineOfSight(entity), candidates, 64);
        candidates.sort(Comparator.comparingDouble((LivingEntity entity) -> {
            Vec3 to = entity.getEyePosition().subtract(eye);
            return to.lengthSqr() < 1.0e-8 ? 0 : -look.dot(to.normalize());
        }).thenComparingDouble(player::distanceToSqr));
        int current = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (mirror != null && mirror.target().equals(candidates.get(i).getUUID())) current = i;
        }
        if (candidates.isEmpty()) return;
        int next = current < 0 ? (direction >= 0 ? 0 : candidates.size() - 1)
                : Math.floorMod(current + (direction >= 0 ? 1 : -1), candidates.size());
        net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.send(
                net.bullettrain.xenopixelsmod.combat.v3.V3Input.LOCK_CYCLE, candidates.get(next).getUUID(),
                net.bullettrain.xenopixelsmod.combat.v3.V3Direction.NONE);
    }

    private static List<LivingEntity> gather(LocalPlayer player, double range) {
        Vec3 eye = player.getEyePosition(1f);
        Vec3 look = player.getLookAngle();
        AABB box = player.getBoundingBox().inflate(range);
        List<LivingEntity> list = new ArrayList<>();
        for (var e : player.level().getEntities(player, box,
                ent -> ent instanceof LivingEntity le && le.isAlive() && le != player)) {
            if (!(e instanceof LivingEntity living)) continue;
            double dist = player.distanceTo(living);
            if (dist > range || dist < 0.5) continue;
            // The same sight test DragonMineZ's own lock makes. The "lock through blocks" setting
            // answers it for the local player (DmzLockOnLosMixin), so with the setting on this
            // passes everything, and with it off cycling no longer hops to a target behind a wall
            // that the lock key itself would refuse.
            if (!player.hasLineOfSight(living)) continue;
            list.add(living);
        }
        // Prefer more central / closer targets
        list.sort(Comparator
                .comparingDouble((LivingEntity le) -> {
                    Vec3 to = le.getEyePosition(1f).subtract(eye);
                    double len = to.length();
                    if (len < 1.0e-4) return 0.0;
                    return -look.dot(to.scale(1.0 / len)); // higher dot first
                })
                .thenComparingDouble(player::distanceTo));
        return list;
    }

    private static void unlock() {
        try {
            LockOnEvent.unlock();
        } catch (Throwable ignored) {
        }
    }

    private static void setLocked(LivingEntity target) {
        if (!resolveField()) {
            // Fallback: toggle until we match (unreliable) — just unlock
            unlock();
            return;
        }
        try {
            lockedTargetField.set(null, target);
        } catch (Throwable t) {
            unlock();
        }
    }

    /** Public, safe lock adoption used by the optional XenoParty target-assist marker. */
    public static boolean lock(LivingEntity target) {
        if (net.bullettrain.xenopixelsmod.client.XenoServerClientState.v3Controller()) {
            return target != null && target.isAlive() && net.bullettrain.xenopixelsmod.client.combat.v3.V3ClientState.send(
                    net.bullettrain.xenopixelsmod.combat.v3.V3Input.LOCK_ACQUIRE, target.getUUID(),
                    net.bullettrain.xenopixelsmod.combat.v3.V3Direction.NONE);
        }
        if (target == null || !target.isAlive() || !resolveField()) return false;
        try {
            lockedTargetField.set(null, target);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean resolveField() {
        if (fieldResolved) return lockedTargetField != null;
        fieldResolved = true;
        try {
            lockedTargetField = LockOnEvent.class.getDeclaredField("lockedTarget");
            lockedTargetField.setAccessible(true);
            return true;
        } catch (Throwable t) {
            lockedTargetField = null;
            return false;
        }
    }
}
