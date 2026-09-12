package net.bullettrain.xenopixelsmod.shop;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.bullettrain.xenopixelsmod.plot.PlotSignReader;
import net.bullettrain.xenopixelsmod.plot.PlotSignSyntax;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Keeps a finished shop or plot-sale sign from being broken by anyone without the edit node.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SignListingProtection {

    private SignListingProtection() {
    }

    public static boolean isShop(SignBlockEntity sign) {
        return sign != null && (SignShopReader.hasMarker(sign.getText(true))
                || SignShopReader.hasMarker(sign.getText(false)));
    }

    public static boolean isPlot(SignBlockEntity sign) {
        return sign != null && (PlotSignReader.hasMarker(sign.getText(true))
                || PlotSignReader.hasMarker(sign.getText(false)));
    }

    /**
     * @return {@code true} when this edit must be dropped: the face is already a listing, or the
     *         incoming text would become one, and the player lacks the matching edit node.
     */
    public static boolean denyEdit(ServerPlayer player, SignBlockEntity sign, String[] incoming,
                                   boolean front) {
        if (player == null || sign == null) {
            return true;
        }
        boolean existingShop = SignShopReader.hasMarker(sign.getText(front));
        boolean existingPlot = PlotSignReader.hasMarker(sign.getText(front));
        boolean incomingShop = SignShopSyntax.isShopSign(incoming);
        boolean incomingPlot = PlotSignSyntax.isPlotSign(incoming);
        if ((existingShop || incomingShop)
                && !XenoPermissions.hasPermission(player, XenoPermissions.SHOP_EDIT)) {
            player.displayClientMessage(Component.literal("You cannot edit this shop sign."), true);
            return true;
        }
        if ((existingPlot || incomingPlot)
                && !XenoPermissions.hasPermission(player, XenoPermissions.PLOT_EDIT)) {
            player.displayClientMessage(Component.literal("You cannot edit this plot sign."), true);
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof SignBlockEntity sign)) {
            return;
        }
        boolean shop = isShop(sign);
        boolean plot = isPlot(sign);
        if (!shop && !plot) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            event.setCanceled(true);
            return;
        }
        if (shop && !XenoPermissions.hasPermission(player, XenoPermissions.SHOP_EDIT)) {
            player.displayClientMessage(Component.literal("You cannot break this shop sign."), true);
            event.setCanceled(true);
            return;
        }
        if (plot && !XenoPermissions.hasPermission(player, XenoPermissions.PLOT_EDIT)) {
            player.displayClientMessage(Component.literal("You cannot break this plot sign."), true);
            event.setCanceled(true);
        }
    }
}
