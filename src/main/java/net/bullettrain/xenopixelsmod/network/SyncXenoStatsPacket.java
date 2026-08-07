package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncXenoStatsPacket {
    private final float health;
    private final float maxHealth;
    private final float ki;
    private final float maxKi;
    private final float stamina;
    private final float maxStamina;

    public SyncXenoStatsPacket(float health, float maxHealth, float ki, float maxKi, float stamina, float maxStamina) {
        this.health = health;
        this.maxHealth = maxHealth;
        this.ki = ki;
        this.maxKi = maxKi;
        this.stamina = stamina;
        this.maxStamina = maxStamina;
    }

    public static void encode(SyncXenoStatsPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.health);
        buf.writeFloat(msg.maxHealth);
        buf.writeFloat(msg.ki);
        buf.writeFloat(msg.maxKi);
        buf.writeFloat(msg.stamina);
        buf.writeFloat(msg.maxStamina);
    }

    public static SyncXenoStatsPacket decode(FriendlyByteBuf buf) {
        return new SyncXenoStatsPacket(
                buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat()
        );
    }

    public static void handle(SyncXenoStatsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketHandlers.handleXenoStats(
                msg.health, msg.maxHealth, msg.ki, msg.maxKi, msg.stamina, msg.maxStamina));
        ctx.get().setPacketHandled(true);
    }
}
