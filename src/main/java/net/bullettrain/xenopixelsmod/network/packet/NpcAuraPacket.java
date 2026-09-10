package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraResolver;
import net.bullettrain.xenopixelsmod.util.XenoIdentifierDiagnostics;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → client: this entity should show DragonMineZ's real aura shader mesh.
 * Keyed by UUID so the client does not drop the aura if the entity is not spawned yet.
 */
public final class NpcAuraPacket {
    private final UUID entityUuid;
    private final boolean on;
    private final int rgb;
    private final float scale;
    private final boolean lightnings;
    private final int lightningRgb;
    private final boolean sparking;
    private final boolean groundRing;
    private final List<NpcAuraResolver.Layer> layers;

    public NpcAuraPacket(UUID entityUuid, boolean on, int rgb) {
        this(entityUuid, on, rgb, 1.7f, false, 0xD9F4FF);
    }

    public NpcAuraPacket(UUID entityUuid, boolean on, int rgb, float scale) {
        this(entityUuid, on, rgb, scale, false, 0xD9F4FF);
    }

    public NpcAuraPacket(UUID entityUuid, boolean on, int rgb, float scale,
                         boolean lightnings, int lightningRgb) {
        this(entityUuid, on, scale, new NpcAuraResolver.Resolved(
                List.of(new NpcAuraResolver.Layer("kakarot", 0, rgb & 0xFFFFFF)),
                lightnings, lightningRgb, true, true, true));
    }

    public NpcAuraPacket(UUID entityUuid, boolean on, float scale, NpcAuraResolver.Resolved resolved) {
        this.entityUuid = entityUuid;
        this.on = on;
        this.layers = resolved == null ? List.of() : List.copyOf(resolved.layers());
        this.rgb = layers.isEmpty() ? 0xFFFFFF : layers.get(layers.size() - 1).rgb() & 0xFFFFFF;
        this.scale = scale;
        this.lightnings = resolved != null && resolved.lightning();
        this.lightningRgb = resolved == null ? 0xD9F4FF : resolved.lightningRgb() & 0xFFFFFF;
        this.sparking = resolved == null || resolved.sparking();
        this.groundRing = resolved == null || resolved.groundRing();
    }

    public NpcAuraPacket(FriendlyByteBuf buf) {
        this.entityUuid = buf.readUUID();
        this.on = buf.readBoolean();
        this.rgb = buf.readInt() & 0xFFFFFF;
        this.scale = buf.readFloat();
        this.lightnings = buf.readBoolean();
        this.lightningRgb = buf.readInt() & 0xFFFFFF;
        this.sparking = buf.readBoolean();
        this.groundRing = buf.readBoolean();
        int count = Math.max(0, Math.min(16, buf.readVarInt()));
        List<NpcAuraResolver.Layer> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String type = buf.readUtf(64);
            XenoIdentifierDiagnostics.reportIfMalformed(type,
                    "NpcAuraPacket entity=" + entityUuid + " layer=" + i + " field=type");
            read.add(new NpcAuraResolver.Layer(type, buf.readVarInt(), buf.readInt() & 0xFFFFFF));
        }
        this.layers = List.copyOf(read);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(entityUuid);
        buf.writeBoolean(on);
        buf.writeInt(rgb);
        buf.writeFloat(scale);
        buf.writeBoolean(lightnings);
        buf.writeInt(lightningRgb);
        buf.writeBoolean(sparking);
        buf.writeBoolean(groundRing);
        buf.writeVarInt(layers.size());
        for (NpcAuraResolver.Layer layer : layers) {
            buf.writeUtf(layer.type(), 64);
            buf.writeVarInt(layer.index());
            buf.writeInt(layer.rgb());
        }
    }

    public static void handle(NpcAuraPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                net.bullettrain.xenopixelsmod.client.compat.npc.NpcAuraClient.apply(
                        msg.entityUuid, msg.on, msg.rgb, msg.scale,
                        msg.lightnings, msg.lightningRgb, msg.sparking, msg.groundRing,
                        msg.layers));
        ctx.get().setPacketHandled(true);
    }
}
