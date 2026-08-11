package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import net.bullettrain.xenopixelsmod.aero.AeroHitRegions;
import net.bullettrain.xenopixelsmod.aero.AeroPanelActions;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.OpenGuidancePacket;
import net.bullettrain.xenopixelsmod.network.packet.AeroStatePacket;
import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ballistic Guidance Computer — native XenoPixels ship missile aim point.
 * <p>
 * Right-click opens an XYZ GUI (set target / launch / abort). Shift-click aborts
 * flight or clears aim. Redstone / CC also fires. <b>Not Ballistix.</b>
 */
public class ShipVlsGuidanceBlock extends BaseEntityBlock {
    public static final MapCodec<ShipVlsGuidanceBlock> CODEC = simpleCodec(ShipVlsGuidanceBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public ShipVlsGuidanceBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        return new ShipVlsGuidanceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof ShipVlsGuidanceBlockEntity g) {
                g.serverTick();
            }
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection().getOpposite());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof ShipVlsGuidanceBlockEntity be) {
            be.onRedstoneChanged(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ShipVlsGuidanceBlockEntity be) {
            // Shift: server-side abort / clear (no GUI)
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    if (be.abortShipFlight()) {
                        player.displayClientMessage(Component.literal("§cShip ballistic flight aborted"), true);
                    } else {
                        be.clearTarget();
                        player.displayClientMessage(Component.literal("§7Ballistic aim cleared"), true);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            // Physical panel first: a click that lands on a modelled control operates that
            // control instead of opening the GUI. Regions come from the model geometry via
            // AeroHitRegions, so this carries no hard-coded hit percentages.
            if (!level.isClientSide && player instanceof ServerPlayer panelUser) {
                Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos));
                Vec3 model = AeroHitRegions.toModelSpace(local, state.getValue(FACING));
                AeroHitRegions.Region region = AeroHitRegions.at(model.x, model.y, model.z);
                if (region != null) {
                    AeroPanelActions.activate(be, region, panelUser);
                    return InteractionResult.CONSUME;
                }
            }

            // Normal right-click: the server owns the persisted configuration and sends
            // the complete snapshot. Client-side BE data is often stale on moving ships.
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                BlockPos t = be.getTarget();
                int ix = t != null ? t.getX() : (int) Math.floor(player.getX());
                int iy = t != null ? t.getY() : 64;
                int iz = t != null ? t.getZ() : (int) Math.floor(player.getZ());
                String status = be.getLastStatus();
                // Client hint — server does real detection; show thruster count at least
                if (be.getPairedThrusterCount() > 0) {
                    status = status + " | paired=" + be.getPairedThrusterCount();
                }
                ModNetwork.sendToPlayer(serverPlayer, new OpenGuidancePacket(
                        pos.immutable(), ix, iy, iz, status,
                        be.getPairedThrusterCount(), be.getSpeedLevel(),
                        be.getDesiredApexY(), be.getDesiredCruiseY(),
                        be.getFleetChannel(), be.getSalvoIntervalTicks(),
                        be.getGravitySi(), be.getDragCoefficient(),
                        be.getMissileBaseBlock(), be.getMissileCenterBlock(), be.getMissileNoseBlock(),
                        be.getGuidanceStopDistance()));
                ModNetwork.sendToPlayer(serverPlayer, new AeroStatePacket(
                        AeroStateSnapshot.of(pos.immutable(), be.aeroBus())));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
