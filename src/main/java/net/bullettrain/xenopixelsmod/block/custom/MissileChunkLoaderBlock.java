package net.bullettrain.xenopixelsmod.block.custom;

import net.bullettrain.xenopixelsmod.block.entity.MissileChunkLoaderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Ship-safe chunk loader for VLS / guided missiles.
 * When powered (or always if unpowered-toggle off), keeps a radius of chunks loaded.
 */
public class MissileChunkLoaderBlock extends BaseEntityBlock {
    public MissileChunkLoaderBlock(Properties props) {
        super(props);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MissileChunkLoaderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof MissileChunkLoaderBlockEntity loader) {
                loader.serverTick();
            }
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MissileChunkLoaderBlockEntity be) {
            be.toggleAlwaysOn();
            player.displayClientMessage(Component.literal(
                    "§bMissile Chunk Loader: " + (be.isAlwaysOn() ? "§aALWAYS ON" : "§eREDSTONE only")
                            + " §7(radius " + be.getRadius() + ")"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
