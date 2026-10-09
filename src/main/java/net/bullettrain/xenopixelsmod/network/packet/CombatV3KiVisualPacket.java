package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;
import net.bullettrain.xenopixelsmod.fx.ki.KiLook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Server-authored native DMZ geometry; never creates a gameplay entity. */
public record CombatV3KiVisualPacket(ResourceLocation dimension, UUID id, UUID owner, Phase phase,
                                     KiLook.Kind kind, int renderType, int core, int edge, float size,
                                     Vec3 position, Vec3 forward, float length) {
    public enum Phase { CHARGE, FLIGHT, IMPACT, REMOVE, CLEAR_OWNER }

    public CombatV3KiVisualPacket {
        if (dimension == null || id == null || owner == null || phase == null || kind == null
                || position == null || forward == null || !finite(position) || !finite(forward)
                || !Float.isFinite(size) || size <= 0 || size > 32
                || !Float.isFinite(length) || length < 0 || length > 1024 || renderType < 0 || renderType > 11)
            throw new IllegalArgumentException("Invalid V3 ki visual");
    }

    public CombatV3KiVisualPacket(FriendlyByteBuf buf) {
        this(buf.readResourceLocation(), buf.readUUID(), buf.readUUID(), phase(buf.readVarInt()),
                kind(buf.readVarInt()), buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readFloat(),
                vector(buf), vector(buf), buf.readFloat());
    }

    private static Phase phase(int id) {
        if (id < 0 || id >= Phase.values().length) throw new IllegalArgumentException("Invalid ki visual phase");
        return Phase.values()[id];
    }
    private static KiLook.Kind kind(int id) {
        if (id < 0 || id >= KiLook.Kind.values().length) throw new IllegalArgumentException("Invalid ki visual kind");
        return KiLook.Kind.values()[id];
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static Vec3 vector(FriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    private static void vector(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    public static void encode(CombatV3KiVisualPacket p, FriendlyByteBuf b) {
        b.writeResourceLocation(p.dimension); b.writeUUID(p.id); b.writeUUID(p.owner);
        b.writeVarInt(p.phase.ordinal()); b.writeVarInt(p.kind.ordinal());
        b.writeVarInt(p.renderType);
        b.writeInt(p.core); b.writeInt(p.edge); b.writeFloat(p.size);
        vector(b, p.position); vector(b, p.forward); b.writeFloat(p.length);
    }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT)
                net.bullettrain.xenopixelsmod.client.ClientPacketHandlers.handleCombatV3KiVisual(this);
        });
        ctx.setPacketHandled(true);
    }
}
