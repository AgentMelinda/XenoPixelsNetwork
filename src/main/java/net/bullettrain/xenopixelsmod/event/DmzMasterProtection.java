package net.bullettrain.xenopixelsmod.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.dragonminez.common.init.entities.MastersEntity;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

/**
 * Keeps DMZ masters (Goku, Beerus, Frieza trainers, …) from being punched,
 * damaged, or knocked around by players — public-server QoL.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DmzMasterProtection {
    private DmzMasterProtection() {}

    public static boolean isDmzMaster(Entity entity) {
        if (entity == null) return false;
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId != null) {
            String id = typeId.toString();
            if (id.startsWith("dragonminez:saga_")) return false;
            if (isDmzMasterTypeId(id)) return true;
        }
        try {
            return entity instanceof MastersEntity;
        } catch (Throwable t) {
            String className = entity.getClass().getName();
            return className.startsWith("com.dragonminez.") && className.contains("Master");
        }
    }

    public static boolean isDmzMasterTypeId(String typeId) {
        if (typeId == null) return false;
        return typeId.startsWith("dragonminez:master_");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttackEntity(AttackEntityEvent event) {
        if (!XenoServerConfig.protectDmzMasters) return;
        if (!isDmzMaster(event.getTarget())) return;
        event.setCanceled(true);
        Player p = event.getEntity();
        if (p instanceof ServerPlayer sp && sp.tickCount % 20 == 0) {
            sp.displayClientMessage(Component.literal("§7Masters cannot be attacked"), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingIncomingDamageEvent event) {
        if (!XenoServerConfig.protectDmzMasters) return;
        LivingEntity victim = event.getEntity();
        if (!isDmzMaster(victim)) return;
        // Block player-sourced hits (and projectiles owned by players)
        Entity src = event.getSource().getEntity();
        if (src instanceof Player || event.getSource().getDirectEntity() instanceof Player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (!XenoServerConfig.protectDmzMasters) return;
        if (isDmzMaster(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
