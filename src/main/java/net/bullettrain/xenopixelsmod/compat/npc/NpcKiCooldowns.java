package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** DMZ-compatible per-NPC technique cooldowns for entities without StatsData. */
public final class NpcKiCooldowns {
    private static final Map<UUID, Map<String, Integer>> ACTIVE = new ConcurrentHashMap<>();

    private NpcKiCooldowns() {}

    public static boolean ready(LivingEntity npc, String id) {
        if (npc == null || id == null || id.isBlank()) return false;
        return remaining(npc, id) <= 0;
    }

    public static int remaining(LivingEntity npc, String id) {
        if (npc == null || id == null) return 0;
        Map<String, Integer> map = ACTIVE.get(npc.getUUID());
        return map == null ? 0 : Math.max(0, map.getOrDefault(normalize(id), 0));
    }

    public static void consume(LivingEntity npc, KiAttackData data, float chargeFactor) {
        if (npc == null || data == null) return;
        int ticks = Math.max(1, (int) Math.ceil(data.getActualCooldown() * chargeFactor));
        ACTIVE.computeIfAbsent(npc.getUUID(), ignored -> new ConcurrentHashMap<>())
                .put(normalize(data.getId()), ticks);
    }

    public static void consume(LivingEntity npc, StrikeAttackData data) {
        if (npc == null || data == null) return;
        ACTIVE.computeIfAbsent(npc.getUUID(), ignored -> new ConcurrentHashMap<>())
                .put(normalize(data.getId()), Math.max(1, data.getActualCooldown()));
    }

    public static void clear(LivingEntity npc, String id) {
        if (npc == null || id == null) return;
        Map<String, Integer> map = ACTIVE.get(npc.getUUID());
        if (map != null) {
            map.remove(normalize(id));
            if (map.isEmpty()) ACTIVE.remove(npc.getUUID());
        }
    }

    public static void tick() {
        if (ACTIVE.isEmpty()) return;
        ACTIVE.entrySet().removeIf(entry -> {
            entry.getValue().replaceAll((ignored, ticks) -> ticks - 1);
            entry.getValue().entrySet().removeIf(cooldown -> cooldown.getValue() <= 0);
            return entry.getValue().isEmpty();
        });
    }

    public static void clear(UUID id) {
        if (id != null) ACTIVE.remove(id);
    }

    private static String normalize(String id) {
        return id.toLowerCase(java.util.Locale.ROOT);
    }
}
