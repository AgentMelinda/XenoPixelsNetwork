package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A short hit-recovery window in which an NPC yields steering, aiming and attacks.
 *
 * <p>The brain's {@code steer} runs every tick and, while it is flying an NPC after its target,
 * writes the NPC's velocity outright. A knockback is a velocity too, so it was overwritten on the
 * very next tick and a flying NPC could not be pushed at all (2026-10-02 owner: "and dummytrain
 * not getting knockedbacked?"). During the window the brain leaves the velocity alone, so the push
 * plays out, and then it takes the NPC back.
 *
 * <p>Does <b>not</b> force face-away / face-toward — that fought fly retaliation (owner revert
 * 2026-03-22). Recovery only freezes the yaw that already existed that tick.
 *
 * <p>Server thread only. Weak keys: an unloaded NPC's entry goes with it.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcKnockbackGrace {
    /** Ticks the brain stays off the velocity: long enough to see a launch arc start. */
    static final int TICKS = 10;

    private static final Map<Entity, Long> UNTIL = new WeakHashMap<>();

    private NpcKnockbackGrace() {
    }

    /** The game tick a window opened at {@code now} closes. */
    static long until(long now) {
        return until(now, TICKS);
    }

    static long until(long now, int ticks) {
        return now + Math.clamp(ticks, 0, 200);
    }

    static boolean open(Long until, long now) {
        return until != null && now < until;
    }

    /** Same open-window rule, for pure tests without an entity map. */
    public static boolean shouldHoldFacing(Long until, long now) {
        return open(until, now);
    }

    /** Pure helper kept for unit tests. Not applied during recovery anymore. */
    public static float faceAwayYaw(double victimX, double victimZ, double attackerX, double attackerZ) {
        double lookX = victimX - attackerX;
        double lookZ = victimZ - attackerZ;
        if (!Double.isFinite(lookX) || !Double.isFinite(lookZ) || lookX * lookX + lookZ * lookZ < 1.0E-8) {
            return Float.NaN;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(lookZ, lookX)) - 90.0);
        if (yaw <= -180f) yaw += 360f;
        if (yaw > 180f) yaw -= 360f;
        return yaw;
    }

    /** Called when {@code victim} has just been pushed. Players steer themselves and are ignored. */
    public static void mark(Entity victim) {
        mark(victim, null);
    }

    /** Opens recovery without forcing facing. {@code attacker} is accepted for call-site compatibility. */
    public static void mark(Entity victim, Entity attacker) {
        if (victim == null || victim instanceof Player || victim.level().isClientSide()) return;
        int ticks = Math.clamp(XenoServerConfig.npcHitRecoveryTicks, 0, 200);
        if (ticks == 0) return;
        boolean alreadyRecovering = active(victim);
        UNTIL.put(victim, until(victim.level().getGameTime(), ticks));
        if (victim instanceof Mob mob) mob.getNavigation().stop();
        if (!alreadyRecovering && victim instanceof LivingEntity living
                && !net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime
                        .isControlledVictim(victim.getUUID())) {
            NpcCombatBrain.interruptForHit(living);
        }
    }

    /** Whether the brain should keep its hands off this NPC's velocity for now. */
    public static boolean active(Entity npc) {
        if (npc == null || UNTIL.isEmpty()) return false;
        Long until = UNTIL.get(npc);
        if (until == null) return false;
        if (open(until, npc.level().getGameTime())) return true;
        UNTIL.remove(npc);
        return false;
    }

    /** No forced recovery yaw (owner reverted face-lock). */
    public static Float recoveryYaw(Entity npc) {
        return null;
    }

    /** Vanilla knockback counts too, once every guard that may cancel it has had its turn. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (!event.isCanceled()) mark(event.getEntity());
    }

    /** Native NPCs also need recovery when an accepted hit has no vanilla knockback event. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity)
                || !(event.getNewDamage() > 0f) || !Float.isFinite(event.getNewDamage())) return;
        Entity attacker = event.getSource().getEntity();
        mark(event.getEntity(), attacker instanceof LivingEntity ? attacker : null);
    }
}
