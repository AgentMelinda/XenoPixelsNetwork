package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Cosmic Ball Scanner: right-click pings for the nearest Super Dragon Ball not already in the player's inventory. */
public class CosmicBallScannerItem extends Item {
    private static final int COOLDOWN_TICKS = 20;

    public CosmicBallScannerItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(CosmicDragonBalls.radar(serverPlayer), true);
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
