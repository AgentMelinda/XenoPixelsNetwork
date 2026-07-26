package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL = "10";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(XenoPixelsMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int id = 0;

    public static void register() {
        CHANNEL.registerMessage(id++, SyncXenoStatsPacket.class,
                SyncXenoStatsPacket::encode,
                SyncXenoStatsPacket::decode,
                SyncXenoStatsPacket::handle);
        CHANNEL.registerMessage(id++, SyncDmzHudStatePacket.class,
                SyncDmzHudStatePacket::encode,
                SyncDmzHudStatePacket::decode,
                SyncDmzHudStatePacket::handle);
        CHANNEL.registerMessage(id++, Bt3CombatPacket.class,
                Bt3CombatPacket::encode,
                Bt3CombatPacket::decode,
                Bt3CombatPacket::handle);
        CHANNEL.registerMessage(id++, SyncServerConfigPacket.class,
                SyncServerConfigPacket::encode,
                SyncServerConfigPacket::decode,
                SyncServerConfigPacket::handle);
        CHANNEL.registerMessage(id++, ChargeAnimPacket.class,
                ChargeAnimPacket::encode,
                ChargeAnimPacket::decode,
                ChargeAnimPacket::handle);
    }
}