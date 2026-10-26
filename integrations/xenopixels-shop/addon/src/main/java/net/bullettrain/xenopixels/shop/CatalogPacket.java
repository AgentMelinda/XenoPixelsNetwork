package net.bullettrain.xenopixels.shop;

import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.api.network.AddonPacketContext;
import net.bullettrain.xenopixelsmod.api.network.AddonPacketDirection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.List;

record CatalogPacket(List<ShopProduct> products, String shopUrl) {
    private static final int MAX_PRODUCTS = 100;

    static void register() {
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath(XenoShopAddon.MOD_ID, "catalog"),
                CatalogPacket.class, AddonPacketDirection.CLIENTBOUND,
                CatalogPacket::encode, CatalogPacket::decode, CatalogPacket::handle);
    }

    private static void encode(CatalogPacket packet, FriendlyByteBuf buffer) {
        int count = Math.min(MAX_PRODUCTS, packet.products().size());
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ShopProduct product = packet.products().get(i);
            buffer.writeUtf(product.slug(), 80);
            buffer.writeUtf(product.title(), 120);
            buffer.writeUtf(product.description(), 512);
            buffer.writeVarLong(product.webPriceMinor());
            buffer.writeUtf(product.webCurrency(), 3);
            buffer.writeVarLong(product.mmoEconPrice());
            buffer.writeVarLong(product.zeniCashPrice());
        }
        buffer.writeUtf(packet.shopUrl(), 256);
    }

    private static CatalogPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PRODUCTS) throw new IllegalArgumentException("Invalid shop catalog size");
        List<ShopProduct> products = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            products.add(new ShopProduct(buffer.readUtf(80), buffer.readUtf(120), buffer.readUtf(512),
                    buffer.readVarLong(), buffer.readUtf(3), buffer.readVarLong(), buffer.readVarLong()));
        }
        return new CatalogPacket(List.copyOf(products), buffer.readUtf(256));
    }

    private static void handle(CatalogPacket packet, AddonPacketContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ShopClient.open(packet.products(), packet.shopUrl());
        });
    }
}
