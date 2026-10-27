package net.bullettrain.xenopixelsmod.client.npc.dialog;

import com.dragonminez.client.gui.dialogue.NpcDialogueScreen;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcDialoguePacket;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcTravelPacket;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueText;
import net.bullettrain.xenopixelsmod.client.npc.speech.BubbleText;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Xeno dialogue actions with the actual DMZ screen, portrait, typewriter and option column. */
public final class DmzNpcDialoguePresentation {
    private static final Map<NpcDialogueScreen, Component> NAMES = new WeakHashMap<>();
    private final int entityId;
    private final XenoDialogue dialogue;
    private final String name;
    private final NpcDialogueScreen screen;
    private DmzNpcDialoguePresentation(int entityId, XenoDialogue dialogue, String name) {
        this.entityId = entityId;
        this.dialogue = dialogue;
        this.name = name == null ? "" : name;
        screen = Dmz22DialogueAccess.screen(entityId);
        NAMES.put(screen, Component.literal(this.name));
    }
    public static Component name(NpcDialogueScreen screen) { return NAMES.get(screen); }
    /** False when the installed DragonMineZ does not expose this build's screen contract. */
    public static boolean available() { return Dmz22DialogueAccess.ready(); }
    public static void open(int entityId, XenoDialogue dialogue, String name) {
        if (dialogue == null || dialogue.startNode() == null) return;
        DialogueBubbleSession.end();
        DmzNpcDialoguePresentation presentation = new DmzNpcDialoguePresentation(entityId, dialogue, name);
        presentation.show(dialogue.start());
        Minecraft.getInstance().setScreen(presentation.screen);
    }
    public static void completion(String title, String description, String reward) {
        String line = title + "\n\n" + description + (reward == null || reward.isBlank() ? "" : "\n\nRewards: " + reward);
        open(-1, new XenoDialogue("complete", Map.of("complete", new XenoDialogue.Node(line,
                List.of(new XenoDialogue.Option("Continue", XenoDialogue.OptionType.QUIT, "", "", ""))))), "Quest complete");
    }
    private Component text(String text) {
        var player = Minecraft.getInstance().player;
        return BubbleText.styled(XenoDialogueText.resolve(text,
                player == null ? "" : player.getName().getString(), name));
    }
    private void show(String nodeId) {
        XenoDialogue.Node node = dialogue.nodes().get(nodeId);
        if (node == null) { screen.onClose(); return; }
        List<Object> options = new ArrayList<>();
        for (int index = 0; index < node.options().size(); index++) {
            XenoDialogue.Option option = node.options().get(index);
            int sourceIndex = option.sourceIndex() >= 0 ? option.sourceIndex() : index;
            boolean enabled = option.type() != XenoDialogue.OptionType.ROLE
                    && ((option.type() != XenoDialogue.OptionType.QUEST
                    && option.type() != XenoDialogue.OptionType.DMZ_QUEST) || !option.quest().isBlank());
            options.add(Dmz22DialogueAccess.option(Integer.toString(index), text(option.text()),
                    () -> choose(nodeId, option, sourceIndex), enabled));
        }
        Dmz22DialogueAccess.page(screen, nodeId, () -> text(node.text()), () -> List.copyOf(options));
    }
    private void choose(String nodeId, XenoDialogue.Option option, int index) {
        switch (option.type()) {
            case TEXT -> {
                ModNetwork.sendToServer(new XenoNpcDialoguePacket(entityId, nodeId, index));
                show(option.target());
            }
            case QUEST, DMZ_QUEST, COMMAND -> {
                ModNetwork.sendToServer(new XenoNpcDialoguePacket(entityId, nodeId, index));
                screen.onClose();
            }
            case TRANSPORT -> {
                ModNetwork.sendToServer(new XenoNpcTravelPacket(entityId, option.target()));
                screen.onClose();
            }
            case ROLE -> { }
            default -> screen.onClose();
        }
    }
}
