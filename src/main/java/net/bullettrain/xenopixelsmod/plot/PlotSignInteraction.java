package net.bullettrain.xenopixelsmod.plot;

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

import javax.annotation.Nullable;

/**
 * Purchase trigger for plot-sale signs.
 *
 * <p>Bound to <b>sneak-right-click</b> for the same reason a shop sign is: a plain right-click
 * opens vanilla's editor, so the buy action needs an input that cannot fire by accident. Editing
 * a plot sign is therefore untouched.</p>
 *
 * <p>Purchase delegates to {@link PlotSale#buy}, so money-first settlement, the {@code NO_ECONOMY}
 * failure posture and the ownership hand-off are the already-verified plot sale path rather than a
 * second implementation. The sign contributes only the plot tuple; the seller and the price come
 * from the server's own listing, so a sign cannot be edited to change what is actually charged.</p>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PlotSignInteraction {

    private PlotSignInteraction() {
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
        boolean isPlotSign = PlotSignReader.fromSignText(sign.getText(true), false) != null
                || PlotSignReader.fromSignText(sign.getText(false), false) != null;
        if (!isPlotSign) {
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
        };
    }
}