package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import net.bullettrain.xenopixelsmod.block.entity.ModBlockEntities;
import net.bullettrain.xenopixelsmod.block.entity.WingPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A wing panel: a real aerodynamic surface that generates lift where it is placed.
 *
 * <p>Unlike the controller's flap setting — one lumped coefficient for a whole hull — this is
 * per-block aerodynamics run by Sable itself. Implementing {@link BlockSubLevelLiftProvider} is
 * the entire integration: {@code ServerLevelPlot} watches for blocks of this kind as they are
 * placed and broken, and {@code ServerSubLevel} groups them and applies their combined lift and
 * drag at the group's own centre every physics tick. A craft's handling therefore comes from how
 * it is actually built.
 *
 * <p><b>Modelled on the shipped reference.</b> Create Simulated's {@code SymmetricSailBlock} is
 * the same integration done by the people who wrote the physics engine, and two of its choices
 * are copied here deliberately:
 *
 * <ul>
 *   <li><b>An axis, not a facing.</b> An aerodynamic surface is symmetric — air pushing on one
 *       face is the same surface as air pushing on the other, and which of the two opposite
 *       normals is "the" normal is meaningless. So the state carries {@code AXIS} (the same
 *       property {@link net.minecraft.world.level.block.RotatedPillarBlock} uses) and the normal
 *       is the positive direction along it, exactly as the sail does. A six-way facing would let
 *       two identical panels disagree about their own geometry.</li>
 *   <li><b>Placed by look direction.</b> The axis comes from
 *       {@link BlockPlaceContext#getNearestLookingDirection()}, so panels line up with how the
 *       builder is looking at the hull, and a row of them placed while walking along a wing all
 *       come out parallel.</li>
 * </ul>
 *
 * <p>Where this deliberately differs: the symmetric sail returns a lift scalar of {@code 0} and a
 * raised parallel drag of {@code 1.75} — it is a sail, a drag surface that catches wind. A wing
 * is the opposite instrument, so this keeps Sable's default lift and default drag, which is what
 * Sable's own Create-sail integration uses for a lifting surface.
 *
 * <p>Deflection is a continuous angle held by {@link WingPanelBlockEntity}, driven by whichever
 * control input {@link #ROLE} assigns the panel to (flap, pitch, roll, yaw, or air brake) — see
 * {@link net.bullettrain.xenopixelsmod.aero.control.AeroFlightCore}, which writes it each flight
 * tick, and {@link #getRenderShape} for why only role-assigned panels are drawn by a renderer
 * at all. The real
 * aerodynamic effect of a panel is always the same lift/drag pass regardless of role or angle,
 * because Sable's lift scalars are per-block-type and cannot vary per ship, per panel or per tick
 * — role and deflection only decide what the panel <i>looks</i> like it is doing.
 *
 * <p>{@code AXIS} is not {@link net.minecraft.world.level.block.RotatedPillarBlock}'s own property
 * inherited via that class, since a block entity is needed here and Java has no multiple
 * inheritance; {@link #rotate} and {@link #mirror} below replicate that class's own behaviour for
 * it so a WorldEdit/structure rotation still re-orients placed panels correctly.
 */
public class WingPanelBlock extends BaseEntityBlock implements BlockSubLevelLiftProvider {

    public static final MapCodec<WingPanelBlock> CODEC = simpleCodec(WingPanelBlock::new);

    /** Surface axis. The lift normal is the positive direction along it. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    /** Which control input this panel visually tracks; see {@link PanelRole}. */
    public static final EnumProperty<PanelRole> ROLE = EnumProperty.create("role", PanelRole.class);
    /** Flips the sign of the commanded deflection — two panels sharing a role (e.g. ailerons on
     * opposite wingtips) need to move opposite ways for the same command. */
    public static final BooleanProperty INVERT = BooleanProperty.create("invert");

    /** Centred slab: a symmetric surface has no near or far side. */
    private static final VoxelShape SHAPE_Y = Block.box(0, 6.5, 0, 16, 9.5, 16);
    private static final VoxelShape SHAPE_X = Block.box(6.5, 0, 0, 9.5, 16, 16);
    private static final VoxelShape SHAPE_Z = Block.box(0, 0, 6.5, 16, 16, 9.5);

    public WingPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(ROLE, PanelRole.NONE)
                .setValue(INVERT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, ROLE, INVERT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
    }

    /** Replicates {@code RotatedPillarBlock.rotatePillar}, which is not reachable without extending it. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90, CLOCKWISE_90 -> switch (state.getValue(AXIS)) {
                case X -> state.setValue(AXIS, Direction.Axis.Z);
                case Z -> state.setValue(AXIS, Direction.Axis.X);
                default -> state;
            };
            default -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    /**
     * Structural panels are baked into the chunk mesh; only the ones actually assigned a control
     * role are drawn by {@link net.bullettrain.xenopixelsmod.client.render.WingPanelBlockEntityRenderer}.
     *
     * <p>This split is the whole reason a wing can be built out of hundreds of these without the
     * renderer noticing. {@link RenderShape#ENTITYBLOCK_ANIMATED} takes a block out of the
     * section mesh entirely and hands it to a per-frame block-entity draw — correct for a
     * surface that has to move through in-between angles, ruinous for the skin of a hull that
     * never moves at all. A builder assigns a role to the handful of blocks that are ailerons,
     * elevators and rudders; everything else stays {@link RenderShape#MODEL} and costs nothing.
     *
     * <p>Vanilla's {@code SectionCompiler} meshes only {@code MODEL} states and collects block
     * entities regardless, so the two paths never both draw the same panel — which is what would
     * have happened had the renderer simply been registered against the old always-{@code MODEL}
     * shape: the mesh would draw the panel flat while the renderer drew a second, deflected copy
     * through it.
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(ROLE) == PanelRole.NONE
                ? RenderShape.MODEL : RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WingPanelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                             BlockEntityType<T> type) {
        if (!level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.WING_PANEL.get(),
                (lvl, pos, st, be) -> be.tickClientAnimation());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Z -> SHAPE_Z;
            default -> SHAPE_Y;
        };
    }

    /** A wing is a thin sheet; mobs should not treat one as a floor to path across. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // --- Sable lift provider ---

    @Override
    public Direction sable$getNormal(BlockState state) {
        return Direction.get(Direction.AxisDirection.POSITIVE, state.getValue(AXIS));
    }

}
