package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client request to persist three ship-local missile body reference blocks. */
public final class BodyCalibrationPacket {
    private final BlockPos computerPos;
    private final BlockPos base;
    private final BlockPos center;
    private final BlockPos nose;

    public BodyCalibrationPacket(BlockPos computerPos, BlockPos base, BlockPos center, BlockPos nose) {
        this.computerPos = computerPos;
        this.base = base;
        this.center = center;
        this.nose = nose;
    }

    public BodyCalibrationPacket(FriendlyByteBuf buf) {
        computerPos = buf.readBlockPos();
        base = buf.readBlockPos();
        center = buf.readBlockPos();
        nose = buf.readBlockPos();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(computerPos);
        buf.writeBlockPos(base);
        buf.writeBlockPos(center);
        buf.writeBlockPos(nose);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.serverLevel().hasChunkAt(computerPos)
                    || !(player.serverLevel().getBlockEntity(computerPos)
                    instanceof ShipVlsGuidanceBlockEntity guidance)) return;
            var world = guidance.getLaunchWorldPosition();
            if (player.distanceToSqr(world.x, world.y, world.z) > 64.0 * 64.0) {
                player.displayClientMessage(Component.literal("§cToo far from guidance computer"), true);
                return;
            }
            ShipVlsGuidanceBlockEntity.BodyCalibrationResult result =
                    guidance.applyMissileBodyCalibration(base, center, nose);
            player.displayClientMessage(Component.literal(
                    (result == ShipVlsGuidanceBlockEntity.BodyCalibrationResult.APPLIED ? "§a" : "§cCalibration failed: ")
                            + result.message()), true);
        });
        context.setPacketHandled(true);
    }
}
