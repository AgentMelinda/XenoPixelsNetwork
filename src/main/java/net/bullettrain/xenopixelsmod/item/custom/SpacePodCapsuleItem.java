package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.space.pod.SpacePodLifecycle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Reusable P15 launcher for the fixed Classic Egg Sable hull. */
public final class SpacePodCapsuleItem extends Item {
    public SpacePodCapsuleItem(Properties properties) { super(properties); }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Classic Egg Pod Capsule");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SpacePodLifecycle.Request result = SpacePodLifecycle.requestLaunch(serverPlayer);
            String message = switch (result) {
                case STARTED -> null; // The lifecycle owns the queued/build/ready messages.
                case SPACE_DISABLED -> "Space is disabled on this server";
                case PODS_DISABLED -> "Space pods are disabled on this server";
                case ALREADY_IN_POD -> "You are already aboard a space pod";
                case BUILD_PENDING -> "Your Classic Egg pod is already assembling";
            };
            if (message != null) serverPlayer.displayClientMessage(Component.literal(message), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
