package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcAppearancePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcAppearanceFx {
    private static final double SYNC_RANGE_SQ = 128.0 * 128.0;

    private NpcAppearanceFx() {}

    public static void sync(Entity entity) {
        if (!(entity instanceof LivingEntity living)
                || living.level().isClientSide()
                || !NpcCombatProfile.hasProfile(living)
                || !(living.level() instanceof ServerLevel level)) {
            return;
        }
        NpcAppearancePacket packet = packet(living);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(living) <= SYNC_RANGE_SQ) {
                ModNetwork.sendToPlayer(viewer, packet);
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof LivingEntity living
                && NpcCombatProfile.hasProfile(living)) {
            ModNetwork.sendToPlayer(player, packet(living));
            NpcTransformSystem.syncHold(player, living);
        }
    }

    private static NpcAppearancePacket packet(LivingEntity living) {
        NpcCombatProfile profile = NpcCombatProfile.read(living);
        return new NpcAppearancePacket(living.getUUID(), profile.raceId,
                profile.formGroup, profile.formId,
                profile.hairEnabled, profile.hairCode, profile.hairColor,
                profile.strength, profile.strikePower, profile.resistance,
                profile.vitality, profile.kiPower, profile.energy,
                profile.auraColor, profile.auraScale,
                (profile.appearance == null ? new NpcDmzAppearance() : profile.appearance).toTag(),
                profile.visualOptionsTag());
    }
}
