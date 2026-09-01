package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.targeting.TargetLockManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Client &rarr; server: attempt a lock, clear the current one, or cycle to the nearest other
 * candidate. This is the only thing the client ever tells the server about targeting — which
 * entity id it would like locked. Every rule about whether that is allowed lives in
 * {@link net.bullettrain.xenopixelsmod.combat.targeting.LockOnValidator} on the server; nothing
 * here is trusted beyond "the player pressed a button".
 */
public class TargetLockPacket {

    public enum Kind { REQUEST, CLEAR, CYCLE }

    private final Kind kind;
    private final int targetEntityId;

    public TargetLockPacket(Kind kind, int targetEntityId) {
        this.kind = kind;
        this.targetEntityId = targetEntityId;
    }

    public static TargetLockPacket request(int targetEntityId) {
        return new TargetLockPacket(Kind.REQUEST, targetEntityId);
    }

    public static TargetLockPacket clear() {
        return new TargetLockPacket(Kind.CLEAR, -1);
    }

    public static TargetLockPacket cycle() {
        return new TargetLockPacket(Kind.CYCLE, -1);
    }

    public TargetLockPacket(FriendlyByteBuf buf) {
        this.kind = buf.readEnum(Kind.class);
        this.targetEntityId = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeInt(targetEntityId);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            switch (kind) {
                case REQUEST -> TargetLockManager.requestLock(player, targetEntityId);
                case CLEAR -> TargetLockManager.clearLock(player, "cleared");
                case CYCLE -> TargetLockManager.cycleTarget(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
