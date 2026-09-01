package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repairs only CustomNPCs entities that explicitly carry an Xeno NPC profile.
 * Ordinary CustomNPC statues keep their configured NoAI setting untouched.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class NpcProfileLifecycle {
    private static final int RESPAWN_GRACE_TICKS = 40;
    private static final Map<UUID, Integer> RESPAWN_DEADLINES = new ConcurrentHashMap<>();
    private static final Map<UUID, LivingEntity> PROFILED = new ConcurrentHashMap<>();

    private NpcProfileLifecycle() {}

    public static void track(Entity entity) {
        if (entity instanceof LivingEntity living && !entity.level().isClientSide()
                && NpcCombatProfile.hasProfile(entity)) {
            PROFILED.put(entity.getUUID(), living);
        }
    }

    /** Called after profile writes and entity loads to prevent stale NoAI NBT freezing profiled NPCs. */
    public static void repairAi(Entity entity) {
        if (!(entity instanceof Mob mob)
                || entity.level().isClientSide()
                || !NpcCombatProfile.hasProfile(entity)
                || !isCustomNpc(entity)) {
            return;
        }
        if (mob.isNoAi()) {
            mob.setNoAi(false);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            Entity entity = event.getEntity();
            repairAi(entity);
            if (NpcCombatProfile.hasProfile(entity)) {
                track(entity);
                NpcCombatProfile profile = NpcCombatProfile.read(entity);
                NpcVitalitySync.apply(entity, profile);
                if (entity instanceof LivingEntity living) NpcFormAttributeSync.apply(living, profile);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity npc = event.getEntity();
        if (npc.level().isClientSide() || !NpcCombatProfile.hasProfile(npc) || !isCustomNpc(npc)) {
            return;
        }
        NpcKiAim.cancel(npc);
        NpcKiCooldowns.clear(npc.getUUID());
        NpcTransformSystem.cancel(npc);
        NpcStrikeDispatcher.cancel(npc.getUUID());
        NpcAuraFx.hide(npc);

        if (npc.level() instanceof ServerLevel level && respawnType(npc) == 0) {
            int respawnTicks = Math.max(1, respawnSeconds(npc)) * 20;
            RESPAWN_DEADLINES.put(npc.getUUID(),
                    level.getServer().getTickCount() + respawnTicks + RESPAWN_GRACE_TICKS);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            NpcKiAim.cancel(living);
        } else {
            NpcKiAim.cancel(entity.getUUID());
        }
        NpcTransformSystem.cancel(entity.getUUID());
        NpcStrikeDispatcher.cancel(entity.getUUID());
        PROFILED.remove(entity.getUUID());
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != Entity.RemovalReason.UNLOADED_TO_CHUNK
                && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER
                && reason != Entity.RemovalReason.CHANGED_DIMENSION) {
            RESPAWN_DEADLINES.remove(entity.getUUID());
            NpcKiCooldowns.clear(entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        NpcKiCooldowns.tick();
        int serverTick = event.getServer().getTickCount();
        PROFILED.entrySet().removeIf(entry -> {
            LivingEntity npc = entry.getValue();
            if (npc == null || npc.isRemoved()) return true;
            if (npc.isAlive()) {
                NpcCombatProfile profile = NpcCombatProfile.read(npc);
                NpcFormDrainSystem.tick(npc, profile, serverTick);
                profile = NpcCombatProfile.read(npc);
                NpcResources.tick(npc, profile);
                if (serverTick % 40 == Math.floorMod(npc.getId(), 40)) {
                    NpcAuraFx.sync(npc);
                }
            }
            return false;
        });
        if (RESPAWN_DEADLINES.isEmpty()) {
            return;
        }
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, Integer>> it = RESPAWN_DEADLINES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            LivingEntity npc = find(event.getServer(), entry.getKey());
            if (npc == null) {
                continue; // Chunk may be unloaded; retain the deadline until it is loaded again.
            }
            if (npc.isAlive()) {
                repairAi(npc);
                NpcAuraFx.sync(npc);
                it.remove();
                continue;
            }
            if (now < entry.getValue()) {
                continue; // Let CustomNPCs' native timer run first.
            }
            if (respawnType(npc) != 0 || !invokeReset(npc)) {
                it.remove();
                continue;
            }
            repairAi(npc);
            NpcAuraFx.sync(npc);
            it.remove();
        }
    }

    private static LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static boolean isCustomNpc(Entity entity) {
        try {
            return Class.forName("noppes.npcs.entity.EntityNPCInterface").isInstance(entity);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static Object stats(LivingEntity npc) {
        try {
            Field field = Class.forName("noppes.npcs.entity.EntityNPCInterface").getField("stats");
            return field.get(npc);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static int respawnType(LivingEntity npc) {
        Object stats = stats(npc);
        if (stats == null) {
            return -1;
        }
        try {
            return (Integer) stats.getClass().getMethod("getRespawnType").invoke(stats);
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    private static int respawnSeconds(LivingEntity npc) {
        Object stats = stats(npc);
        if (stats == null) {
            return 1;
        }
        try {
            return (Integer) stats.getClass().getMethod("getRespawnTime").invoke(stats);
        } catch (ReflectiveOperationException ignored) {
            return 1;
        }
    }

    private static boolean invokeReset(LivingEntity npc) {
        try {
            Method reset = Class.forName("noppes.npcs.entity.EntityNPCInterface").getMethod("reset");
            reset.invoke(npc);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
