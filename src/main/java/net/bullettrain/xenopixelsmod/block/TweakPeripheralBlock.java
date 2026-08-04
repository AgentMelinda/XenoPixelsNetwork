package net.bullettrain.xenopixelsmod.block;

import net.bullettrain.xenopixelsmod.block.entity.ModBlockEntities;
import net.bullettrain.xenopixelsmod.block.entity.TweakPeripheralBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tweak Peripheral Block - ComputerCraft integration for VS2 ship control
 */
public class TweakPeripheralBlock extends BaseEntityBlock {
    
    public TweakPeripheralBlock(Properties properties) {
        super(properties);
    }
    
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TweakPeripheralBlockEntity(pos, state);
    }
    
    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }
    
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @NotNull Level level, 
            @NotNull BlockState state, 
            @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(
            type, 
            ModBlockEntities.TWEAK_PERIPHERAL.get(),
            TweakPeripheralBlockEntity::serverTick
        );
    }
}
