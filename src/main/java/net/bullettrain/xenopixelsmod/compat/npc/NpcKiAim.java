package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.compat.CameraAimHelper;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Briefly poses a CustomNPCs caster toward the chosen target. This deliberately
 * does not rewrite the NPC's AI target, attacker, follow range, or aggro range:
 * those values belong to CustomNPCs and changing them caused aggressive and
 * retaliate NPCs to retain broken state in their saved NBT.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcKiAim {
    public static final double LOCK_RANGE = 128.0;

    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

    private record Hold(UUID targetId, int untilTick, int restoreAnim) {}

    private NpcKiAim() {}

    /** Point body + head at {@code target.getEyePosition()} (the head). */
    public static Vec3 applyPose(LivingEntity caster, LivingEntity target) {
        if (caster == null || target == null || !target.isAlive()) {
            return caster != null ? caster.getLookAngle() : Vec3.ZERO;
        }
        Vec3 dir = target.getEyePosition().subtract(caster.getEyePosition());
        if (dir.lengthSqr() < 1.0E-8) {
            return caster.getLookAngle();
        }
        dir = dir.normalize();
        applyLook(caster, CameraAimHelper.yaw(dir), CameraAimHelper.pitch(dir));
        CameraAimHelper.store(caster, dir);
        return dir;
    }

    public static void applyLook(LivingEntity caster, float yaw, float pitch) {
        if (caster == null) {
            return;
        }
        caster.setYRot(yaw);
        caster.setXRot(pitch);
        caster.setYHeadRot(yaw);
        caster.yBodyRot = yaw;
        caster.yRotO = yaw;
        caster.xRotO = pitch;
        caster.yHeadRotO = yaw;
        caster.yBodyRotO = yaw;
    }

    public static void hold(LivingEntity caster, LivingEntity target, int ticks) {
        if (caster == null || target == null || ticks <= 0) {
            return;
        }
        applyPose(caster, target);
        int until = caster.tickCount + ticks;
        if (caster.level() instanceof ServerLevel server) {
            until = server.getServer().getTickCount() + ticks;
        }
        Hold existing = HOLDS.get(caster.getUUID());
        // Snapshot before applying AIM. The previous order captured AIM itself and
        // therefore never restored the NPC's original animation.
        int restore = existing != null ? existing.restoreAnim() : readCnpcAnimation(caster);
        HOLDS.put(caster.getUUID(), new Hold(target.getUUID(), until, restore));
        startCastAnimation(caster);
    }

    public static LivingEntity lockedTarget(LivingEntity caster) {
        if (caster == null) {
            return null;
        }
        Hold hold = HOLDS.get(caster.getUUID());
        if (hold == null) {
            return null;
        }
        if (caster.level() instanceof ServerLevel server) {
            Entity entity = server.getEntity(hold.targetId());
            if (entity instanceof LivingEntity living && living.isAlive() && living != caster) {
                return living;
            }
        }
        return null;
    }

    /** Keep an existing hold, but lock it to a newly computed yaw/pitch (Y-aim). */
    public static void updateHold(LivingEntity caster, float yaw, float pitch) {
        if (caster == null || !HOLDS.containsKey(caster.getUUID())) {
            return;
        }
        applyLook(caster, yaw, pitch);
        applyCnpcAimPose(caster);
    }

    /**
     * Vanilla arm swing plus CustomNPCs {@code AnimationType.AIM} (two-handed bow-style
     * pose). DMZ GeckoLib ki-hand clips only run on {@code IPlayerAnimatable} players
     * ({@code TriggerAnimationS2C} looks up {@code getPlayerByUUID}).
     */
    public static void startCastAnimation(LivingEntity caster) {
        if (caster == null) {
            return;
        }
        caster.swing(InteractionHand.MAIN_HAND, true);
        // Gecko custom-model entities do not render the vanilla swing event.
        // Trigger the attack clip configured in Model Editor when available,
        // while retaining AIM for classic CustomNPC models.
        NpcGeckoAnim.playAttack(caster);
        applyCnpcAimPose(caster);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (HOLDS.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, Hold>> it = HOLDS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Hold> e = it.next();
            Hold hold = e.getValue();
            LivingEntity caster = findLiving(event, e.getKey());
            if (now > hold.untilTick() || caster == null || !caster.isAlive()) {
                if (caster != null) {
                    restoreCnpcAnimation(caster, hold.restoreAnim());
                }
                it.remove();
                continue;
            }
            LivingEntity target = findLiving(event, hold.targetId());
            if (target == null || !target.isAlive() || target.distanceTo(caster) > LOCK_RANGE) {
                restoreCnpcAnimation(caster, hold.restoreAnim());
                it.remove();
                continue;
            }
            applyPose(caster, target);
            applyCnpcAimPose(caster);
        }
    }

    /** Cancel a cast pose and restore the CustomNPCs animation immediately. */
    public static void cancel(LivingEntity caster) {
        if (caster == null) {
            return;
        }
        Hold hold = HOLDS.remove(caster.getUUID());
        if (hold != null) {
            restoreCnpcAnimation(caster, hold.restoreAnim());
        }
    }

    /** Forget an unloaded/deleted caster when no live entity is available to restore. */
    public static void cancel(UUID casterId) {
        if (casterId != null) {
            HOLDS.remove(casterId);
        }
    }

    private static void applyCnpcAimPose(LivingEntity caster) {
        try {
            Class<?> npcClass = Class.forName("noppes.npcs.entity.EntityNPCInterface");
            if (!npcClass.isInstance(caster)) {
                return;
            }
            int aim = Class.forName("noppes.npcs.api.constants.AnimationType")
                    .getField("AIM")
                    .getInt(null);
            npcClass.getMethod("setCurrentAnimation", int.class).invoke(caster, aim);
        } catch (Throwable ignored) {
        }
    }

    private static int readCnpcAnimation(LivingEntity caster) {
        try {
            Class<?> npcClass = Class.forName("noppes.npcs.entity.EntityNPCInterface");
            if (!npcClass.isInstance(caster)) {
                return 0;
            }
            Object value = npcClass.getField("currentAnimation").get(caster);
            return value instanceof Integer i ? i : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void restoreCnpcAnimation(LivingEntity caster, int animation) {
        try {
            Class<?> npcClass = Class.forName("noppes.npcs.entity.EntityNPCInterface");
            if (!npcClass.isInstance(caster)) {
                return;
            }
            npcClass.getMethod("setCurrentAnimation", int.class).invoke(caster, animation);
        } catch (Throwable ignored) {
        }
    }

    private static LivingEntity findLiving(ServerTickEvent.Post event, UUID id) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        return null;
    }
}
