package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.v3.V3Config;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import java.util.function.Supplier;

/** S2C normalized presentation settings; applying them never writes server configuration. */
public record CombatV3ConfigPacket(V3Config.Values values) {
    public CombatV3ConfigPacket {
        if (values == null) throw new IllegalArgumentException("Missing V3 config snapshot");
    }
    public CombatV3ConfigPacket(FriendlyByteBuf buf) {
        this(read(buf));
    }
    /** Field order is append-only: the three original doubles, then the 2026-10-08 tuning tail. */
    private static V3Config.Values read(FriendlyByteBuf buf) {
        try {
            return readFields(buf);
        } catch (IndexOutOfBoundsException truncated) {
            throw new IllegalArgumentException("Truncated V3 config snapshot", truncated);
        }
    }
    private static V3Config.Values readFields(FriendlyByteBuf buf) {
        double range = buf.readDouble();
        double attackerCost = buf.readDouble();
        double victimDrain = buf.readDouble();
        double dashSpeed = buf.readDouble();
        double dashLaunch = buf.readDouble();
        double dashFollow = buf.readDouble();
        int followCooldown = buf.readVarInt();
        int followWindow = buf.readVarInt();
        double strikeLaunch = buf.readDouble();
        double strikeApproach = buf.readDouble();
        boolean strikeCamera = buf.readBoolean();
        boolean dashCamera = buf.readBoolean();
        boolean sounds = buf.readBoolean();
        double chargeLaunch = buf.readDouble();
        double soundVolume = buf.readDouble();
        boolean strikeKiRequireLock = buf.isReadable() && buf.readBoolean();
        double strikeKiRange = buf.isReadable() ? buf.readDouble() : V3Config.Values.DEFAULT_STRIKE_KI_RANGE;
        double grabThrowDistance = buf.isReadable() ? buf.readDouble() : V3Config.Values.DEFAULT_GRAB_THROW_DISTANCE;
        int strikeCameraHoldTicks = buf.isReadable() ? buf.readVarInt() : V3Config.Values.DEFAULT_STRIKE_CAMERA_HOLD;
        int strikeCinematicCameraHoldTicks = buf.isReadable()
                ? buf.readVarInt() : V3Config.Values.DEFAULT_STRIKE_CINEMATIC_OPENING_HOLD;
        boolean strikeRequireLock = !buf.isReadable() || buf.readBoolean();
        return new V3Config.Values(range, attackerCost, victimDrain, dashSpeed, dashLaunch, dashFollow,
                followCooldown, followWindow, strikeLaunch, strikeApproach, strikeCamera, dashCamera, sounds,
                chargeLaunch, soundVolume, strikeKiRequireLock, strikeKiRange, grabThrowDistance,
                strikeCameraHoldTicks, strikeCinematicCameraHoldTicks, strikeRequireLock);
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(values.dragonDashRange());
        buf.writeDouble(values.heavyAttackerStaminaCost());
        buf.writeDouble(values.heavyVictimStaminaDrain());
        buf.writeDouble(values.dragonDashSpeed());
        buf.writeDouble(values.dragonDashLaunchDistance());
        buf.writeDouble(values.dragonDashFollowDistance());
        buf.writeVarInt(values.dragonDashFollowCooldownTicks());
        buf.writeVarInt(values.dragonDashFollowWindowTicks());
        buf.writeDouble(values.strikeLaunchDistance());
        buf.writeDouble(values.strikeApproachRange());
        buf.writeBoolean(values.strikeCinematicCamera());
        buf.writeBoolean(values.dashCamera());
        buf.writeBoolean(values.attackSounds());
        buf.writeDouble(values.heavyChargeLaunchDistance());
        buf.writeDouble(values.attackSoundVolume());
        buf.writeBoolean(values.strikeKiRequireLock());
        buf.writeDouble(values.strikeKiRange());
        buf.writeDouble(values.grabThrowDistance());
        buf.writeVarInt(values.strikeCameraHoldTicks());
        buf.writeVarInt(values.strikeCinematicCameraHoldTicks());
        buf.writeBoolean(values.strikeRequireLock());
    }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                net.bullettrain.xenopixelsmod.client.ClientPacketHandlers.handleCombatV3Config(this);
            }
        });
        ctx.setPacketHandled(true);
    }
}
