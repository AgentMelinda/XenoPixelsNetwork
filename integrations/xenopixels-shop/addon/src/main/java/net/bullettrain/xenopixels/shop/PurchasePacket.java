package net.bullettrain.xenopixels.shop;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.api.network.AddonPacketContext;
import net.bullettrain.xenopixelsmod.api.network.AddonPacketDirection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

record PurchasePacket(String slug, String currency) {
    static void register() {
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath(XenoShopAddon.MOD_ID, "purchase"),
                PurchasePacket.class, AddonPacketDirection.SERVERBOUND,
                PurchasePacket::encode, PurchasePacket::decode, PurchasePacket::handle);
    }

    private static void encode(PurchasePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.slug(), 80);
        buffer.writeUtf(packet.currency(), 48);
    }

    private static PurchasePacket decode(FriendlyByteBuf buffer) {
        String slug = buffer.readUtf(80);
        String currency = buffer.readUtf(48);
        if (!slug.matches("[a-z0-9_-]{1,80}") || !currency.matches("mmoecon|zeni_cash")) {
            throw new IllegalArgumentException("Invalid shop purchase request");
        }
        return new PurchasePacket(slug, currency);
    }

    private static void handle(PurchasePacket packet, AddonPacketContext context) {
        context.enqueueWork(() -> context.sender().ifPresent(player ->
                ShopBridge.purchase(player, packet.slug(), packet.currency())));
    }
}
