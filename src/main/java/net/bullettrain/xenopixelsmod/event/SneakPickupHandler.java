package net.bullettrain.xenopixelsmod.event;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * Items are only picked up while sneaking.
 *
 * <p>A DMZ fight against a mob group drops far more than vanilla combat does, and walking over
 * the pile mid-fight fills the hotbar with junk the player did not ask for — including, at the
 * worst moment, swapping what is in hand. Requiring a crouch makes collecting a deliberate act.
 *
 * <p><b>Nothing is destroyed.</b> This only declines the pickup, so the stack stays on the
 * ground with its normal despawn timer running. That distinction matters: a handler that
 * deleted the drop instead would quietly eat loot from anyone who did not know the rule.
 *
 * <p>Experience orbs are unaffected, and not because of a check here: they are a different
 * entity on a different pickup path, so this event never sees them. XP keeps vacuuming up the
 * way it always has, which is the desired outcome anyway — nobody complains about collecting XP.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SneakPickupHandler {

    private SneakPickupHandler() {
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!XenoServerConfig.sneakToPickup) return;

        Player player = event.getPlayer();
        if (player == null || player.isSpectator()) return;
        // Creative-mode players are usually building, not fighting; the drop pile is not their
        // problem and silently refusing their pickups is more confusing than helpful.
        if (player.isCreative()) return;
        if (player.isShiftKeyDown()) return;

        // Declines this pickup only. The item entity is untouched, so it stays on the ground
        // and despawns on its own schedule exactly as it would have.
        event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
}
