package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.compat.CameraAimHelper;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
    /**
     * Persistent NPC → target locks, set by scripts.
     *
     * <p>Separate from {@link #HOLDS}, which is a short visual cast pose on a tick budget. A
     * hard lock never expires on its own: it survives the target leaving range, the NPC losing
     * its CustomNPCs target, and death-and-respawn of the NPC, until a script clears it.
     */
    private static final Map<UUID, UUID> HARD_LOCKS = new ConcurrentHashMap<>();

    private record Hold(UUID targetId, int untilTick, int restoreAnim) {}

    private NpcKiAim() {}

    /**
     * Pins this NPC to a target until cleared. Also pushes it to CustomNPCs' own target so its
     * native AI cooperates rather than fighting the lock.
     */
    public static void hardLock(LivingEntity npc, LivingEntity target) {
        if (npc == null || !NpcTargetKeeper.isCombatTarget(target) || npc == target) {
            return;
        }
        HARD_LOCKS.put(npc.getUUID(), target.getUUID());
        if (npc instanceof net.minecraft.world.entity.Mob mob) {
            mob.setTarget(target);
        }
    }

    /** Pins this NPC to a target by UUID, whether or not that entity is loaded right now. */
    public static void hardLock(LivingEntity npc, UUID targetId) {
        if (npc != null && targetId != null && !targetId.equals(npc.getUUID())) {
            HARD_LOCKS.put(npc.getUUID(), targetId);
        }
    }

    /** The live entity this NPC is hard-locked to, or null. */
    public static LivingEntity hardLock(net.minecraft.server.MinecraftServer server, LivingEntity npc) {
        if (server == null || npc == null) {
            return null;
        }
        UUID targetId = HARD_LOCKS.get(npc.getUUID());
        if (targetId == null) {
            return null;
        }
        LivingEntity target = NpcEntityLookup.findAlive(server, targetId);
        if (!NpcTargetKeeper.isCombatTarget(target)) {
            if (target != null) HARD_LOCKS.remove(npc.getUUID(), targetId);
            return null;
        }
        return target == npc ? null : target;
    }

    /** The UUID this NPC is hard-locked to, even when that entity is not loaded. */
    public static UUID hardLockId(LivingEntity npc) {
        return npc == null ? null : HARD_LOCKS.get(npc.getUUID());
    }

    public static boolean isHardLocked(LivingEntity npc) {
        return npc != null && HARD_LOCKS.containsKey(npc.getUUID());
    }

    public static void clearHardLock(LivingEntity npc) {
        if (npc != null) {
            HARD_LOCKS.remove(npc.getUUID());
        }
    }

    public static void clearHardLock(UUID npcId) {
        if (npcId != null) {
            HARD_LOCKS.remove(npcId);
        }
    }

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

    /** Yaw toward the target's XZ at the caster's eye height — never 90° up. */
    public static Vec3 applyHorizontalPose(LivingEntity caster, LivingEntity target) {
        if (caster == null || target == null || !target.isAlive()) {
            return caster != null ? caster.getLookAngle() : Vec3.ZERO;
        }
        Vec3 from = caster.getEyePosition();
        Vec3 to = new Vec3(target.getX(), from.y, target.getZ());
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0E-8) {
            return caster.getLookAngle();
        }
        dir = dir.normalize();
        applyLook(caster, CameraAimHelper.yaw(dir), 0.0f);
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

    /** DMZ Z-lock style tracking for the server NPC. The body stays upright while the
     * head/camera pitch follows the target midpoint; projectile aim is computed separately. */
    public static void trackKiSenseTarget(LivingEntity caster, LivingEntity target) {
        if (caster == null || target == null || !target.isAlive()) return;
        Vec3 from = caster.getEyePosition();
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 1.0e-8) return;
        float yaw = NpcBrainKiRotation.targetYaw(delta.x, delta.z, caster.getYRot());
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        if (caster.isNoGravity() && !caster.onGround()) {
            // In flight the brain's chase velocity already points at this target. A 15% ease
            // left the body several degrees behind a circling target every tick, so it flew one
            // way while facing another. Track with a fixed turn rate instead, and keep the body
            // level: pitch belongs to the head and the shot, not the flying pose.
            float body = NpcBrainKiRotation.slew(caster.getYRot(), yaw, FLIGHT_TURN_PER_TICK);
            applyLook(caster, body, 0.0f);
            caster.setXRot(smoothAngle(caster.getXRot(), pitch));
            caster.xRotO = caster.getXRot();
            return;
        }
        applyLook(caster, smoothAngle(caster.getYRot(), yaw),
                smoothAngle(caster.getXRot(), pitch));
    }

    /** Degrees per tick a Ki Sense lock may turn a flying NPC's body. */
    static final float FLIGHT_TURN_PER_TICK = 30.0f;

    static float smoothAngle(float current, float desired) {
        return current + Mth.wrapDegrees(desired - current) * 0.15f;
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
            if (entity instanceof LivingEntity living
                    && NpcTargetKeeper.isCombatTarget(living) && living != caster) {
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
     * Melee swing for an NPC, choosing an airborne clip while it is flying.
     *
     * <p>A flying NPC has no ground melee pose: CustomNPCs drives its own animation state from
     * {@code aiStep} and only ever picks between its configured standing/moving animations, so a
     * punch thrown mid-flight otherwise plays nothing at all. Gecko custom models carry their own
     * clips and are triggered through {@link NpcGeckoAnim}; classic models fall back to the
     * vanilla swing, which is all CustomNPCs itself renders for them.
     *
     * <p>{@code DmzAnimHelper}'s clip constants are deliberately not used here — that helper
     * broadcasts through DMZ's player-only animation packet.
     */
    public static void playMelee(LivingEntity npc) {
        if (npc == null) {
            return;
        }
        npc.swing(InteractionHand.MAIN_HAND, true);
        if (NpcFlightBridge.isFlyingNavigation(npc)) {
            // Prefer a flight-specific clip when the model author provided one; the configured
            // attack clip is the fallback, and is still better than a frozen pose.
            if (NpcGeckoAnim.play(npc, "attack_air")) {
                return;
            }
        }
        NpcGeckoAnim.playAttack(npc);
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
        // This is ranged aiming. A vanilla swing makes the Full DMZ proxy play a
        // melee punch on every ki cast, even when the target is across the arena.
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
            Class<?> npcClass = NpcTypes.npcInterface();
            if (!npcClass.isInstance(caster)) {
                return;
            }
            int aim = NpcTypes.find("api.constants.AnimationType")
                    .getField("AIM")
                    .getInt(null);
            npcClass.getMethod("setCurrentAnimation", int.class).invoke(caster, aim);
        } catch (Throwable ignored) {
        }
    }

    private static int readCnpcAnimation(LivingEntity caster) {
        try {
            Class<?> npcClass = NpcTypes.npcInterface();
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
            Class<?> npcClass = NpcTypes.npcInterface();
            if (!npcClass.isInstance(caster)) {
                return;
            }
            npcClass.getMethod("setCurrentAnimation", int.class).invoke(caster, animation);
        } catch (Throwable ignored) {
        }
    }

    private static LivingEntity findLiving(ServerTickEvent.Post event, UUID id) {
        return NpcEntityLookup.findAlive(event.getServer(), id);
    }
}
