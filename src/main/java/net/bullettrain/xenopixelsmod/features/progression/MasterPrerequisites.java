package net.bullettrain.xenopixelsmod.features.progression;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCnpcQuests;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-side, data-driven gates for DMZ master dialogue extensions. */
public final class MasterPrerequisites {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Requirement> REQUIREMENTS = new LinkedHashMap<>();
    private static Path path;

    private MasterPrerequisites() {}

    public record Requirement(String questId, String externalQuestTitle, String itemId,
                              int itemCount, String skillId, int skillLevel) {
        public Requirement {
            questId = blankToEmpty(questId);
            externalQuestTitle = blankToEmpty(externalQuestTitle);
            itemId = blankToEmpty(itemId);
            skillId = blankToEmpty(skillId);
            itemCount = Math.max(0, itemCount);
            skillLevel = Math.max(0, skillLevel);
        }
    }

    public record Result(boolean allowed, String message) {}

    public static synchronized void load(Path configDir) {
        path = configDir.resolve("xenopixelsmod-master-prerequisites.json");
        REQUIREMENTS.clear();
        if (!Files.exists(path)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null && data.requirements != null) REQUIREMENTS.putAll(data.requirements);
        } catch (IOException | RuntimeException ignored) {
            REQUIREMENTS.clear();
        }
    }

    public static synchronized void save() {
        if (path == null) return;
        try {
            Files.createDirectories(path.getParent());
            Data data = new Data();
            data.requirements = new LinkedHashMap<>(REQUIREMENTS);
            try (Writer writer = Files.newBufferedWriter(path)) { GSON.toJson(data, writer); }
        } catch (IOException ignored) {
            // A missing optional config must not prevent the server from starting.
        }
    }

    public static synchronized Map<String, Requirement> snapshot() {
        return Map.copyOf(REQUIREMENTS);
    }

    public static synchronized void put(String masterId, Requirement requirement) {
        if (masterId == null || masterId.isBlank() || requirement == null) return;
        REQUIREMENTS.put(normalize(masterId), requirement);
        save();
    }

    public static synchronized void remove(String masterId) {
        REQUIREMENTS.remove(normalize(masterId));
        save();
    }

    public static Result check(ServerPlayer player, String masterId) {
        Requirement requirement = REQUIREMENTS.get(normalize(masterId));
        if (requirement == null) return new Result(true, "");
        if (!requirement.questId().isEmpty()) {
            var data = XenoCapabilities.get(player).orElse(null);
            // Reads the completed set, not the current quest. The old check wanted the id to match
            // AND progress to be at target - but completion erased the id in the same instant, so
            // there was no moment at which both could be true. A master gated on a quest was
            // locked forever, and nothing said why.
            if (data == null || !data.quests().hasCompleted(requirement.questId())) {
                return new Result(false, "Complete quest " + requirement.questId() + " first");
            }
        }
        if (!requirement.externalQuestTitle().isEmpty()) {
            boolean found = NpcCnpcQuests.active(player).stream()
                    .anyMatch(q -> requirement.externalQuestTitle().equalsIgnoreCase(q.title()));
            if (!found) return new Result(false, "Complete the required NPC quest first");
        }
        if (!requirement.itemId().isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(requirement.itemId());
            Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
            int count = 0;
            if (item != null) {
                for (var stack : player.getInventory().items) {
                    if (stack.is(item)) count += stack.getCount();
                }
            }
            if (item == null || count < Math.max(1, requirement.itemCount())) {
                return new Result(false, "Bring the required item first");
            }
        }
        if (!requirement.skillId().isEmpty()
                && CombatSkills.level(player, requirement.skillId()) < requirement.skillLevel()) {
            return new Result(false, "Unlock skill " + requirement.skillId() + " first");
        }
        return new Result(true, "");
    }

    private static String normalize(String id) {
        String value = id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
        return value.startsWith("dragonminez:") ? value.substring("dragonminez:".length()) : value;
    }

    private static String blankToEmpty(String value) { return value == null ? "" : value.trim(); }

    private static final class Data {
        Map<String, Requirement> requirements = new LinkedHashMap<>();
    }
}
