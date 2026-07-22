package net.bullettrain.xenopixelsmod.item.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class StrawBerrySenzuItem extends Item {
    private static final int COOLDOWN_TICKS = 1800;

    public StrawBerrySenzuItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        // Add the cooldown on BOTH sides (like vanilla does for ender pearls/chorus fruit).
        // Adding it only on the server means the client doesn't know about the cooldown
        // until the sync packet arrives, so a fast re-click right after eating can slip
        // through without ever seeing the cooldown message.
        if (entity instanceof Player player) {
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }

        return result;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            if (level.isClientSide) {
                float percentLeft = player.getCooldowns().getCooldownPercent(this, 0.0f);
                int secondsLeft = Math.round((percentLeft * COOLDOWN_TICKS) / 20.0f);

                if (secondsLeft <= 0) {
                    secondsLeft = 1;
                }

                // 1. Regular chat message using a translation key and passing secondsLeft as an argument
                player.displayClientMessage(
                        Component.translatable("chat.xenopixelsmod.strawberry_senzu_cooldown", secondsLeft),
                        false
                );

                // 2. Action bar text using a translation key and passing secondsLeft as an argument
                player.displayClientMessage(
                        Component.translatable("actionbar.xenopixelsmod.strawberry_senzu_cooldown", secondsLeft),
                        true
                );
            }
            return InteractionResultHolder.fail(stack);
        }

        return super.use(level, player, hand);
    }
}