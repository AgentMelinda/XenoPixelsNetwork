package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.bullettrain.xenopixelsmod.block.entity.WingPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (fork) wing / flap. Same textures as {@link WingPanelBlock}, plus a six-way {@code FACING}
 * that {@code sable$getNormal} follows.
 */
public class WingPanelForkBlock extends WingPanelBlock {
    public static final MapCodec<WingPanelForkBlock> CODEC = simpleCodec(WingPanelForkBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public WingPanelForkBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return withFacing(defaultBlockState(), context.getClickedFace());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        applyPlacementHinge(level, pos, state, placer);
    }

    /** Aim the small stub at the placer. Used after place so ROLE=NONE still shows the hinge. */
    public static void applyPlacementHinge(Level level, BlockPos pos, BlockState state,
                                          @Nullable LivingEntity placer) {
        if (level == null || level.isClientSide || placer == null || !state.hasProperty(FACING)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof WingPanelBlockEntity panel)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        Vec3 delta = placer.getEyePosition().subtract(Vec3.atCenterOf(pos));
        Direction want = Direction.getNearest(delta.x, delta.y, delta.z);
        panel.setHingeRotation(WingPanelPose.hingeToward(facing, want, placer.getDirection()));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
                .setValue(AXIS, state.getValue(FACING).getAxis());
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public Direction sable$getNormal(BlockState state) {
        return liftNormal(state);
    }

    public static Direction liftNormal(BlockState state) {
        Direction facing = state != null && state.hasProperty(FACING) ? state.getValue(FACING) : null;
        Direction.Axis axis = state != null && state.hasProperty(AXIS) ? state.getValue(AXIS) : null;
        return liftNormal(facing, axis);
    }

    /** Facing wins. Used by {@code sable$getNormal} and unit tests that cannot build a blockstate. */
    public static Direction liftNormal(Direction facing, Direction.Axis axis) {
        if (facing != null) {
            return facing;
        }
        if (axis != null) {
            return Direction.get(Direction.AxisDirection.POSITIVE, axis);
        }
        return Direction.UP;
    }

    public static BlockState withFacing(BlockState state, Direction facing) {
        if (state == null || facing == null || !state.hasProperty(FACING)) {
            return state;
        }
        BlockState next = state.setValue(FACING, facing);
        if (next.hasProperty(AXIS)) {
            next = next.setValue(AXIS, facing.getAxis());
        }
        return next;
    }
}
