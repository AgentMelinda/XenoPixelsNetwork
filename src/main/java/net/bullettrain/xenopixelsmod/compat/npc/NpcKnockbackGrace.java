package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A short window after an NPC is knocked back in which its combat brain does not steer it.
 *
 * <p>The brain's {@code steer} runs every tick and, while it is flying an NPC after its target,
 * writes the NPC's velocity outright. A knockback is a velocity too, so it was overwritten on the
 * very next tick and a flying NPC could not be pushed at all (2026-10-02 owner: "and dummytrain
 * not getting knockedbacked?"). During the window the brain leaves the velocity alone, so the push
 * plays out, and then it takes the NPC back.
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
        return now + TICKS;
    }

    static boolean open(Long until, long now) {
        return until != null && now < until;
    }

    /** Called when {@code victim} has just been pushed. Players steer themselves and are ignored. */
    public static void mark(Entity victim) {
        if (victim == null || victim instanceof Player || victim.level().isClientSide()) return;
        UNTIL.put(victim, until(victim.level().getGameTime()));
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

    /** Vanilla knockback counts too, once every guard that may cancel it has had its turn. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (!event.isCanceled()) mark(event.getEntity());
    }
}
