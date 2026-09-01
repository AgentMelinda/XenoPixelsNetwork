package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → client: NPC is mid DragonMineZ transform hold. Client lerps
 * {@code HairRenderer} from the committed form hair to this target.
 */
public final class NpcTransformHoldPacket {
    private final UUID entityUuid;
    private final boolean active;
    private final boolean stack;
    private final String toGroup;
    private final String toForm;
    private final int duration;
    private final long startGameTime;

    public static NpcTransformHoldPacket cancel(UUID entityUuid) {
        return new NpcTransformHoldPacket(entityUuid, false, false, "", "", 0, 0L);
    }

    public NpcTransformHoldPacket(UUID entityUuid, String toGroup, String toForm,
                                  int duration, long startGameTime) {
        this(entityUuid, false, toGroup, toForm, duration, startGameTime);
    }

    public NpcTransformHoldPacket(UUID entityUuid, boolean stack, String toGroup, String toForm,
                                  int duration, long startGameTime) {
        this(entityUuid, true, stack, toGroup, toForm, duration, startGameTime);
    }

    private NpcTransformHoldPacket(UUID entityUuid, boolean active, boolean stack, String toGroup, String toForm,
                                   int duration, long startGameTime) {
        this.entityUuid = entityUuid;
        this.active = active;
        this.stack = stack;
        this.toGroup = toGroup == null ? "" : toGroup;
        this.toForm = toForm == null ? "" : toForm;
        this.duration = duration;
        this.startGameTime = startGameTime;
    }

    public NpcTransformHoldPacket(FriendlyByteBuf buf) {
        entityUuid = buf.readUUID();
        active = buf.readBoolean();
        stack = buf.readBoolean();
        toGroup = buf.readUtf();
        toForm = buf.readUtf();
        duration = buf.readVarInt();
        startGameTime = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(entityUuid);
        buf.writeBoolean(active);
        buf.writeBoolean(stack);
        buf.writeUtf(toGroup);
        buf.writeUtf(toForm);
        buf.writeVarInt(duration);
        buf.writeLong(startGameTime);
    }

    public static void handle(NpcTransformHoldPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.active) {
                net.bullettrain.xenopixelsmod.client.compat.npc.NpcTransformHairClient.apply(
                        msg.entityUuid, msg.stack, msg.toGroup, msg.toForm, msg.duration, msg.startGameTime);
            } else {
                net.bullettrain.xenopixelsmod.client.compat.npc.NpcTransformHairClient.clear(msg.entityUuid);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
