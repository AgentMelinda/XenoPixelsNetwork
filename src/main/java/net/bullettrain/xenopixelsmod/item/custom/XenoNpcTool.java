package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * An item that does something when it is used on a Xeno NPC.
 *
 * <p><b>Why this exists.</b> {@code XenoNpcEntity.mobInteract} used to test for each tool by hand,
 * and every one needed the same two lines twice over: check the main hand, then check the off hand,
 * because vanilla runs {@code mobInteract} for the main hand first and stops as soon as it consumes
 * the interaction. Getting that wrong is invisible — the tool simply never fires, and the NPC's
 * ordinary interaction happens instead, which reads as the item being broken rather than unrouted.
 * It is exactly how the Cloner shipped unusable: registered, textured, compiling, and never called.
 *
 * <p>So the check lives in one place. A tool implements this, and
 * {@link #resolve(Player, XenoNpcEntity)} finds whichever hand holds one.
 *
 * <p>Tools are consulted <em>before</em> the NPC's own interaction — a trader with stock opens its
 * shop, and a tool in hand has to beat that or an operator could never edit a trader.
 */
public interface XenoNpcTool {

    /**
     * Does this tool's thing to the NPC.
     *
     * <p>Called on both sides, as {@code mobInteract} is. An implementation returns
     * {@link InteractionResult#SUCCESS} on the client without acting — the server's answer is the
     * one that counts, and acting on both would do it twice.
     *
     * @return what the interaction did; {@link InteractionResult#PASS} to let the NPC handle it
     */
    InteractionResult useOnNpc(ItemStack stack, Player player, XenoNpcEntity npc);

    /**
     * The stack in either hand holding a tool, or {@link ItemStack#EMPTY}.
     *
     * <p>The main hand wins when both hold one, which matches how a player thinks about which item
     * they are using.
     */
    static ItemStack held(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        if (player.getMainHandItem().getItem() instanceof XenoNpcTool) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof XenoNpcTool) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    /**
     * Runs whichever tool the player is holding, or {@link InteractionResult#PASS}.
     *
     * <p>The single call {@code mobInteract} makes. Adding a tool means implementing this interface
     * and nothing else — there is no list here to forget to add to, which is the failure this
     * replaces.
     */
    static InteractionResult resolve(Player player, XenoNpcEntity npc) {
        ItemStack stack = held(player);
        if (stack.isEmpty() || npc == null) {
            return InteractionResult.PASS;
        }
        return ((XenoNpcTool) stack.getItem()).useOnNpc(stack, player, npc);
    }
}
