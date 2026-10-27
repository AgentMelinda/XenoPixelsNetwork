package net.bullettrain.xenopixelsmod.api.commerce;

import net.minecraft.server.level.ServerPlayer;

/** Server-side balance adapter. Implementations must reject non-positive amounts and be atomic. */
public interface CommerceCurrencyProvider {
    String id();
    String displayName();
    boolean available();
    long balance(ServerPlayer player);
    boolean debit(ServerPlayer player, long amount);
    boolean credit(ServerPlayer player, long amount);
}
