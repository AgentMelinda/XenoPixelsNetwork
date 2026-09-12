package net.bullettrain.xenopixelsmod.shop;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Purchase trigger for sign shops.
 *
 * <p>A plain right-click on a sign opens vanilla's editor, so the buy action is bound to
 * <b>sneak-right-click</b> instead. That leaves editing untouched and gives the purchase a
 * deliberate input that cannot fire by accident.</p>
 *
 * <p>The "is this a shop" test reads the sign's own text through {@link SignShopReader}, which works
 * on both sides. The client therefore never opens the editor over a shop sign, and the server never
 * has to trust a client-side claim that a sign is a shop — the purchase path re-resolves the listing
 * from the server's own registry.</p>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SignShopInteraction {

    private SignShopInteraction() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() == null || !event.getEntity().isShiftKeyDown()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return;
        }
        boolean isShop = SignShopReader.fromSignText(sign.getText(true), false) != null
                || SignShopReader.fromSignText(sign.getText(false), false) != null;
        if (!isShop) {
            return;
        }

        if (level.isClientSide) {
            // Suppress the editor; the server is the only side that may move money.
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