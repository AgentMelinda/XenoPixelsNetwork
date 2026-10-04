package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync;
import net.bullettrain.xenopixelsmod.compat.npc.NpcProfileWatchers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

/**
 * C2S: a DMZ screen asks for the NPC's full server profile (answered with
 * {@link NpcProfileRefreshPacket}) and starts watching it. Same checks as a profile save: operator,
 * editable NPC, within 64 blocks.
 */
public final class NpcProfileRequestPacket {
    private final int entityId;

    public NpcProfileRequestPacket(int entityId) {
        this.entityId = entityId;
    }

    public NpcProfileRequestPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static void handle(NpcProfileRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2) || !(player.level() instanceof ServerLevel level)) return;
            Entity raw = level.getEntity(msg.entityId);
            if (!(raw instanceof LivingEntity living) || !living.isAlive() || !NpcCounterpartSync.isCustomNpc(living)) return;
            if (player.distanceToSqr(living) > 64.0 * 64.0) return;
            NpcProfileWatchers.watchAndSend(player, living);
        });
        ctx.get().setPacketHandled(true);
    }
}
