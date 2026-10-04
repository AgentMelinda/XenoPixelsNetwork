package net.bullettrain.xenopixelsmod.client.npc.dialog;

import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.XenoNpcDialoguePacket;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;

import java.util.List;

/**
 * What picking a dialogue option does.
 *
 * <p>Its own class because two things now pick options - the crosshair, and the older screen kept
 * behind a config flag. Two copies of this logic would drift, and the half most likely to drift is
 * the part that decides what reaches the server.
 *
 * <p>Navigation between text nodes is resolved here, on the client, because it is only a change of
 * which node is showing. Anything with a consequence - a quest, a command - goes to the server as
 * an <em>index</em>, and the server re-reads its own copy of the dialogue before acting. That split
 * is why a crafted packet cannot start an arbitrary quest, and it is unchanged by moving the input.
 */
public final class DialogueBubbleChoice {

    private DialogueBubbleChoice() {
    }

    /** What the caller should do with its own UI afterwards. */
    public enum Result {
        /** The conversation moved on and is still open. */
        CONTINUE,
        /** The conversation is over. */
        CLOSED,
        /** Nothing happened; the option does nothing yet. */
        IGNORED
    }

    /**
     * Acts on one option of the active conversation.
     *
     * <p>Ends the session itself on anything terminal, so a caller that forgets to close is left
     * with no session rather than a stale one.
     */
    /** Tells the server which option was picked so a dialog objective can advance. */
    private static void reportSelection(DialogueBubbleSession session, int index) {
        ModNetwork.sendToServer(new XenoNpcDialoguePacket(
                session.entityId(), session.nodeId(), serverOptionIndex(session, index)));
    }

    static int serverOptionIndex(DialogueBubbleSession session, int visibleIndex) {
        if (session == null || visibleIndex < 0 || visibleIndex >= session.options().size()) {
            return -1;
        }
        int sourceIndex = session.options().get(visibleIndex).sourceIndex();
        return sourceIndex < 0 ? visibleIndex : sourceIndex;
    }

    public static Result choose(DialogueBubbleSession session, int index) {
        if (session == null) {
            return Result.CLOSED;
        }
        List<XenoDialogue.Option> options = session.options();
        if (index < 0 || index >= options.size()) {
            return Result.IGNORED;
        }
        XenoDialogue.Option option = options.get(index);
        switch (option.type()) {
            case TEXT -> {
                reportSelection(session, index);
                session.goTo(option.target());
                // goTo clears the session when the target does not resolve, which is how a
                // dialogue that points nowhere ends rather than freezing on its last line.
                return DialogueBubbleSession.active() == null ? Result.CLOSED : Result.CONTINUE;
            }
            case QUEST, COMMAND -> {
                int optionIndex = serverOptionIndex(session, index);
                if (optionIndex < 0) {
                    return Result.IGNORED;
                }
                ModNetwork.sendToServer(new XenoNpcDialoguePacket(
                        session.entityId(), session.nodeId(), optionIndex));
                DialogueBubbleSession.end();
                return Result.CLOSED;
            }
            case TRANSPORT -> {
                // The destination id, never a position. Everything about where that is lives on
                // the server, which re-checks that this NPC actually offers it.
                ModNetwork.sendToServer(new net.bullettrain.xenopixelsmod.network.packet
                        .XenoNpcTravelPacket(session.entityId(), option.target()));
                DialogueBubbleSession.end();
                return Result.CLOSED;
            }
            case ROLE -> {
                // No economy roles exist yet. Nothing happens rather than something misleading,
                // and the conversation stays open so the player can pick something else.
                return Result.IGNORED;
            }
            default -> {
                DialogueBubbleSession.end();
                return Result.CLOSED;
            }
        }
    }
}
