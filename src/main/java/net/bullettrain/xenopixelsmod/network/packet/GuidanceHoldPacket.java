package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.common.compat.CameraAimHelper;
import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.technique.KiGuidance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * Client → server: Ki Guidance key is down, lock-on id, and camera aim.
 *
 * <p>Same shape as {@code SokidanControlC2S.aim}: store camera aim, then the server
 * finds the owner's live ki and steers it. Target id is re-checked in the world.
 */
public class GuidanceHoldPacket {

    private final int targetId;
    private final float aimX;
    private final float aimY;
    private final float aimZ;

    public GuidanceHoldPacket(int targetId, Vec3 aim) {
        this.targetId = targetId;
        this.aimX = (float) aim.x;
        this.aimY = (float) aim.y;
        this.aimZ = (float) aim.z;
    }

    public GuidanceHoldPacket(FriendlyByteBuf buf) {
        this.targetId = buf.readInt();
        this.aimX = buf.readFloat();
        this.aimY = buf.readFloat();
        this.aimZ = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.targetId);
        buf.writeFloat(this.aimX);
        buf.writeFloat(this.aimY);
        buf.writeFloat(this.aimZ);
    }

    public static void handle(GuidanceHoldPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.level() == null) return;
            CameraAimHelper.store(player, new Vec3(msg.aimX, msg.aimY, msg.aimZ));
            KiGuidance.reportHeld(player, msg.targetId);
            KiGuidance.guideOwned(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
