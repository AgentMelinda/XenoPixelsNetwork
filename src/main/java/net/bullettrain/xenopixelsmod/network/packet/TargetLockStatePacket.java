package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.combat.ClientLockState;
import net.bullettrain.xenopixelsmod.combat.targeting.LockOnQuality;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Server &rarr; client: the locking player's own lock state. Sent only to the locker, never
 * broadcast, and only when something in it actually changed (see
 * {@link net.bullettrain.xenopixelsmod.combat.targeting.TargetLockManager}) — this is the one
 * targeting packet sent every tick a lock is progressing, so it is kept small.
 *
 * <p>Velocity travels only when {@link net.bullettrain.xenopixelsmod.combat.targeting.LockOnConfig#sendVelocityForLead}
 * allows it and a lock is actually held; a client with no lock receives nothing beyond "no
 * target", which is also all vanilla's own entity tracking would have told it anyway once the
 * target left interest range.
 */
public class TargetLockStatePacket {
    public static final int NO_TARGET = -1;

    private final int targetEntityId;
    private final LockOnQuality quality;
    private final int progressPercent;
    private final boolean hasVelocity;
    private final float velX;
    private final float velY;
    private final float velZ;

    public TargetLockStatePacket(int targetEntityId, LockOnQuality quality, int progressPercent) {
        this(targetEntityId, quality, progressPercent, false, 0, 0, 0);
    }

    public TargetLockStatePacket(int targetEntityId, LockOnQuality quality, int progressPercent,
                                 boolean hasVelocity, double velX, double velY, double velZ) {
        this.targetEntityId = targetEntityId;
        this.quality = quality == null ? LockOnQuality.WEAK : quality;
        this.progressPercent = progressPercent;
        this.hasVelocity = hasVelocity;
        this.velX = (float) velX;
        this.velY = (float) velY;
        this.velZ = (float) velZ;
    }

    public static TargetLockStatePacket none() {
        return new TargetLockStatePacket(NO_TARGET, LockOnQuality.WEAK, 0);
    }

    public TargetLockStatePacket(FriendlyByteBuf buf) {
        this.targetEntityId = buf.readInt();
        this.quality = buf.readEnum(LockOnQuality.class);
        this.progressPercent = buf.readVarInt();
        this.hasVelocity = buf.readBoolean();
        this.velX = hasVelocity ? buf.readFloat() : 0f;
        this.velY = hasVelocity ? buf.readFloat() : 0f;
        this.velZ = hasVelocity ? buf.readFloat() : 0f;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(targetEntityId);
        buf.writeEnum(quality);
        buf.writeVarInt(progressPercent);
        buf.writeBoolean(hasVelocity);
        if (hasVelocity) {
            buf.writeFloat(velX);
            buf.writeFloat(velY);
            buf.writeFloat(velZ);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientLockState.accept(targetEntityId, quality, progressPercent,
                hasVelocity, velX, velY, velZ));
        ctx.setPacketHandled(true);
    }
}
