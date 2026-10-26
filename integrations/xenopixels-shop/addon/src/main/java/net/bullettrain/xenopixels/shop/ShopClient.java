package net.bullettrain.xenopixels.shop;

import net.minecraft.client.Minecraft;

import java.util.List;

final class ShopClient {
    private ShopClient() {}

    static void open(List<ShopProduct> products, String shopUrl) {
        Minecraft.getInstance().setScreen(new ShopScreen(products, shopUrl));
    }

    static void openAccount(AccountPacket account) {
        Minecraft.getInstance().setScreen(new AccountScreen(account));
    }
}
