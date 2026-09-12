package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lightweight Parallel Quest catalog. */
public final class ParallelQuests {
    public static final Map<String, QuestDef> QUESTS = new LinkedHashMap<>();

    static {
        QUESTS.put("kill_mobs", new QuestDef("kill_mobs", "Hunt", "Defeat 10 hostiles", 10));
        QUESTS.put("kill_players", new QuestDef("kill_players", "Rivalry", "Defeat 3 players", 3));
        QUESTS.put("dummy_session", new QuestDef("dummy_session", "Training", "Deal 500 damage to a training dummy", 500));
    }

    private ParallelQuests() {}

    public record QuestDef(String id, String title, String desc, int target) {}

    public static String start(ServerPlayer player, String questId) {
        if (questId != null && questId.toLowerCase().startsWith("npc:")) {
            return "Take that CustomNPCs quest from the NPC — /xenoquest cannot accept it for you";
        }
        QuestDef def = QUESTS.get(questId == null ? "" : questId.toLowerCase());
        if (def == null) return "Unknown quest. Try: kill_mobs, kill_players, dummy_session or see npc: ids from /xenoquest list";
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return "No player data";
        if (data.hasActiveQuest()) {
            return "Already on quest: " + data.getQuestId()
                    + " (" + data.getQuestProgress() + "/" + data.getQuestTarget() + "). /xenoquest abort";
        }
        data.startQuest(def.id, def.target);
        player.displayClientMessage(Component.literal(
                "§bQuest started: §f" + def.title() + " §7— " + def.desc()), false);
        return null;
    }

    public static String status(ServerPlayer player) {
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        if (data == null) return "No player data";
        if (!data.hasActiveQuest()) return "No active quest. /xenoquest start <id>";
        return data.getQuestId() + ": " + data.getQuestProgress() + "/" + data.getQuestTarget();
    }
}
