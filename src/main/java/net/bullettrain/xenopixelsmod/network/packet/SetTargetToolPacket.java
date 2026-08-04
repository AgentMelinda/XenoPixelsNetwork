package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.item.custom.TargetToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: persist an XYZ target on the Target Tool currently held by the player. */
public final class SetTargetToolPacket {
    private final int x;
    private final int y;
    private final int z;

    public SetTargetToolPacket(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public SetTargetToolPacket(FriendlyByteBuf buf) {
        this.x = buf.readVarInt();
        this.y = buf.readVarInt();
        this.z = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ItemStack tool = heldTool(player);
            if (tool.isEmpty()) {
                player.displayClientMessage(Component.translatable(
                        "chat.xenopixelsmod.target_tool_not_held"), true);
                return;
            }

            ServerLevel level = player.serverLevel();
            int cx = Math.max(-30_000_000, Math.min(30_000_000, x));
            int cy = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, y));
            int cz = Math.max(-30_000_000, Math.min(30_000_000, z));
            TargetToolItem.setStoredTarget(tool, new BlockPos(cx, cy, cz));
            player.getInventory().setChanged();
            player.displayClientMessage(Component.translatable(
                    "chat.xenopixelsmod.target_set", cx, cy, cz), true);
        });
        context.setPacketHandled(true);
    }

    private static ItemStack heldTool(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof TargetToolItem) return main;
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        return off.getItem() instanceof TargetToolItem ? off : ItemStack.EMPTY;
    }
}
