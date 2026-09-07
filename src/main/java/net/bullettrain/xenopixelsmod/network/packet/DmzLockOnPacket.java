package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Server → client: force this player's DragonMineZ lock-on reticle onto an entity.
 *
 * <p>DMZ's lock-on is entirely client-side: {@code LockOnEvent.toggleLock()} scans for its own
 * target in front of the player and requires the {@code kisense} skill, {@code unlock()} clears
 * it, and the {@code lockedTarget} field itself is private with no setter. There is no server
 * entry point at all, so a script that wants to point a player at a specific entity — a boss
 * calling out a challenger, say — needs this packet plus {@code DmzLockOnAccessor}.
 *
 * <p>Carries an entity id rather than a UUID because the client resolves ids directly
 * ({@code ClientLevel.getEntity(int)}) while UUID lookup on the client goes through the
 * level's entity getter, and a lock is only meaningful for an entity the client can already see.
 */
public final class DmzLockOnPacket {
    /** Sentinel meaning "clear the lock". */
    private static final int NO_TARGET = -1;

    private final int targetEntityId;

    public DmzLockOnPacket(int targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    /** Clears the receiving player's lock. */
    public static DmzLockOnPacket clear() {
        return new DmzLockOnPacket(NO_TARGET);
    }

    public DmzLockOnPacket(FriendlyByteBuf buf) {
        this.targetEntityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(targetEntityId);
    }

    public static void handle(DmzLockOnPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                net.bullettrain.xenopixelsmod.client.compat.npc.DmzLockOnClient.apply(
                        msg.targetEntityId));
        ctx.get().setPacketHandled(true);
    }
}
