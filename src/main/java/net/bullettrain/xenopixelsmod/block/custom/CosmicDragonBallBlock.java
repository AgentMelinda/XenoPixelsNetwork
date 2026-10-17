package net.bullettrain.xenopixelsmod.block.custom;

import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Super Dragon Ball lying in the world (design-doc 16 v1). Unbreakable; right-click with an empty hand picks it up as
 * its item, and placing the item puts the ball back so the scanner keeps tracking it.
 */
public class CosmicDragonBallBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 8, 12);
    private final int number;

    public CosmicDragonBallBlock(int number, Properties properties) {
        super(properties);
        this.number = number;
    }

    public int number() { return number; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        level.removeBlock(pos, false);
        if (!CosmicDragonBalls.isCurrent((ServerLevel) level, number, pos)) {
            // Stale copy from before a re-scatter: it fades instead of duplicating the real ball.
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1f, 0.5f);
            return InteractionResult.CONSUME;
        }
        ItemStack stack = new ItemStack(ModBlocks.COSMIC_DRAGON_BALL_ITEMS.get(number - 1).get());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.8f + number * 0.05f);
        CosmicDragonBalls.onCollected(level.getServer(), number);
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) CosmicDragonBalls.onPlaced(server, number, pos);
    }
}
