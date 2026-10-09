package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.v3.V3State;
import net.bullettrain.xenopixelsmod.combat.v3.V3TargetSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import java.util.UUID;
import java.util.function.Supplier;

/** S2C authoritative fighter state. All durations are bounded remaining server ticks. */
public record CombatV3StatePacket(UUID session, V3State state, V3TargetSnapshot target,
                                  int acknowledgedSequence, int chargeTicks,
                                  int windowTicksLeft, int windowTicksTotal,
                                  net.bullettrain.xenopixelsmod.combat.v3.V3Window window) {
    public CombatV3StatePacket(UUID session, V3State state, V3TargetSnapshot target, int acknowledgedSequence,
                               int chargeTicks, int windowTicksLeft, int windowTicksTotal) {
        this(session, state, target, acknowledgedSequence, chargeTicks, windowTicksLeft, windowTicksTotal,
                net.bullettrain.xenopixelsmod.combat.v3.V3Window.NONE);
    }
    public CombatV3StatePacket {
        if (session == null || state == null || window == null || acknowledgedSequence < -1
                || chargeTicks < 0 || chargeTicks > 200 || windowTicksLeft < 0
                || windowTicksTotal < 0 || windowTicksTotal > 1200 || windowTicksLeft > windowTicksTotal) {
            throw new IllegalArgumentException("Invalid V3 server state");
        }
    }
    public CombatV3StatePacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), V3State.decode(buf.readVarInt()), readTarget(buf), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                net.bullettrain.xenopixelsmod.combat.v3.V3Window.decode(buf.readVarInt()));
    }
    private static V3TargetSnapshot readTarget(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return new V3TargetSnapshot(buf.readUUID(), buf.readVarInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarLong());
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(session);
        buf.writeVarInt(state.ordinal());
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeUUID(target.target()); buf.writeVarInt(target.entityId());
            buf.writeDouble(target.position().x); buf.writeDouble(target.position().y); buf.writeDouble(target.position().z);
            buf.writeDouble(target.velocity().x); buf.writeDouble(target.velocity().y); buf.writeDouble(target.velocity().z);
            buf.writeVarLong(target.revision());
        }
        buf.writeVarInt(acknowledgedSequence); buf.writeVarInt(chargeTicks);
        buf.writeVarInt(windowTicksLeft); buf.writeVarInt(windowTicksTotal);
        buf.writeVarInt(window.ordinal());
    }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                net.bullettrain.xenopixelsmod.client.ClientPacketHandlers.handleCombatV3State(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
