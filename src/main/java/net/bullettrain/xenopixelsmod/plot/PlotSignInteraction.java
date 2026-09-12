package net.bullettrain.xenopixelsmod.plot;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.bullettrain.xenopixelsmod.shop.SignListingProtection;
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

import javax.annotation.Nullable;

/**
 * Purchase and edit gates for plot-sale signs.
 *
 * <p>A recognized plot-sale sign swallows a plain right-click and buys when the player has
 * {@link XenoPermissions#PLOT_USE}. Sneak-right-click opens vanilla's editor only when the
 * player has {@link XenoPermissions#PLOT_EDIT}.</p>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlotSignInteraction {

    private PlotSignInteraction() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() == null || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)
                || !SignListingProtection.isPlot(sign)) {
            return;
        }

        if (event.getEntity().isShiftKeyDown()) {
            if (level.isClientSide) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            if (event.getEntity() instanceof ServerPlayer player
                    && !XenoPermissions.hasPermission(player, XenoPermissions.PLOT_EDIT)) {
                player.displayClientMessage(Component.literal("You cannot edit this plot sign."), true);
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
        PlotSignData data = PlotSignReader.fromSignText(sign.getText(true), false);
        if (data == null) {
            data = PlotSignReader.fromSignText(sign.getText(false), false);
        }
        if (data == null) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        PlotArea reference = new PlotArea(level.dimension().location(), data.minX(), data.minZ(),
                data.maxX(), data.maxZ(), null, PlotFlags.DEFAULT);
        PlotSale.Result result = PlotSale.buy(player, reference);
        Component message = describe(result);
        if (message != null) {
            player.displayClientMessage(message, true);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** Human-readable failure text, or {@code null} for {@link PlotSale.Result#SUCCESS}. */
    @Nullable
    public static Component describe(PlotSale.Result result) {
        return switch (result) {
            case SUCCESS -> null;
            case NO_ECONOMY -> Component.literal("Plot sales need MMO Econ, which is not installed.");
            case INSUFFICIENT_FUNDS -> Component.literal("You cannot afford this plot.");
            case TRANSFER_FAILED -> Component.literal("The payment did not go through.");
            case NOT_FOR_SALE -> Component.literal("That plot is not for sale.");
            case NO_PLOT -> Component.literal("No claimed plot at those coordinates.");
            case ALREADY_OWNER -> Component.literal("You already own that plot.");
            case NO_PERMISSION -> Component.literal("You cannot use plot signs.");
        };
    }
}
