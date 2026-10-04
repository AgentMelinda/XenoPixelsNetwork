package net.bullettrain.xenopixelsmod.client.npc.dialog;

import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueText;

import java.util.List;

/**
 * The conversation one player is having with one NPC, client side.
 *
 * <p>State only - no rendering and no input. {@code DialogueBubbleRenderer} draws whatever this
 * holds and {@code DialogueBubbleInput} picks from it, which keeps the layout maths out of the
 * click handling and lets both be reasoned about separately.
 *
 * <p>There is at most one conversation at a time. Talking to a second NPC replaces the first rather
 * than stacking, because two NPCs answering at once has no sensible reading.
 */
public final class DialogueBubbleSession {

    private static DialogueBubbleSession active;

    private final int entityId;
    private final XenoDialogue dialogue;
    private final String npcName;
    private String nodeId;

    private DialogueBubbleSession(int entityId, XenoDialogue dialogue, String npcName) {
        this.entityId = entityId;
        this.dialogue = dialogue;
        this.npcName = npcName == null ? "" : npcName;
        this.nodeId = dialogue.start();
    }

    /**
     * Starts a conversation, replacing any in progress.
     *
     * @return the session, or null when the dialogue names no node that exists - a pack can ship a
     *         {@code start} that was renamed, and an empty bubble is worse than no bubble
     */
    public static DialogueBubbleSession begin(int entityId, XenoDialogue dialogue, String npcName) {
        if (dialogue == null || dialogue.startNode() == null) {
            active = null;
            return null;
        }
        active = new DialogueBubbleSession(entityId, dialogue, npcName);
        return active;
    }

    /** The conversation in progress, or null. */
    public static DialogueBubbleSession active() {
        return active;
    }

    public static void end() {
        active = null;
    }

    /** Ends the conversation if it belongs to {@code entityId}. Used when that NPC goes away. */
    public static void endFor(int entityId) {
        if (active != null && active.entityId == entityId) {
            active = null;
        }
    }

    public int entityId() {
        return entityId;
    }

    public String npcName() {
        return npcName;
    }

    public String nodeId() {
        return nodeId;
    }

    /** The node being shown, or null when the conversation has walked off the end. */
    public XenoDialogue.Node node() {
        return dialogue.nodes().get(nodeId);
    }

    /** What the NPC is saying right now, or "" when there is no node. */
    public String text() {
        XenoDialogue.Node node = node();
        return node == null ? "" : resolve(node.text());
    }

    public String resolve(String text) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return XenoDialogueText.resolve(text,
                player == null ? "" : player.getName().getString(), npcName);
    }

    /** The options on the current node, or empty. */
    public List<XenoDialogue.Option> options() {
        XenoDialogue.Node node = node();
        return node == null ? List.of() : node.options();
    }

    /**
     * Moves to {@code target}.
     *
     * <p>A target that does not exist ends the conversation rather than leaving the player looking
     * at the same node wondering whether the click registered - the same rule the old dialogue
     * screen applied.
     */
    public void goTo(String target) {
        if (target != null && dialogue.nodes().containsKey(target)) {
            nodeId = target;
        } else {
            end();
        }
    }
}
