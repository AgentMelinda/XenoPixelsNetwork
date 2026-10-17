package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.oxygen.OxygenSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Design-doc §6 Breathing Capsule: right-click adds {@code oxygenCapsuleSeconds} of oxygen supply (capped at
 * {@code oxygenReserveCapSeconds}) that is used before holding breath in vacuum. Does nothing while oxygen is off.
 */
public class BreathingCapsuleItem extends Item {
    private static final int COOLDOWN_TICKS = 10;

    public BreathingCapsuleItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!XenoSpaceConfig.oxygenEnabled()) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) return InteractionResultHolder.consume(stack);
        if (!(player instanceof ServerPlayer serverPlayer) || !OxygenSystem.addCapsule(serverPlayer)) {
            player.displayClientMessage(Component.translatable("actionbar.xenopixelsmod.breathing_capsule_full"), true);
            return InteractionResultHolder.fail(stack);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8f, 1.4f);
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }
}
