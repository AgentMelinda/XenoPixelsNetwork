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

record AccountPacket(boolean linked, List<ShopOrder> orders) {
    private static final int MAX_ORDERS = 20;

    static void register() {
        AddonNetwork.register(ResourceLocation.fromNamespaceAndPath(XenoShopAddon.MOD_ID, "account"),
                AccountPacket.class, AddonPacketDirection.CLIENTBOUND,
                AccountPacket::encode, AccountPacket::decode, AccountPacket::handle);
    }

    private static void encode(AccountPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.linked());
        int count = Math.min(MAX_ORDERS, packet.orders().size());
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            ShopOrder order = packet.orders().get(i);
            buffer.writeUtf(order.title(), 120);
            buffer.writeUtf(order.method(), 16);
            buffer.writeVarLong(order.amount());
            buffer.writeUtf(order.currency(), 3);
            buffer.writeUtf(order.status(), 24);
            buffer.writeUtf(order.createdAt(), 32);
        }
    }

    private static AccountPacket decode(FriendlyByteBuf buffer) {
        boolean linked = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ORDERS) throw new IllegalArgumentException("Invalid shop order count");
        List<ShopOrder> orders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) orders.add(new ShopOrder(buffer.readUtf(120), buffer.readUtf(16),
                buffer.readVarLong(), buffer.readUtf(3), buffer.readUtf(24), buffer.readUtf(32)));
        return new AccountPacket(linked, List.copyOf(orders));
    }

    private static void handle(AccountPacket packet, AddonPacketContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ShopClient.openAccount(packet);
        });
    }
}
