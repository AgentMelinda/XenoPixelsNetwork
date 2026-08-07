package net.bullettrain.xenopixelsmod.combat;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side BT3 phase-1: guard state, STM drain, super-counter windows.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class Bt3CombatEvents {
    // Forge events and handled packets mutate these on the server thread.
    private static final Map<UUID, Long> GUARDING = new HashMap<>();
    private static final Map<UUID, Integer> COUNTER_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Integer> GUARD_STUN_UNTIL = new HashMap<>();

    private Bt3CombatEvents() {}

    public static void setGuarding(ServerPlayer player, boolean on) {
        if (player == null) return;
        if (on) {
            GUARDING.put(player.getUUID(), player.level().getGameTime());
        } else {
            GUARDING.remove(player.getUUID());
        }
    }

    public static boolean isGuarding(ServerPlayer player) {
        return player != null && GUARDING.containsKey(player.getUUID());
    }

    public static boolean isGuardStunned(ServerPlayer player) {
        if (player == null) return false;
        Integer until = GUARD_STUN_UNTIL.get(player.getUUID());
        return until != null && player.tickCount < until;
    }

    public static void openCounterWindow(ServerPlayer player) {
        if (player == null || !XenoServerConfig.bt3SuperCounterEnabled) return;
        int until = player.tickCount + Math.max(4, XenoServerConfig.superCounterWindowTicks);
        COUNTER_UNTIL_TICK.put(player.getUUID(), until);
    }

    public static boolean consumeCounterWindow(ServerPlayer player) {
        if (player == null) return false;
        Integer until = COUNTER_UNTIL_TICK.get(player.getUUID());
        if (until == null || player.tickCount > until) {
            COUNTER_UNTIL_TICK.remove(player.getUUID());
            return false;
        }
        COUNTER_UNTIL_TICK.remove(player.getUUID());
        return true;
    }

    public static boolean hasCounterWindow(ServerPlayer player) {
        if (player == null) return false;
        Integer until = COUNTER_UNTIL_TICK.get(player.getUUID());
        return until != null && player.tickCount <= until;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer defender)) return;
        if (!XenoServerConfig.bt3CombatEnabled) return;

        // Super-counter window after any hit (if enabled)
        if (XenoServerConfig.bt3SuperCounterEnabled && event.getNewDamage() > 0.05f) {
            openCounterWindow(defender);
        }

        if (!XenoServerConfig.bt3GuardEnabled || !isGuarding(defender)) return;
        if (isGuardStunned(defender)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        Resources res = readResources(defender);
        float cost = XenoServerConfig.guardStaminaPerHit;
        if (res != null && res.getCurrentStamina() < cost * 0.5f) {
            // Guard break
            setGuarding(defender, false);
            DmzAnimHelper.broadcastBlockStop(defender);
            int stun = Math.max(5, XenoServerConfig.guardBreakStunTicks);
            GUARD_STUN_UNTIL.put(defender.getUUID(), defender.tickCount + stun);
            defender.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 2, false, true));
            defender.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stun, 0, false, true));
            defender.displayClientMessage(Component.literal("§cGUARD BREAK"), true);
            return;
        }
        if (res != null) {
            res.removeStamina(Math.min(res.getCurrentStamina(), cost));
        }
        float red = Math.max(0f, Math.min(0.95f, XenoServerConfig.guardDamageReduction
                + net.bullettrain.xenopixelsmod.features.progression.CombatSkills.guardBonus(defender)));
        event.setNewDamage(event.getNewDamage() * (1f - red));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!XenoServerConfig.bt3CombatEnabled || !XenoServerConfig.bt3GuardEnabled) return;
        int serverTick = event.getServer().getTickCount();

        // Entries drain once per second; polling five times per second preserves timing
        // without resolving every guarding UUID on every server tick.
        if (serverTick % 4 == 0 && !GUARDING.isEmpty()) {
            Iterator<Map.Entry<UUID, Long>> it = GUARDING.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
                if (p == null || !p.isAlive()) {
                    it.remove();
                    continue;
                }
                long now = p.level().getGameTime();
                long last = e.getValue();
                if (now - last < 20) continue;
                e.setValue(now);
                Resources res = readResources(p);
                float drain = XenoServerConfig.guardStaminaPerSec;
                if (res != null && drain > 0f) {
                    if (res.getCurrentStamina() < drain) {
                        it.remove();
                        DmzAnimHelper.broadcastBlockStop(p);
                        p.displayClientMessage(Component.literal("§eGuard dropped — low stamina"), true);
                    } else {
                        res.removeStamina(drain);
                    }
                }
            }
        }

        // These are queried lazily by exact UUID; bulk pruning once per second is enough.
        if (serverTick % 20 == 0) {
            COUNTER_UNTIL_TICK.entrySet().removeIf(en -> en.getValue() < serverTick - 40);
            GUARD_STUN_UNTIL.entrySet().removeIf(en -> en.getValue() < serverTick - 40);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            UUID id = p.getUUID();
            GUARDING.remove(id);
            COUNTER_UNTIL_TICK.remove(id);
            GUARD_STUN_UNTIL.remove(id);
        }
    }

    private static Resources readResources(ServerPlayer player) {
        try {
            var opt = StatsProvider.get(StatsCapability.INSTANCE, player);
            StatsData data = opt.orElse(null);
            return data != null ? data.getResources() : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
