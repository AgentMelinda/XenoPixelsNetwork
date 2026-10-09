package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3Beat;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3CameraBeat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Caster-only presentation, bound to the server's fighter session, cast, and target UUID. */
public record CombatV3CameraPacket(UUID session, UUID cast, UUID target, int targetId,
                                   int durationTicks, int elapsedTicks, boolean paused,
                                   List<V3CameraBeat> beats) {
    public CombatV3CameraPacket(UUID session, UUID cast, UUID target, int targetId,
                               int durationTicks, int elapsedTicks, List<V3CameraBeat> beats) {
        this(session, cast, target, targetId, durationTicks, elapsedTicks, false, beats);
    }
    public CombatV3CameraPacket {
        if (session == null || cast == null || target == null || targetId < 0 || beats == null
                || durationTicks < 0 || durationTicks > V3Beat.MAX_TICK
                || elapsedTicks < 0 || elapsedTicks > durationTicks
                || (durationTicks == 0 && (paused || !beats.isEmpty()))) {
            throw new IllegalArgumentException("Invalid V3 camera state");
        }
        beats = V3CameraBeat.validate(beats, durationTicks);
    }
    public boolean stopping() { return durationTicks == 0; }
    public static CombatV3CameraPacket stop(UUID session, UUID cast, UUID target, int targetId) {
        return new CombatV3CameraPacket(session, cast, target, targetId, 0, 0, List.of());
    }
    public CombatV3CameraPacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUUID(), buf.readUUID(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readBoolean(), readBeats(buf));
    }
    private static List<V3CameraBeat> readBeats(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > V3CameraBeat.MAX_BEATS) throw new IllegalArgumentException("Too many V3 camera beats");
        List<V3CameraBeat> beats = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int tick = buf.readVarInt(), duration = buf.readVarInt();
            Vec3 position = vector(buf), look = vector(buf);
            float focus = buf.readFloat();
            int easing = buf.readVarInt();
            if (easing < 0 || easing >= V3CameraBeat.Easing.values().length) {
                throw new IllegalArgumentException("Unknown V3 camera easing");
            }
            beats.add(new V3CameraBeat(tick, duration, position, look, focus, V3CameraBeat.Easing.values()[easing]));
        }
        return beats;
    }
    private static Vec3 vector(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }
    private static void vector(FriendlyByteBuf buf, Vec3 value) {
        buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(session); buf.writeUUID(cast); buf.writeUUID(target); buf.writeVarInt(targetId);
        buf.writeVarInt(durationTicks); buf.writeVarInt(elapsedTicks); buf.writeBoolean(paused);
        buf.writeVarInt(beats.size());
        for (V3CameraBeat beat : beats) {
            buf.writeVarInt(beat.tick()); buf.writeVarInt(beat.duration());
            vector(buf, beat.position()); vector(buf, beat.look()); buf.writeFloat(beat.focus());
            buf.writeVarInt(beat.easing().ordinal());
        }
    }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                net.bullettrain.xenopixelsmod.client.ClientPacketHandlers.handleCombatV3Camera(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
