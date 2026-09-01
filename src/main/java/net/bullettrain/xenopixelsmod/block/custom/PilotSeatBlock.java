package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.bullettrain.xenopixelsmod.block.entity.ModBlockEntities;
import net.bullettrain.xenopixelsmod.block.entity.PilotSeatBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pilot seat. Right-click to sit; sitting is what makes you the pilot of the nearest flight
 * controller, so this block is the entry point to the whole seated-flight control path.
 *
 * <p>The block itself holds no flight state. It spawns (or re-uses) a
 * {@link XenoPilotSeatEntity}, which is what the player actually rides and which forwards
 * validated control frames to the controller. Keeping authority in the controller means a seat
 * cannot do anything the flight GUI would have refused.
 */
public class PilotSeatBlock extends BaseEntityBlock {

    public static final MapCodec<PilotSeatBlock> CODEC = simpleCodec(PilotSeatBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 9, 14);

    public PilotSeatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PilotSeatBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.PILOT_SEAT.get(), PilotSeatBlockEntity::serverTick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isPassenger()) return InteractionResult.CONSUME;

        XenoPilotSeatEntity seat = seatAt(level, pos);
        if (seat == null) {
            seat = ModEntities.PILOT_SEAT.get().create(level);
            if (seat == null) return InteractionResult.FAIL;
            seat.setPos(pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5);
            seat.setYRot(state.getValue(FACING).toYRot());
            seat.bind(pos, XenoPilotSeatEntity.findController(level, pos));
            level.addFreshEntity(seat);
        } else if (!seat.getPassengers().isEmpty()) {
            player.displayClientMessage(Component.literal("§7That seat is taken"), true);
            return InteractionResult.CONSUME;
        } else {
            // Re-resolve on every fresh mount: the controller may have been added, moved or broken.
            seat.bind(pos, XenoPilotSeatEntity.findController(level, pos));
        }

        if (!player.startRiding(seat)) return InteractionResult.FAIL;
        return InteractionResult.CONSUME;
    }

    /** The seat entity already serving this block, if one is still alive. */
    private static @Nullable XenoPilotSeatEntity seatAt(Level level, BlockPos pos) {
        List<XenoPilotSeatEntity> found = level.getEntitiesOfClass(XenoPilotSeatEntity.class,
                new AABB(pos).inflate(0.35));
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            XenoPilotSeatEntity seat = seatAt(level, pos);
            if (seat != null) {
                seat.ejectPassengers();
                seat.discard();
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
