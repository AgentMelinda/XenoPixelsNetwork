package net.bullettrain.xenopixelsmod.combat;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.progression.CombatSkills;
import net.bullettrain.xenopixelsmod.network.Bt3CombatPacket;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BT3 Counter Attack System - Allows players to counter-attack during perfect timing windows
 * after being hit or during guard parries.
 */
@Mod.EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class Bt3CounterAttackSystem {
    private static final Map<UUID, Integer> COUNTER_WINDOW_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> PERFECT_COUNTER_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COUNTER_COOLDOWN_TICKS = new ConcurrentHashMap<>();
    
    private Bt3CounterAttackSystem() {}
    
    /**
     * Opens a counter attack window for the player after being hit.
     */
    public static void openCounterWindow(ServerPlayer player, boolean isPerfect) {
        if (player == null || !XenoServerConfig.bt3CombatEnabled) return;
        
        UUID id = player.getUUID();
        int cooldown = COUNTER_COOLDOWN_TICKS.getOrDefault(id, 0);
        if (cooldown > 0) {
            return; // Still on cooldown
        }
        
        int windowTicks = isPerfect ? 
            Math.max(8, XenoServerConfig.superCounterWindowTicks + 4) :
            Math.max(4, XenoServerConfig.superCounterWindowTicks);
        
        if (isPerfect) {
            PERFECT_COUNTER_TICKS.put(id, player.tickCount + windowTicks);
            player.displayClientMessage(Component.literal("§6§lPERFECT COUNTER WINDOW!"), true);
        } else {
            COUNTER_WINDOW_TICKS.put(id, player.tickCount + windowTicks);
            player.displayClientMessage(Component.literal("§eCounter window open!"), true);
        }
        
        // Sync to client using SUPER_COUNTER action type
        ModNetwork.sendToPlayer(new Bt3CombatPacket(Bt3CombatPacket.Action.SUPER_COUNTER, 0, isPerfect ? 2 : 1), player);
    }
    
    /**
     * Attempts to execute a counter attack.
     * @return true if counter was successful
     */
    public static boolean tryCounterAttack(ServerPlayer player) {
        if (player == null) return false;
        
        UUID id = player.getUUID();
        int currentTick = player.tickCount;
        
        // Check perfect counter first
        Integer perfectUntil = PERFECT_COUNTER_TICKS.get(id);
        if (perfectUntil != null && currentTick <= perfectUntil) {
            PERFECT_COUNTER_TICKS.remove(id);
            COUNTER_WINDOW_TICKS.remove(id);
            executeCounterAttack(player, true);
            return true;
        }
        
        // Check normal counter
        Integer normalUntil = COUNTER_WINDOW_TICKS.get(id);
        if (normalUntil != null && currentTick <= normalUntil) {
            COUNTER_WINDOW_TICKS.remove(id);
            executeCounterAttack(player, false);
            return true;
        }
        
        return false;
    }
    
    /**
     * Executes the counter attack with appropriate effects.
     */
    private static void executeCounterAttack(ServerPlayer player, boolean isPerfect) {
        // Set cooldown
        int cooldownTicks = isPerfect ? 100 : 150;
        COUNTER_COOLDOWN_TICKS.put(player.getUUID(), cooldownTicks);
        
        // Apply buff effects
        int buffDuration = isPerfect ? 100 : 60;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, buffDuration, isPerfect ? 2 : 1, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, buffDuration, isPerfect ? 1 : 0, false, true));
        
        // Display message
        Component msg = isPerfect ? 
            Component.literal("§6§lPERFECT COUNTER! §fDamage boosted!") :
            Component.literal("§eCOUNTER! §fBrief damage boost");
        player.displayClientMessage(msg, true);
        
        // Sync to client for visual effects
        ModNetwork.sendToPlayer(new Bt3CombatPacket(Bt3CombatPacket.Action.SUPER_COUNTER, 0, isPerfect ? 2 : 1), player);
    }
    
    /**
     * Checks if player has an active counter window.
     */
    public static boolean hasCounterWindow(ServerPlayer player) {
        if (player == null) return false;
        UUID id = player.getUUID();
        int currentTick = player.tickCount;
        
        Integer perfectUntil = PERFECT_COUNTER_TICKS.get(id);
        if (perfectUntil != null && currentTick <= perfectUntil) {
            return true;
        }
        
        Integer normalUntil = COUNTER_WINDOW_TICKS.get(id);
        return normalUntil != null && currentTick <= normalUntil;
    }
    
    /**
     * Gets remaining ticks on counter window (0 if none).
     */
    public static int getCounterWindowRemaining(ServerPlayer player) {
        if (player == null) return 0;
        UUID id = player.getUUID();
        int currentTick = player.tickCount;
        
        Integer perfectUntil = PERFECT_COUNTER_TICKS.get(id);
        if (perfectUntil != null && currentTick <= perfectUntil) {
            return perfectUntil - currentTick;
        }
        
        Integer normalUntil = COUNTER_WINDOW_TICKS.get(id);
        if (normalUntil != null && currentTick <= normalUntil) {
            return normalUntil - currentTick;
        }
        
        return 0;
    }
    
    /**
     * Checks if counter attack is on cooldown.
     */
    public static boolean isOnCooldown(ServerPlayer player) {
        if (player == null) return false;
        Integer cooldown = COUNTER_COOLDOWN_TICKS.get(player.getUUID());
        return cooldown != null && cooldown > 0;
    }
    
    /**
     * Gets remaining cooldown ticks.
     */
    public static int getCooldownRemaining(ServerPlayer player) {
        if (player == null) return 0;
        Integer cooldown = COUNTER_COOLDOWN_TICKS.get(player.getUUID());
        return cooldown != null ? Math.max(0, cooldown) : 0;
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!XenoServerConfig.bt3CombatEnabled) return;
        if (!(event.getEntity() instanceof ServerPlayer defender)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (event.getAmount() < 1.0f) return;
        
        // Chance to open counter window based on CombatSkills
        float chance = 0.3f + (CombatSkills.level(defender, CombatSkills.GUARD) * 0.1f);
        if (defender.getRandom().nextFloat() < chance) {
            // Check for perfect counter (low health or timed perfectly)
            boolean isPerfect = defender.getHealth() < defender.getMaxHealth() * 0.3f ||
                               Bt3SparkingSystem.isSparking(defender);
            openCounterWindow(defender, isPerfect);
        }
    }
    
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!XenoServerConfig.bt3CombatEnabled) return;
        if (!(event.getEntity() instanceof ServerPlayer attacker)) return;
        
        // Check if this attack is a counter attack
        if (tryCounterAttack(attacker)) {
            // Counter attack succeeded - damage will be boosted by effects
        }
    }
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        // Decrement cooldowns and clean up expired windows
        COUNTER_WINDOW_TICKS.entrySet().removeIf(entry -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            return player == null || entry.getValue() < player.tickCount;
        });
        
        PERFECT_COUNTER_TICKS.entrySet().removeIf(entry -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            return player == null || entry.getValue() < player.tickCount;
        });
        
        COUNTER_COOLDOWN_TICKS.entrySet().forEach(entry -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    COUNTER_COOLDOWN_TICKS.remove(entry.getKey());
                } else {
                    COUNTER_COOLDOWN_TICKS.put(entry.getKey(), remaining);
                }
            } else {
                COUNTER_COOLDOWN_TICKS.remove(entry.getKey());
            }
        });
    }
    
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        COUNTER_WINDOW_TICKS.remove(id);
        PERFECT_COUNTER_TICKS.remove(id);
        COUNTER_COOLDOWN_TICKS.remove(id);
    }
}
