package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcAuraPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side aura flag + sync. The visible aura is DragonMineZ's real shader mesh
 * drawn on the client ({@code NpcAuraClient}) — not vanilla dust. DMZ's
 * {@code DMZAuraLayer} only attaches to {@code AbstractClientPlayer}, so we reuse
 * its shader, {@code AuraMeshFactory} quads, and {@code kakarot_aura} texture.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcAuraFx {
    private static final Map<UUID, Boolean> ACTIVE = new ConcurrentHashMap<>();
    private static final double SYNC_RANGE_SQ = 128.0 * 128.0;

    private NpcAuraFx() {}

    public static void setActive(LivingEntity entity, boolean on) {
        if (entity == null) {
            return;
        }
        if (on) {
            ACTIVE.put(entity.getUUID(), Boolean.TRUE);
        } else {
            ACTIVE.remove(entity.getUUID());
        }
        sync(entity);
    }

    public static void sync(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.level().isClientSide()) {
            return;
        }
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(living);
        boolean effective = effectiveOn(living, profile);
        if (effective && living.isAlive()) {
            ACTIVE.put(living.getUUID(), Boolean.TRUE);
        } else {
            ACTIVE.remove(living.getUUID());
        }
        syncState(living, effective, profile);
    }

    /** Hide the current visual without erasing the profile's desired post-respawn aura state. */
    public static void hide(LivingEntity living) {
        if (living == null || living.level().isClientSide()) {
            return;
        }
        ACTIVE.remove(living.getUUID());
        syncState(living, false, NpcCombatProfile.read(living));
    }

    private static void syncState(LivingEntity living, boolean on, NpcCombatProfile profile) {
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }
        NpcAuraResolver.Resolved resolved = NpcAuraResolver.resolve(profile);
        NpcAuraPacket packet = new NpcAuraPacket(living.getUUID(), on,
                NpcCombatProfile.clampAuraScale(profile.auraScale), resolved);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(living) > SYNC_RANGE_SQ) {
                continue;
            }
            ModNetwork.sendToPlayer(viewer, packet);
        }
    }

    public static boolean isActive(UUID id) {
        return id != null && ACTIVE.containsKey(id);
    }

    static java.util.Set<UUID> activeIds() {
        return ACTIVE.keySet();
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(living);
        if (effectiveOn(living, profile)) {
            ACTIVE.put(living.getUUID(), Boolean.TRUE);
            sync(living);
        } else {
            ACTIVE.remove(living.getUUID());
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity living)) {
            return;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(living);
        NpcAuraResolver.Resolved resolved = NpcAuraResolver.resolve(profile);
        boolean on = effectiveOn(living, profile) && living.isAlive();
        if (!on && !resolved.lightning()) {
            return;
        }
        ModNetwork.sendToPlayer(player, new NpcAuraPacket(living.getUUID(), on,
                NpcCombatProfile.clampAuraScale(profile.auraScale), resolved));
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            return;
        }
        if (entity.isRemoved()) {
            ACTIVE.remove(entity.getUUID());
        }
    }

    /**
     * Same priority DragonMineZ uses for players: live form aura color, then a stored
     * custom color, then ki color. White is last-resort (that was the ice-globe tint).
     */
    public static int resolveAuraRgb(NpcCombatProfile profile) {
        return NpcAuraResolver.resolve(profile).particleRgb();
    }

    public static boolean effectiveOn(LivingEntity living, NpcCombatProfile profile) {
        if (profile == null) return false;
        return profile.auraOn || !profile.formGroup.isBlank() || !profile.stackGroup.isBlank()
                || (living != null && NpcTransformSystem.isHolding(living.getUUID()));
    }
}
