package net.bullettrain.xenopixels.shop;

import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.Mod;

@Mod(XenoShopAddon.MOD_ID)
public final class XenoShopAddon {
    public static final String MOD_ID = "xenopixels_shop";

    public XenoShopAddon() {
        CatalogPacket.register();
        PurchasePacket.register();
        AccountPacket.register();
        AccountRequestPacket.register();
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("xshop")
                .executes(context -> open(context.getSource().getPlayerOrException()))
                .then(Commands.literal("link")
                        .then(Commands.argument("code", StringArgumentType.word())
                                .executes(context -> link(context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "code")))))
                .then(Commands.literal("claim")
                        .executes(context -> claim(context.getSource().getPlayerOrException())))
                .then(Commands.literal("account")
                        .executes(context -> account(context.getSource().getPlayerOrException()))));
    }

    private static int open(ServerPlayer player) {
        ShopBridge.fetchCatalog(player);
        return 1;
    }

    private static int link(ServerPlayer player, String code) {
        ShopBridge.redeemLink(player, code);
        return 1;
    }

    private static int claim(ServerPlayer player) {
        ShopBridge.claimDelivery(player);
        return 1;
    }

    private static int account(ServerPlayer player) {
        ShopBridge.fetchAccount(player);
        return 1;
    }
}
