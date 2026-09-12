package net.bullettrain.xenopixelsmod.shop;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Purchase and edit gates for sign shops.
 *
 * <p>A recognized shop swallows a plain right-click and buys when the player has
 * {@link XenoPermissions#SHOP_USE}. Sneak-right-click opens vanilla's editor only when the
 * player has {@link XenoPermissions#SHOP_EDIT}; everyone else is refused so a finished listing
 * cannot be rewritten.</p>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SignShopInteraction {

    private SignShopInteraction() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() == null || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)
                || !SignListingProtection.isShop(sign)) {
            return;
        }

        if (event.getEntity().isShiftKeyDown()) {
            if (level.isClientSide) {
                // Do not open the editor locally. The server sends it only when shop.edit is held.
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            if (event.getEntity() instanceof ServerPlayer player
                    && !XenoPermissions.hasPermission(player, XenoPermissions.SHOP_EDIT)) {
                player.displayClientMessage(Component.literal("You cannot edit this shop sign."), true);
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
            return;
        }

        if (level.isClientSide) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SignShopPurchase.Result result = SignShopPurchase.buy(player, pos);
        Component message = SignShopPurchase.describe(result);
        if (message != null) {
            player.displayClientMessage(message, true);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
