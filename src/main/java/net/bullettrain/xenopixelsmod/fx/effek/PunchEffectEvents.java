package net.bullettrain.xenopixelsmod.fx.effek;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Basic DMZ punches (players and NPCs) land through ordinary melee damage and never reach
 * {@code CombatFx}, so they showed no hit effect.
 *
 * <p>Combo, charge and guard code calls {@code hurt} (which fires this hook) before its own
 * {@code CombatFx.impact}, so the hook must not play right away: it queues the hit and plays the
 * light effect at the end of the server tick, only when nothing explicit already drew an effect on
 * that target (the gate reports it as a duplicate). It never draws vanilla particles or cues, so
 * with the effects off plain melee looks exactly as before.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PunchEffectEvents {
    private record Hit(ServerLevel level, long tick, Vec3 pos, Vec3 dir) {}

    /** Target id -> first queued hit this tick. Server thread only. */
    private static final Int2ObjectLinkedOpenHashMap<Hit> QUEUE = new Int2ObjectLinkedOpenHashMap<>();

    private PunchEffectEvents() {}

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || event.getNewDamage() <= 0f) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!PunchEffectRules.isDmzMelee(event.getSource(), attacker)) return;
        // At the height the attacker's crosshair meets the target (2026-09-29 owner).
        queue(level, target.getId(), net.bullettrain.xenopixelsmod.combat.fx.CombatFx.crosshairPoint(attacker, target),
                target.position().subtract(attacker.position()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerTick(ServerTickEvent.Post event) {
        flush();
    }

    /** Records a basic punch; the first hit per target wins. */
    static void queue(ServerLevel level, int targetId, Vec3 pos, Vec3 dir) {
        long tick = level == null ? 0L : level.getGameTime();
        if (!QUEUE.containsKey(targetId)) QUEUE.put(targetId, new Hit(level, tick, pos, dir));
    }

    /** Plays the queued basic punches that nothing explicit covered. @return effects played */
    static int flush() {
        if (QUEUE.isEmpty()) return 0;
        int played = 0;
        for (var entry : QUEUE.int2ObjectEntrySet()) {
            Hit hit = entry.getValue();
            if (XenoEffects.attemptAt(hit.level(), hit.tick(), EffectSlot.PUNCH_IMPACT, hit.pos(), hit.dir(), 1.0f,
                    entry.getIntKey()) == XenoEffects.Outcome.PLAYED) {
                played++;
            }
        }
        QUEUE.clear();
        return played;
    }

    static void clearForTest() {
        QUEUE.clear();
    }
}
