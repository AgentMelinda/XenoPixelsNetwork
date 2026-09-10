package net.bullettrain.xenopixelsmod.api.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.function.Consumer;

/** Context supplied to addon packet handlers without exposing DragonMineZ networking types. */
public final class AddonPacketContext {
    private final AddonPacketDirection direction;
    private final ServerPlayer sender;
    private final Consumer<Runnable> workQueue;

    AddonPacketContext(AddonPacketDirection direction, ServerPlayer sender, Consumer<Runnable> workQueue) {
        this.direction = direction;
        this.sender = sender;
        this.workQueue = workQueue;
    }

    public AddonPacketDirection direction() {
        return direction;
    }

    /** Present only for a serverbound packet handled on the logical server. */
    public Optional<ServerPlayer> sender() {
        return Optional.ofNullable(sender);
    }

    /** Queues work on the receiving side's main thread. */
    public void enqueueWork(Runnable work) {
        workQueue.accept(work);
    }
}
