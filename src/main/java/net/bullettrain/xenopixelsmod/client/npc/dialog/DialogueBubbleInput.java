package net.bullettrain.xenopixelsmod.client.npc.dialog;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Answering a conversation without losing the mouse.
 *
 * <p>The bubbles used to be drawn in the world while an invisible {@code Screen} sat on top of
 * them purely to provide a cursor and catch clicks. That worked, but it took the mouse: the player
 * stopped being able to look around mid-conversation, which is the opposite of what moving dialogue
 * out of a menu was for. Here there is no screen at all. The crosshair does the aiming, and a click
 * picks whatever bubble it is on.
 *
 * <p>Either button answers. The player asked for that and it is the right default: there is no
 * meaningful difference between "attack this bubble" and "use this bubble", so making one of them
 * wrong would only be a rule to remember.
 *
 * <p>Both buttons are <b>consumed</b> for as long as a conversation is open, even when the
 * crosshair is on nothing. Letting them through would mean punching the NPC in the middle of its
 * own sentence. This is the one place the usual rule - that a Xeno input should sit alongside the
 * vanilla one rather than replace it - is deliberately not followed, because a conversation is a
 * modal intent even without a modal screen. Sneak leaves, so the player is never stuck.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class DialogueBubbleInput {

    /** Was the sneak key down last tick, so leaving triggers on the press rather than the hold. */
    private static boolean sneakWasDown;

    private DialogueBubbleInput() {
    }

    /**
     * Picks the bubble under the crosshair on either mouse button.
     *
     * <p>{@link InputEvent.InteractionKeyMappingTriggered} rather than a raw mouse hook: it is the
     * event that already knows whether a press was attack or use, it is cancellable, and it is what
     * {@code Bt3CombatClient} uses for the same purpose - one way of suppressing a click in this
     * codebase rather than two.
     */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        DialogueBubbleSession session = DialogueBubbleSession.active();
        if (session == null || !usesCrosshair()) {
            return;
        }
        if (!event.isAttack() && !event.isUseItem()) {
            // Middle-click pick-block and anything else is left alone.
            return;
        }

        // Consumed whether or not it hits, so a stray click cannot damage the NPC being spoken to.
        event.setCanceled(true);
        event.setSwingHand(false);

        DialogueBubbleRenderer.OptionHit hit = DialogueBubbleRenderer.pickAtCrosshair();
        if (hit != null) {
            DialogueBubbleChoice.choose(session, hit.index());
        }
    }

    /**
     * Leaves the conversation on sneak, and picks an option on the number keys.
     *
     * <p>Polled on the client tick because there is no screen to receive key events any more. The
     * number keys are kept from the screen version: a bubble can end up behind the player or off
     * the edge of the display, and a conversation that cannot be answered at all is worse than one
     * answered with a key.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        DialogueBubbleSession session = DialogueBubbleSession.active();
        if (session == null || !usesCrosshair()) {
            sneakWasDown = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            // Some other screen took over - an inventory, the pause menu. Leave its input alone.
            return;
        }

        boolean sneakDown = mc.options.keyShift.isDown();
        if (sneakDown && !sneakWasDown) {
            DialogueBubbleSession.end();
            sneakWasDown = true;
            return;
        }
        sneakWasDown = sneakDown;

        for (int i = 0; i < Math.min(9, session.options().size()); i++) {
            if (mc.options.keyHotbarSlots[i].consumeClick()) {
                DialogueBubbleChoice.choose(session, i);
                return;
            }
        }
    }

    /**
     * Whether conversations are answered at the crosshair rather than through the older screen.
     *
     * <p>Read every time rather than cached, so flipping the config takes effect on the next
     * conversation instead of needing a restart.
     */
    private static boolean usesCrosshair() {
        return net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.dialogueBubbles
                && net.bullettrain.xenopixelsmod.client.config.XenoClientConfig.dialogueCrosshair;
    }
}
