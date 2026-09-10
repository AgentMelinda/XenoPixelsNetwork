package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcCombatProtection {
    private NpcCombatProtection() {}

    public static boolean isKnockable(Entity entity) {
        NpcCombatProfile profile = profile(entity);
        return profile == null || profile.knockable;
    }

    public static boolean isPunchable(Entity entity) {
        NpcCombatProfile profile = profile(entity);
        return profile == null || profile.punchable;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!isPunchable(event.getTarget())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!isPunchable(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (!isKnockable(event.getEntity())) event.setCanceled(true);
    }

    private static NpcCombatProfile profile(Entity entity) {
        if (!(entity instanceof LivingEntity) || !NpcTypes.isNpc(entity)
                || !NpcCombatProfile.hasProfile(entity)) return null;
        return NpcCombatProfile.readCached(entity);
    }
}
