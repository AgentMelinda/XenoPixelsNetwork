package net.bullettrain.xenopixelsmod.client.npc.dialog;

import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * An invisible screen that exists only to catch clicks on the dialogue bubbles.
 *
 * <p>The bubbles themselves are drawn in the world by {@link DialogueBubbleRenderer}; this draws
 * nothing at all. It is here because a conversation needs a cursor and a way to click, and an open
 * screen is how Minecraft gives you both - the world keeps rendering behind it, so the bubbles stay
 * where they are and keep facing the camera.
 *
 * <p>No dimming and no background: the point of moving dialogue out of a menu was to keep the world
 * visible while the NPC talks.
 */
public final class DialogueBubbleScreen extends Screen {

    public DialogueBubbleScreen() {
        super(Component.literal("Dialogue"));
    }

    /**
     * Opens the conversation, or does nothing when there is none to open.
     *
     * <p>Which input route runs is decided here. With {@code dialogueCrosshair} on, the session is
     * started and <em>no screen is opened at all</em> - {@link DialogueBubbleInput} answers at the
     * crosshair and the player keeps the mouse. With it off, this screen opens as before.
     */
    public static void open(int entityId, XenoDialogue dialogue, String npcName) {
        if (DialogueBubbleSession.begin(entityId, dialogue, npcName) == null) {
            return;
        }
        if (net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.dialogueCrosshair) {
            return;
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(new DialogueBubbleScreen());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately not super.render: that would paint the dimming layer over the world.
        if (DialogueBubbleSession.active() == null) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        // A conversation should not stop single-player time; the NPC is still standing there.
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        DialogueBubbleSession session = DialogueBubbleSession.active();
        if (session == null) {
            onClose();
            return true;
        }
        DialogueBubbleRenderer.OptionHit hit = DialogueBubbleRenderer.pick(mouseX, mouseY);
        if (hit != null) {
            choose(session, hit.index());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        DialogueBubbleSession session = DialogueBubbleSession.active();
        // Number keys pick options too. A bubble can end up behind the player or off the edge of
        // the screen, and a conversation that cannot be answered is worse than an ugly one.
        if (session != null && key >= 49 && key <= 57) {
            int index = key - 49;
            if (index < session.options().size()) {
                choose(session, index);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /**
     * Acts on one option, through the logic both input routes share.
     *
     * <p>{@link DialogueBubbleChoice} owns what an option does - navigation on the client, anything
     * with a consequence sent to the server as an index it re-reads. This screen only has to close
     * itself when that says the conversation is over; two copies of the deciding half would be two
     * things to keep in step.
     */
    private void choose(DialogueBubbleSession session, int index) {
        if (DialogueBubbleChoice.choose(session, index) == DialogueBubbleChoice.Result.CLOSED) {
            // The session is already ended; closing the screen must not end it a second time.
            super.onClose();
        }
    }

    @Override
    public void onClose() {
        DialogueBubbleSession.end();
        super.onClose();
    }
}
