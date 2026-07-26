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
