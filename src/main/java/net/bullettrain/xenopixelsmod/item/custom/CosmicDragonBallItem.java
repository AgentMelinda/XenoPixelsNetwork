package net.bullettrain.xenopixelsmod.item.custom;

import net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** A held Super Dragon Ball. Normal use places it; sneak-use (in the air or on a block) tries to summon the dragon. */
public class CosmicDragonBallItem extends BlockItem {
    public CosmicDragonBallItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (!context.getLevel().isClientSide && player != null
                && !CosmicDragonBalls.legacyGameplayEnabled(context.getLevel().getServer())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.xenopixelsmod.sdb_legacy_disabled"), true);
            return InteractionResult.CONSUME;
        }
        if (player != null && player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) CosmicDragonBalls.trySummon(serverPlayer);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer serverPlayer) CosmicDragonBalls.trySummon(serverPlayer);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
