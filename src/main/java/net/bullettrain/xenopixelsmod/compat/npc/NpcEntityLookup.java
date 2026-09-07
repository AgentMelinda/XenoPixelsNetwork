package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UUID → entity lookup that remembers which level an id was last found in.
 *
 * <p>Every per-tick NPC system here resolves its tracked ids by looping
 * {@code server.getAllLevels()} and calling {@code level.getEntity(uuid)} on each — so on a server
 * with several dimensions, most of that work is a guaranteed miss, repeated every tick per tracked
 * NPC. The remembered level turns the common case into one lookup; the full scan stays as the
 * fallback for a first lookup or an NPC that changed dimension.
 */
public final class NpcEntityLookup {
    private static final Map<UUID, ResourceKey<Level>> LAST_LEVEL = new ConcurrentHashMap<>();

    private NpcEntityLookup() {}

    /** The living entity with this id, or null when it is not loaded anywhere. */
    public static LivingEntity findLiving(MinecraftServer server, UUID id) {
        Entity entity = find(server, id);
        return entity instanceof LivingEntity living ? living : null;
    }

    /** As {@link #findLiving}, but also requires the entity to be alive. */
    public static LivingEntity findAlive(MinecraftServer server, UUID id) {
        LivingEntity living = findLiving(server, id);
        return living != null && living.isAlive() ? living : null;
    }

    public static Entity find(MinecraftServer server, UUID id) {
        if (server == null || id == null) {
            return null;
        }
        ResourceKey<Level> remembered = LAST_LEVEL.get(id);
        if (remembered != null) {
            ServerLevel level = server.getLevel(remembered);
            if (level != null) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    return entity;
                }
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().equals(remembered)) {
                continue; // already missed above
            }
            Entity entity = level.getEntity(id);
            if (entity != null) {
                LAST_LEVEL.put(id, level.dimension());
                return entity;
            }
        }
        return null;
    }

    /** Records where an entity is, so the first lookup after tracking starts is already direct. */
    public static void remember(Entity entity) {
        if (entity != null && entity.level() instanceof ServerLevel level) {
            LAST_LEVEL.put(entity.getUUID(), level.dimension());
        }
    }

    public static void forget(UUID id) {
        if (id != null) {
            LAST_LEVEL.remove(id);
        }
    }
}
