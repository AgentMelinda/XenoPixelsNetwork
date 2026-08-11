package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * Server → client combat impact cue: what happened, where, and how hard.
 *
 * <p>Only the camera and screen response travels this way. The world particles are ordinary
 * server-spawned particles that vanilla already replicates, so this packet stays small enough to
 * send once per landed hit during a combo.
 *
 * <p>Direction is quantised to bytes. It only ever steers a camera kick, where a degree or two
 * is invisible, and three bytes instead of three doubles matters when a rush chain can fire this
 * a dozen times a second to everyone nearby.
 */
public class CombatFxPacket {

    private final CombatFxKind kind;
    private final Vec3 pos;
    private final Vec3 dir;
    private final float intensity;

    public CombatFxPacket(CombatFxKind kind, Vec3 pos, Vec3 dir, float intensity) {
        this.kind = kind;
        this.pos = pos;
        this.dir = dir;
        this.intensity = intensity;
    }

    public CombatFxPacket(FriendlyByteBuf buf) {
        this.kind = buf.readEnum(CombatFxKind.class);
        this.pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.dir = new Vec3(buf.readByte() / 127.0, buf.readByte() / 127.0, buf.readByte() / 127.0);
        this.intensity = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeByte(quantise(dir.x));
        buf.writeByte(quantise(dir.y));
        buf.writeByte(quantise(dir.z));
        buf.writeFloat(intensity);
    }

    /** Unit component to a signed byte. Clamped so a slightly-unnormalised input cannot wrap. */
    private static int quantise(double component) {
        return (int) Math.round(Math.max(-1.0, Math.min(1.0, component)) * 127.0);
    }

    public CombatFxKind kind() {
        return kind;
    }

    public Vec3 pos() {
        return pos;
    }

    public Vec3 dir() {
        return dir;
    }

    public float intensity() {
        return intensity;
    }

    public static void handle(CombatFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.bullettrain.xenopixelsmod.client.ClientScreens.receiveCombatFx.accept(msg));
        ctx.get().setPacketHandled(true);
    }
}
