package net.bullettrain.xenopixelsmod.features.progression;

import net.bullettrain.xenopixelsmod.capability.XenoCapabilities;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hides dialogue options whose quest the player is not allowed to take. */
public final class QuestDialogueFilter {

    private QuestDialogueFilter() {}

    public static XenoDialogue forPlayer(ServerPlayer player, XenoDialogue dialogue) {
        if (player == null || dialogue == null) return dialogue;
        XenoPlayerData data = XenoCapabilities.get(player).orElse(null);
        return filterUnavailable(dialogue,
                option -> canOffer(player, data, option.quest()));
    }

    static XenoDialogue filterUnavailable(XenoDialogue dialogue,
                                          java.util.function.Predicate<XenoDialogue.Option> canOffer) {
        if (dialogue == null) return null;
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, XenoDialogue.Node> entry : dialogue.nodes().entrySet()) {
            List<XenoDialogue.Option> kept = new ArrayList<>();
            List<XenoDialogue.Option> original = entry.getValue().options();
            for (int index = 0; index < original.size(); index++) {
                XenoDialogue.Option option = original.get(index);
                if (option.type() == XenoDialogue.OptionType.QUEST && !canOffer.test(option)) {
                    continue;
                }
                int sourceIndex = option.sourceIndex() >= 0 ? option.sourceIndex() : index;
                kept.add(new XenoDialogue.Option(option.text(), option.type(), option.target(),
                        option.quest(), option.command(), sourceIndex, option.palette()));
            }
            nodes.put(entry.getKey(), new XenoDialogue.Node(
                    entry.getValue().text(), List.copyOf(kept), entry.getValue().palette()));
        }
        if (!nodes.containsKey(dialogue.start())) return null;
        return new XenoDialogue(dialogue.start(), nodes);
    }

    public static boolean canOffer(ServerPlayer player, XenoPlayerData data, String questId) {
        ParallelQuests.QuestDef def = ParallelQuests.definition(questId);
        if (def == null) return false;
        return QuestRuntime.available(player, data, def.availability());
    }
}
