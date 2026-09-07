package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.clone.CloneTargetTracker;
import net.bullettrain.xenopixelsmod.combat.clone.XenoCloneSystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

/** Client → server: current lock-on target id for clone AI. */
public class CloneTargetPacket {
    private final int targetId;

    public CloneTargetPacket(int targetId) {
        this.targetId = targetId;
    }

    public CloneTargetPacket(FriendlyByteBuf buf) {
        this.targetId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.targetId);
    }

    public static void handle(CloneTargetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            long now = player.serverLevel().getGameTime();
            XenoCloneSystem.TARGET_TRACKER.accept(player.getUUID(), msg.targetId, now,
                    id -> player.level().getEntity(id) instanceof LivingEntity living && living.isAlive() ? living : null,
                    living -> living.isAlive() && !living.isRemoved() && living.level() == player.level());
        });
        ctx.get().setPacketHandled(true);
    }
}