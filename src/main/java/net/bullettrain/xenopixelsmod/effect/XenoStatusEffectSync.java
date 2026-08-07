package net.bullettrain.xenopixelsmod.effect;

import net.neoforged.fml.common.EventBusSubscriber;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.combat.Bt3SparkingSystem;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Mirrors Super Soul / skill / sparking state into vanilla {@link MobEffect}s
 * so they appear in the inventory (E) effects panel and the HUD icon strip.
 *
 * <p>Display-only: no attribute modifiers; combat still reads capability + config.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoStatusEffectSync {
    private static final int REFRESH = 80; // 4s — long enough that inventory doesn't flicker
    private static final String[] SOUL_EFFECT_IDS = {
            "warrior", "iron", "spark", "finisher", "balanced"
    };

    private XenoStatusEffectSync() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Stagger by UUID so 30+ players don't all refresh the same tick
        if ((player.tickCount + player.getId()) % 20 != 0) return;
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        if (player == null || player.isRemoved()) return;

        // --- Super Soul ---
        XenoPlayerData xenoData = XenoCapabilities.get(player).orElse(null);
        String soulId = xenoData != null ? xenoData.getSuperSoulId() : "";
        Holder<MobEffect> wantedSoul = ModEffects.soulEffect(soulId);
        for (String id : SOUL_EFFECT_IDS) {
            Holder<MobEffect> e = ModEffects.soulEffect(id);
            if (e == null) continue;
            if (e == wantedSoul) {
                ensure(player, e, 0, REFRESH);
            } else {
                remove(player, e);
            }
        }

        // --- Combat skill summary ---
        int totalLv = 0;
        if (xenoData != null) {
            for (String sk : CombatSkills.DEFS.keySet()) {
                totalLv += xenoData.getSkillLevel(sk);
            }
        }
        if (totalLv > 0) {
            ensure(player, ModEffects.COMBAT_TRAINING, Math.min(11, totalLv - 1), REFRESH);
        } else {
            remove(player, ModEffects.COMBAT_TRAINING);
        }

        // --- Sparking active / ready ---
        if (XenoServerConfig.bt3SparkingEnabled) {
            if (Bt3SparkingSystem.isSparking(player)) {
                int left = Math.max(20, Bt3SparkingSystem.remainingSparkingTicks(player));
                ensure(player, ModEffects.SPARKING, 0, left);
                remove(player, ModEffects.SPARKING_READY);
            } else {
                remove(player, ModEffects.SPARKING);
                if (Bt3SparkingSystem.getMeter(player.getUUID()) >= 99.5f) {
                    ensure(player, ModEffects.SPARKING_READY, 0, REFRESH);
                } else {
                    remove(player, ModEffects.SPARKING_READY);
                }
            }
        } else {
            remove(player, ModEffects.SPARKING);
            remove(player, ModEffects.SPARKING_READY);
        }
    }

    /**
     * Apply / refresh a display effect: no particles, icon visible in inventory + HUD.
     */
    public static void ensure(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int durationTicks) {
        if (effect == null) return;
        MobEffectInstance cur = player.getEffect(effect);
        // ambient=false, visible=false (no particles), showIcon=true
        if (cur == null || cur.getAmplifier() != amplifier || cur.getDuration() < durationTicks / 2) {
            player.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, false, false, true));
        }
    }

    public static void remove(ServerPlayer player, Holder<MobEffect> effect) {
        if (effect == null) return;
        if (player.hasEffect(effect)) {
            player.removeEffect(effect);
        }
    }
}
