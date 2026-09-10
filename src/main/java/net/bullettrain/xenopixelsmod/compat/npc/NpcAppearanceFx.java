package net.bullettrain.xenopixelsmod.compat.npc;

import com.mojang.authlib.GameProfile;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcAppearancePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;

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
        NpcFormDisplayTuning.applyCurrentSize(living, profile);
        return new NpcAppearancePacket(living.getUUID(), profile.raceId,
                profile.formGroup, profile.formId,
                profile.hairEnabled, profile.hairCode, profile.hairColor,
                profile.strength, profile.strikePower, profile.resistance,
                profile.vitality, profile.kiPower, profile.energy, profile.authoritative,
                profile.auraColor, NpcFormDisplayTuning.effectiveAuraScale(profile),
                (profile.appearance == null ? new NpcDmzAppearance() : profile.appearance).toTag(),
                profile.visualOptionsTag(),
                profile.skinPlayer, profile.skinUrl, resolveSkinUuid(living, profile.skinPlayer));
    }

    /**
     * Cached UUID for a player-name skin, used by the FULL DMZ renderer to build a
     * GameProfile. Blank when the name is unknown to the profile cache; an async
     * lookup is kicked off and re-syncs the NPC once it resolves.
     */
    private static String resolveSkinUuid(LivingEntity living, String skinPlayer) {
        if (skinPlayer == null || skinPlayer.isBlank()
                || !(living.level() instanceof ServerLevel serverLevel)) {
            return "";
        }
        MinecraftServer server = serverLevel.getServer();
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) {
            return "";
        }
        Optional<GameProfile> cached = cache.get(skinPlayer);
        if (cached.isPresent()) {
            return cached.get().getId().toString();
        }
        cache.getAsync(skinPlayer).thenAcceptAsync(resolved -> {
            if (resolved.isPresent()) {
                sync(living);
            }
        }, server);
        return "";
    }
}
