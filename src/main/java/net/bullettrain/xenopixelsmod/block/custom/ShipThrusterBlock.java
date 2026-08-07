package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.bullettrain.xenopixelsmod.block.entity.ShipThrusterBlockEntity;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Directional VS2 thruster. Facing = exhaust (plume) direction; thrust is opposite.
 * Redstone or ComputerCraft sets power 0..1. Particles mimic Create propulsion plumes.
 */
public class ShipThrusterBlock extends BaseEntityBlock {
    public static final MapCodec<ShipThrusterBlock> CODEC = simpleCodec(ShipThrusterBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ShipThrusterBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(POWERED, false));
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
        return new ShipThrusterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof ShipThrusterBlockEntity thruster) {
                thruster.tick();
            }
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Look direction = thrust exhaust (players look where they want flame to go)
        Direction face = ctx.getNearestLookingDirection();
        boolean powered = ctx.getLevel().hasNeighborSignal(ctx.getClickedPos());
        return defaultBlockState().setValue(FACING, face).setValue(POWERED, powered);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) return;
        // Single redstone read on neighbor change — thruster BE never polls hasNeighborSignal
        boolean powered = level.hasNeighborSignal(pos);
        if (level.getBlockEntity(pos) instanceof ShipThrusterBlockEntity be) {
            be.onRedstoneChanged(powered);
        }
        // Do not flip POWERED here — BE uses POWERED for plume/lit, not raw redstone
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ShipThrusterBlockEntity be) {
            if (player.isShiftKeyDown()) {
                // Shift: pair / unpair with nearest Ballistic Guidance Computer
                if (be.isPaired()) {
                    BlockPos old = be.getPairedGuidance();
                    be.setPairedGuidance(null);
                    if (old != null && level.getBlockEntity(old) instanceof ShipVlsGuidanceBlockEntity g) {
                        g.unregisterThruster(pos);
                    }
                    player.displayClientMessage(Component.literal("§7Thruster unpaired from guidance"), true);
                } else {
                    ShipVlsGuidanceBlockEntity nearest = findNearestGuidance(level, pos, 16);
                    if (nearest == null) {
                        player.displayClientMessage(Component.literal(
                                "§cNo guidance computer within 16 blocks to pair"), true);
                    } else {
                        be.setPairedGuidance(nearest.getBlockPos());
                        nearest.registerThruster(pos);
                        player.displayClientMessage(Component.literal(
                                "§aThruster paired → guidance @ "
                                        + nearest.getBlockPos().getX() + " "
                                        + nearest.getBlockPos().getY() + " "
                                        + nearest.getBlockPos().getZ()
                                        + " §8(redstone on guidance launches)"), true);
                    }
                }
            } else {
                String pair = be.isPaired()
                        ? " §bpaired→ " + be.getPairedGuidance().getX() + ","
                        + be.getPairedGuidance().getY() + "," + be.getPairedGuidance().getZ()
                        : " §8unpaired (shift-click to pair)";
                player.displayClientMessage(Component.literal(
                        String.format("§6Thruster §7power=§f%.0f%% §7force=§f%.0f §7%s%s",
                                be.getPower() * 100f,
                                be.getMaxForce() * be.getPower(),
                                be.isActive() ? "§aACTIVE" : "§8idle",
                                pair)), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static @Nullable ShipVlsGuidanceBlockEntity findNearestGuidance(Level level, BlockPos from, int r) {
        ShipVlsGuidanceBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(from.offset(-r, -r, -r), from.offset(r, r, r))) {
            if (level.getBlockEntity(p) instanceof ShipVlsGuidanceBlockEntity g) {
                double d = p.distSqr(from);
                if (d < bestD) {
                    bestD = d;
                    best = g;
                }
            }
        }
        return best;
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
        builder.add(FACING, POWERED);
    }
}
