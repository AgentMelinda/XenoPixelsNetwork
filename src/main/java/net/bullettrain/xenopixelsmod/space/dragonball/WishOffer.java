package net.bullettrain.xenopixelsmod.space.dragonball;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Clientbound wish catalog shown after a summon (channel {@code xenopixelsmod:wish_offer_v1}). */
public record WishOffer(List<Entry> entries) {
    public record Entry(String id, String name, String description) {}

    public WishOffer {
        if (entries == null || entries.size() > WishCatalog.MAX_WISHES) throw new IllegalArgumentException("invalid wish offer");
        entries = List.copyOf(entries);
    }

    public static void encode(WishOffer packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entries.size());
        for (Entry e : packet.entries) { buf.writeUtf(e.id, 256); buf.writeUtf(e.name, 64); buf.writeUtf(e.description, 256); }
    }

    public static WishOffer decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > WishCatalog.MAX_WISHES) throw new IllegalArgumentException("wish offer too large");
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) entries.add(new Entry(buf.readUtf(256), buf.readUtf(64), buf.readUtf(256)));
        return new WishOffer(entries);
    }
}
