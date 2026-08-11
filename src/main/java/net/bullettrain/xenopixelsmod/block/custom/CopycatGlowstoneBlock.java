package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.bullettrain.xenopixelsmod.block.entity.CopycatGlowstoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A full-bright decorative block whose rendered material can be replaced by
 * right-clicking it with any normal block item.
 */
public final class CopycatGlowstoneBlock extends BaseEntityBlock {
    public static final MapCodec<CopycatGlowstoneBlock> CODEC = simpleCodec(CopycatGlowstoneBlock::new);
    private static final ResourceLocation CREATE_WRENCH =
            ResourceLocation.fromNamespaceAndPath("create", "wrench");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public CopycatGlowstoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopycatGlowstoneBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, tickPos, tickState, raw) -> {
            if (raw instanceof CopycatGlowstoneBlockEntity copycat)
                copycat.serverTick(tickLevel, tickPos, tickState);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (isCreateWrench(stack)
                && level.getBlockEntity(pos) instanceof CopycatGlowstoneBlockEntity copycat
                && copycat.hasCustomMaterial()) {
            if (!level.isClientSide && copycat.resetMaterial()) {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                        SoundSource.BLOCKS, 0.8F, 1.1F);
                player.displayClientMessage(Component.translatable(
                        "message.xenopixelsmod.copycat_glowstone_unbound"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!(stack.getItem() instanceof BlockItem blockItem)
                || blockItem.getBlock() instanceof CopycatGlowstoneBlock
                || !(level.getBlockEntity(pos) instanceof CopycatGlowstoneBlockEntity copycat)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (copycat.hasCustomMaterial()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(
                    "message.xenopixelsmod.copycat_glowstone_locked"), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockState material = orientMaterial(blockItem.getBlock().defaultBlockState(), hitResult.getDirection());
        if (material.isAir() || material.getRenderShape() == RenderShape.INVISIBLE) {
            return ItemInteractionResult.FAIL;
        }

        if (!level.isClientSide && copycat.applyMaterial(material)) {
            level.playSound(null, pos, material.getSoundType(level, pos, player).getPlaceSound(),
                    SoundSource.BLOCKS, 0.8F, 1.0F);
            player.displayClientMessage(Component.translatable(
                    "message.xenopixelsmod.copycat_glowstone_applied", material.getBlock().getName()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * NeoForge connected-texture models query appearances instead of raw neighbour states.
     * Returning the stored material here is Create's fast copycat connectivity path: it avoids
     * wrapping the whole world and looking up neighbouring block entities during model baking.
     */
    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos,
                                    Direction side, @Nullable BlockState queryState,
                                    @Nullable BlockPos queryPos) {
        if (level.getBlockEntity(pos) instanceof CopycatGlowstoneBlockEntity copycat) {
            BlockState material = copycat.getMaterial();
            if (!material.is(this)) return material;
        }
        return state;
    }

    private static BlockState orientMaterial(BlockState material, Direction face) {
        Direction.Axis axis = face.getAxis();
        if (material.hasProperty(BlockStateProperties.FACING))
            material = material.setValue(BlockStateProperties.FACING, face);
        if (material.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && axis != Direction.Axis.Y)
            material = material.setValue(BlockStateProperties.HORIZONTAL_FACING, face);
        if (material.hasProperty(BlockStateProperties.AXIS))
            material = material.setValue(BlockStateProperties.AXIS, axis);
        if (material.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) && axis != Direction.Axis.Y)
            material = material.setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
        return material;
    }

    private static boolean isCreateWrench(ItemStack stack) {
        return !stack.isEmpty() && CREATE_WRENCH.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
