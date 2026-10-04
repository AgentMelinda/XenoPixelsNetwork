package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.bullettrain.xenopixelsmod.block.entity.MissileTubeBlockEntity;
import net.bullettrain.xenopixelsmod.item.custom.MissileItem;
import net.bullettrain.xenopixelsmod.missile.MissileWarhead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Launch tube for native XenoPixels ballistic missiles (realistic loft + terminal).
 * Facing = eject / rail direction (usually UP on ships).
 */
public class MissileTubeBlock extends BaseEntityBlock {
    public static final MapCodec<MissileTubeBlock> CODEC = simpleCodec(MissileTubeBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public MissileTubeBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MissileTubeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return null;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MissileTubeBlockEntity tube) {
            tube.tickCooldown();
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction d = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()
                ? ctx.getClickedFace()
                : ctx.getNearestLookingDirection().getOpposite();
        if (ctx.getPlayer() != null && !ctx.getPlayer().isShiftKeyDown()) {
            d = Direction.UP;
        }
        return defaultBlockState().setValue(FACING, d);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof MissileTubeBlockEntity tube)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!MissileItem.isBody(stack) && !MissileWarhead.isWarhead(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            if (tube.tryInsert(player, stack)) {
                player.displayClientMessage(tube.statusLine(state.getValue(FACING)), true);
            } else {
                player.displayClientMessage(Component.literal("§cTube slot already filled."), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MissileTubeBlockEntity be) {
            if (player.isShiftKeyDown()) {
                if (be.tryExtract(player)) {
                    player.displayClientMessage(be.statusLine(state.getValue(FACING)), true);
                } else {
                    be.toggleArmed();
                    player.displayClientMessage(Component.literal(
                            "§6Missile tube: " + (be.isArmed() ? "§aARMED" : "§cSAFE")
                                    + (be.getCooldown() > 0 ? " §7(reload " + (be.getCooldown() / 20) + "s)" : "")), true);
                }
            } else {
                player.displayClientMessage(be.statusLine(state.getValue(FACING)), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MissileTubeBlockEntity tube) {
            tube.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
