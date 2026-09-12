package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads CustomNPCs / My NPCs active quests via verified PlayerData.questData.activeQuests.
 * Does not invent a start API — take those quests from the NPC.
 */
public final class NpcCnpcQuests {
    public record Entry(int id, String title) {}

    private static final String[] PLAYER_DATA = {
            "espi.mynpcs.controllers.data.PlayerData",
            "noppes.npcs.controllers.data.PlayerData"
    };

    private NpcCnpcQuests() {}

    public static List<Entry> active(ServerPlayer player) {
        if (player == null) return List.of();
        List<Entry> out = new ArrayList<>();
        for (String className : PLAYER_DATA) {
            try {
                Class<?> type = Class.forName(className);
                Object data = type.getMethod("get", net.minecraft.world.entity.player.Player.class)
                        .invoke(null, player);
                if (data == null) continue;
                Field questDataField = type.getField("questData");
                Object questData = questDataField.get(data);
                if (questData == null) continue;
                Field activeField = questData.getClass().getField("activeQuests");
                Object map = activeField.get(questData);
                if (!(map instanceof Map<?, ?> active)) continue;
                for (Map.Entry<?, ?> e : active.entrySet()) {
                    Object questDataRow = e.getValue();
                    if (questDataRow == null) continue;
                    Object quest = questDataRow.getClass().getField("quest").get(questDataRow);
                    if (quest == null) continue;
                    int id = (Integer) quest.getClass().getMethod("getId").invoke(quest);
                    String title = String.valueOf(quest.getClass().getMethod("getName").invoke(quest));
                    out.add(new Entry(id, title == null || title.isBlank() ? "Quest " + id : title));
                }
                if (!out.isEmpty()) return out;
            } catch (ReflectiveOperationException ignored) {
                // Mod not loaded or API moved.
            }
        }
        return out;
    }

    public static String formatListLine(Entry entry) {
        return "npc:" + entry.id() + " — " + entry.title();
    }
}
