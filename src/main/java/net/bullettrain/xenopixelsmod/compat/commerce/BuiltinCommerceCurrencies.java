package net.bullettrain.xenopixelsmod.compat.commerce;

import net.bullettrain.xenopixelsmod.api.commerce.CommerceApi;
import net.bullettrain.xenopixelsmod.api.commerce.CommerceCurrencyProvider;
import net.bullettrain.xenopixelsmod.compat.mmoecon.MmoEconBridge;
import net.bullettrain.xenopixelsmod.npc.bank.ZeniCash;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Bridges only currencies whose server-side operations are owned and verified by XenoPixels. */
public final class BuiltinCommerceCurrencies {
    private static boolean registered;

    private BuiltinCommerceCurrencies() {}

    public static synchronized void register() {
        if (registered) return;
        CommerceApi.register(new CommerceCurrencyProvider() {
            public String id() { return "mmoecon"; }
            public String displayName() { return "MMO Econ"; }
            public boolean available() { return MmoEconBridge.available(); }
            public long balance(ServerPlayer player) { return MmoEconBridge.getBalance(player.getUUID()); }
            public boolean debit(ServerPlayer player, long amount) { return MmoEconBridge.withdraw(player.getUUID(), amount); }
            public boolean credit(ServerPlayer player, long amount) { return MmoEconBridge.deposit(player.getUUID(), amount); }
        });
        CommerceApi.register(new CommerceCurrencyProvider() {
            public String id() { return "zeni_cash"; }
            public String displayName() { return "Zeni Cash"; }
            public boolean available() { return ModList.get().isLoaded("xenopixelsmod"); }
            public long balance(ServerPlayer player) { return ZeniCash.carriedValue(player); }
            public boolean debit(ServerPlayer player, long amount) { return ZeniCash.spend(player, amount); }
            public boolean credit(ServerPlayer player, long amount) { return ZeniCash.give(player, amount); }
        });
        registered = true;
    }
}
