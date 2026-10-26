package net.bullettrain.xenopixels.shop;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.api.network.AddonPacketContext;
import net.bullettrain.xenopixelsmod.api.network.AddonPacketDirection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

record AccountRequestPacket() {
    static void register() {
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath(XenoShopAddon.MOD_ID, "account_request"),
                AccountRequestPacket.class, AddonPacketDirection.SERVERBOUND,
                (packet, buffer) -> {}, buffer -> new AccountRequestPacket(), AccountRequestPacket::handle);
    }

    private static void handle(AccountRequestPacket packet, AddonPacketContext context) {
        context.enqueueWork(() -> context.sender().ifPresent(ShopBridge::fetchAccount));
    }
}
