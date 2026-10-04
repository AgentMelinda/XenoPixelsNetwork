package net.bullettrain.xenopixelsmod.block.custom;

import com.mojang.math.Axis;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * The single source of truth for a wing panel's render transform, shared by
 * {@code WingPanelBlockEntityRenderer} (which draws it) and {@link PanelConfiguratorItem}'s
 * sneak-click swing readout (which measures it). Both used to carry their own hand-written copy of
 * this math and the copies drifted apart — the item's readout stopped matching what was drawn.
 *
 * <p>The panel renders as two pieces that meet at the {@link #HINGE_SEAM} line: a small fixed stub
 * and the large control surface that swings about the seam. The stub stays put; the big piece
 * moves — a hinged flap/elevator/aileron, not a slat. Which model file is which is a historical
 * accident: {@code wing_panel_flap.json} is the small <i>fixed</i> stub, {@code wing_panel_base.json}
 * is the large <i>moving</i> surface — see {@code WingPanelBlockEntityRenderer.render}.
 *
 * <p>Stock panels take their mount from a per-block-type orientation triple
 * ({@link WingPanelDebugRotation#normalOrient} / {@code horizontalOrient} / {@code verticalOrient}),
 * not from {@code AXIS} — that property only drives Sable lift and the hitbox. Fork panels add a
 * six-way {@code FACING}: the animated two-piece model must use the same X/Y rotations as
 * {@code wing_panel_fork.json}, or a north-facing flap keeps its vertical hitbox while the
 * renderer still draws a flat slab.
 *
 * <p>Pure matrix math: no client-only types, so the common {@link PanelConfiguratorItem} can call it
 * too. A {@code PoseStack} composes each call by post-multiplication exactly the way
 * {@link Matrix4f#translate} / {@link Matrix4f#rotate} do, so building the sequence on a standalone
 * {@code Matrix4f} here and handing it to {@code PoseStack.mulPose(Matrix4f)} is identical to making
 * the calls on the pose stack directly. Vertex-application order is the reverse of call order.
 */
public final class WingPanelPose {

    /**
     * Where the two pieces meet, in raw [0,1] block space along the model's Z axis — matches
     * {@code wing_panel_flap.json}'s {@code from} of 12/16. The large moving surface spans [0, this),
     * the small fixed stub [this, 1]. The deflection rotation pivots exactly on this line.
     */
    public static final double HINGE_SEAM = 0.75;

    private WingPanelPose() {
    }

    /** Transform for the fixed stub — mount orientation only, never deflects. */
    public static Matrix4f fixedMatrix(BlockState state) {
        return fixedMatrix(state, 0);
    }

    public static Matrix4f fixedMatrix(BlockState state, int hingeDeg) {
        Matrix4f m = new Matrix4f();
        m.translate(0.5f, 0.5f, 0.5f);
        applyStaticTwist(m);
        applyOrientation(m, state);
        applyHinge(m, hingeDeg);
        m.translate(-0.5f, -0.5f, -0.5f);
        return m;
    }

    /**
     * Transform for the moving control surface: the same mount orientation as the stub, plus the
     * live deflection rotated about the {@link #HINGE_SEAM} line rather than the piece's own outer
     * edge. At {@code deflectDeg == 0} this is byte-for-byte {@link #fixedMatrix}, so the surface
     * and the stub sit flush.
     */
    public static Matrix4f hingedMatrix(BlockState state, float deflectDeg) {
        return hingedMatrix(state, deflectDeg, 0);
    }

    public static Matrix4f hingedMatrix(BlockState state, float deflectDeg, int hingeDeg) {
        Matrix4f m = new Matrix4f();
        m.translate(0.5f, 0.5f, 0.5f);
        applyStaticTwist(m);
        applyOrientation(m, state);
        applyHinge(m, hingeDeg);
        m.translate(0.0f, 0.0f, (float) (HINGE_SEAM - 0.5));
        float liveDeg = WingPanelDebugRotation.deflectNegated ? -deflectDeg : deflectDeg;
        m.rotate(switch (WingPanelDebugRotation.deflectAxis) {
            case X -> Axis.XP.rotationDegrees(liveDeg);
            case Y -> Axis.YP.rotationDegrees(liveDeg);
            case Z -> Axis.ZP.rotationDegrees(liveDeg);
        });
        m.translate(-0.5f, -0.5f, (float) -HINGE_SEAM);
        return m;
    }

    /**
     * The panel's base mount orientation, one whole-degree {@code {x, y, z}} triple per block type,
     * applied so a vertex is rotated about X, then Y, then Z. Live-tuned with
     * {@code /xenowing orient}. Fork panels start from {@link #facingOrient} so the animated mesh
     * matches the hitbox, then add the v2 extra triple from {@code /xenowing}.
     */
    private static void applyOrientation(Matrix4f m, BlockState state) {
        int[] o = orientFor(state);
        if (o[2] != 0) m.rotate(Axis.ZP.rotationDegrees(o[2]));
        if (o[1] != 0) m.rotate(Axis.YP.rotationDegrees(o[1]));
        if (o[0] != 0) m.rotate(Axis.XP.rotationDegrees(o[0]));
    }

    /** Spin the small stub in the wing plane. Model +Z is the stub; this is a Y rotation. */
    private static void applyHinge(Matrix4f m, int hingeDeg) {
        int deg = Math.floorMod(hingeDeg, 360);
        if (deg != 0) {
            m.rotate(Axis.YP.rotationDegrees(deg));
        }
    }

    /** World direction of the small stub after facing + hinge. */
    public static Direction stubWorldDir(Direction facing, int hingeDeg) {
        Matrix4f m = new Matrix4f();
        int[] o = facingOrient(facing == null ? Direction.DOWN : facing);
        if (o[2] != 0) m.rotate(Axis.ZP.rotationDegrees(o[2]));
        if (o[1] != 0) m.rotate(Axis.YP.rotationDegrees(o[1]));
        if (o[0] != 0) m.rotate(Axis.XP.rotationDegrees(o[0]));
        applyHinge(m, hingeDeg);
        org.joml.Vector3f v = m.transformDirection(0f, 0f, 1f, new org.joml.Vector3f());
        return Direction.getNearest(v.x, v.y, v.z);
    }

    /**
     * 0/90/180/270 hinge so the stub aims at {@code want}. If {@code want} is along the facing
     * normal, {@code fallback} (then DOWN / SOUTH) is used.
     */
    public static int hingeToward(Direction facing, Direction want, Direction fallback) {
        Direction target = want;
        Direction face = facing == null ? Direction.UP : facing;
        if (target == null || target.getAxis() == face.getAxis()) {
            if (fallback != null && fallback.getAxis() != face.getAxis()) {
                target = fallback;
            } else if (face.getAxis().isHorizontal()) {
                target = Direction.DOWN;
            } else {
                target = Direction.SOUTH;
            }
        }
        int best = 0;
        float bestDot = -2f;
        for (int i = 0; i < 4; i++) {
            int deg = i * 90;
            Direction stub = stubWorldDir(face, deg);
            float dot = stub.getStepX() * target.getStepX()
                    + stub.getStepY() * target.getStepY()
                    + stub.getStepZ() * target.getStepZ();
            if (stub == target) {
                return deg;
            }
            if (dot > bestDot) {
                bestDot = dot;
                best = deg;
            }
        }
        return best;
    }

    static int[] orientFor(BlockState state) {
        if (state.hasProperty(WingPanelForkBlock.FACING)) {
            return composeForkOrient(state.getValue(WingPanelForkBlock.FACING), forkExtra(state));
        }
        if (state.getBlock() instanceof WingFlapVerticalBlock) return WingPanelDebugRotation.verticalOrient;
        if (state.getBlock() instanceof WingFlapHorizontalBlock) return WingPanelDebugRotation.horizontalOrient;
        return WingPanelDebugRotation.normalOrient;
    }

    static int[] composeForkOrient(Direction facing, int[] extra) {
        int[] base = facingOrient(facing);
        if (extra == null) {
            return base;
        }
        return new int[] {base[0] + extra[0], base[1] + extra[1], base[2] + extra[2]};
    }

    static int[] forkExtra(BlockState state) {
        if (state.getBlock() instanceof WingFlapVerticalForkBlock) {
            return WingPanelDebugRotation.forkVerticalOrient;
        }
        if (state.getBlock() instanceof WingFlapHorizontalForkBlock) {
            return WingPanelDebugRotation.forkHorizontalOrient;
        }
        return WingPanelDebugRotation.forkNormalOrient;
    }

    /**
     * Same X-then-Y rotations as the fork blockstate JSON ({@code facing=up} → {@code x:180},
     * {@code facing=north} → {@code x:270}, …). Applied around the block centre so a 0°
     * deflection matches the {@code ROLE=NONE} mesh.
     */
    public static int[] facingOrient(Direction facing) {
        return switch (facing) {
            case UP -> new int[] {180, 0, 0};
            case DOWN -> new int[] {0, 0, 0};
            case NORTH -> new int[] {270, 0, 0};
            case SOUTH -> new int[] {270, 180, 0};
            case WEST -> new int[] {270, 270, 0};
            case EAST -> new int[] {270, 90, 0};
        };
    }

    /**
     * Global extra rotation stacked on top of every panel's mount orientation — applied to the
     * already-oriented shape, not the raw model. The three calls are ordered so a vertex is rotated
     * about world X, then Y, then Z. See {@link WingPanelDebugRotation} and
     * {@code /xenowing statictwist}.
     */
    private static void applyStaticTwist(Matrix4f m) {
        int x = WingPanelDebugRotation.staticTwistXDegrees;
        int y = WingPanelDebugRotation.staticTwistYDegrees;
        int z = WingPanelDebugRotation.staticTwistZDegrees;
        if (z != 0) m.rotate(Axis.ZP.rotationDegrees(z));
        if (y != 0) m.rotate(Axis.YP.rotationDegrees(y));
        if (x != 0) m.rotate(Axis.XP.rotationDegrees(x));
    }
}
