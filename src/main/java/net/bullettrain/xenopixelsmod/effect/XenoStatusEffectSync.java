package net.bullettrain.xenopixelsmod.effect;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mirrors Super Soul / skill / sparking state into vanilla {@link MobEffect}s
 * so they appear in the inventory (E) effects panel and the HUD icon strip.
 *
 * <p>Display-only: no attribute modifiers; combat still reads capability + config.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoStatusEffectSync {
    private static final int REFRESH = 80; // 4s — long enough that inventory doesn't flicker

    private XenoStatusEffectSync() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        // Stagger by UUID so 30+ players don't all refresh the same tick
        if ((player.tickCount + player.getId()) % 20 != 0) return;
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        if (player == null || player.isRemoved()) return;

        // --- Super Soul ---
        String soulId = player.getCapability(XenoCapabilities.XENO_DATA)
                .map(d -> d.getSuperSoulId())
                .orElse("");
        MobEffect wantedSoul = ModEffects.soulEffect(soulId);
        for (String id : new String[]{"warrior", "iron", "spark", "finisher", "balanced"}) {
            MobEffect e = ModEffects.soulEffect(id);
            if (e == null) continue;
            if (e == wantedSoul) {
                ensure(player, e, 0, REFRESH);
            } else {
                remove(player, e);
            }
        }

        // --- Combat skill summary ---
        int totalLv = 0;
        for (String sk : CombatSkills.DEFS.keySet()) {
            totalLv += CombatSkills.level(player, sk);
        }
        if (totalLv > 0) {
            ensure(player, ModEffects.COMBAT_TRAINING.get(), Math.min(11, totalLv - 1), REFRESH);
        } else {
            remove(player, ModEffects.COMBAT_TRAINING.get());
        }

        // --- Sparking active / ready ---
        if (XenoServerConfig.bt3SparkingEnabled) {
            if (Bt3SparkingSystem.isSparking(player)) {
                int left = Math.max(20, Bt3SparkingSystem.remainingSparkingTicks(player));
                ensure(player, ModEffects.SPARKING.get(), 0, left);
                remove(player, ModEffects.SPARKING_READY.get());
            } else {
                remove(player, ModEffects.SPARKING.get());
                if (Bt3SparkingSystem.getMeter(player.getUUID()) >= 99.5f) {
                    ensure(player, ModEffects.SPARKING_READY.get(), 0, REFRESH);
                } else {
                    remove(player, ModEffects.SPARKING_READY.get());
                }
            }
        } else {
            remove(player, ModEffects.SPARKING.get());
            remove(player, ModEffects.SPARKING_READY.get());
        }
    }

    /**
     * Apply / refresh a display effect: no particles, icon visible in inventory + HUD.
     */
    public static void ensure(ServerPlayer player, MobEffect effect, int amplifier, int durationTicks) {
        if (effect == null) return;
        MobEffectInstance cur = player.getEffect(effect);
        // ambient=false, visible=false (no particles), showIcon=true
        if (cur == null || cur.getAmplifier() != amplifier || cur.getDuration() < durationTicks / 2) {
            player.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, false, true));
        }
    }

    public static void remove(ServerPlayer player, MobEffect effect) {
        if (effect == null) return;
        if (player.hasEffect(effect)) {
            player.removeEffect(effect);
        }
    }
}
