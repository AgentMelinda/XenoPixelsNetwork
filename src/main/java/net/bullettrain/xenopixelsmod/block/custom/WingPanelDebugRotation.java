package net.bullettrain.xenopixelsmod.block.custom;

import net.minecraft.core.Direction;

/**
 * Live-tunable overrides for the wing-panel deflection transform ({@link WingPanelPose}), set via
 * {@code /xenowing} ({@link net.bullettrain.xenopixelsmod.client.command.WingPanelDebugCommands}).
 *
 * <p>Exists because every prior fix to this transform needed a recompile, a rebuild, and a full
 * client relaunch to judge by eye. This lets the base mount orientation, deflection axis/sign, a
 * global twist, and a fixed test angle be tried live, in-game, with no rebuild in between.
 * Client-set, in-memory, not persisted — deliberately a scratch pad, not a config: once a
 * combination is confirmed correct it belongs back in {@link WingPanelPose} as the real default,
 * not left depending on this class.
 *
 * <p>Lives in {@code block.custom} rather than a client package because {@link WingPanelPose} — and
 * through it the common {@link PanelConfiguratorItem} — reads these fields; only the {@code /xenowing}
 * command that writes them is client-only.
 */
public final class WingPanelDebugRotation {

    /**
     * Per-block-type base mount orientation, whole degrees as {@code {x, y, z}}, applied so a
     * vertex is rotated about X, then Y, then Z. This is what replaces the old per-{@code AXIS}
     * blockstate-mimicry: a wing panel's look is now decided purely by which of the three blocks
     * it is, not by its {@code AXIS} property (which only drives Sable lift and the hitbox now).
     * {@code /xenowing orient <normal|horizontal|vertical> <x|y|z> <degrees>}.
     */
    public static volatile int[] normalOrient = {0, -90, 180};
    public static volatile int[] horizontalOrient = {0, 0, 0};
    public static volatile int[] verticalOrient = {90, 180, 90};

    /**
     * Axis the flap's live deflection rotates around, in the raw model frame (before the mount
     * orientation). The moving surface spans the full model X, is thin in Y, and its hinge line
     * runs along model-local <b>X</b> at Z = {@link WingPanelPose#HINGE_SEAM} — so {@code X} is the
     * correct value by construction. {@code Y}/{@code Z} remain selectable only to rule them out.
     * {@code /xenowing deflectaxis <x|y|z>}.
     */
    public static volatile Direction.Axis deflectAxis = Direction.Axis.X;

    /** Flips the sign of the deflection angle on top of {@link #deflectAxis} — independent of
     * {@code INVERT}/role sign, purely for finding the right raw rotation direction.
     * {@code /xenowing deflectsign <+|->}. */
    public static volatile boolean deflectNegated = false;

    /**
     * Extra rotation stacked on top of every panel's mount orientation, in whole degrees — a
     * global nudge, applied about world X, then Y, then Z, after the per-type orientation.
     * {@code /xenowing statictwist <x|y|z> <+90|-90|reset>}.
     */
    public static volatile int staticTwistXDegrees = 0;
    public static volatile int staticTwistYDegrees = 0;
    public static volatile int staticTwistZDegrees = 0;

    /**
     * When not {@code NaN}, every rendered flap is held at exactly this angle (degrees) instead of
     * its live animated deflection — so axis/sign/orientation can be judged on a single placed
     * panel with no ship, flight controller or seat. {@code /xenowing testdeflect <degrees|off>}.
     */
    public static volatile double testDeflectDeg = Double.NaN;

    private WingPanelDebugRotation() {
    }
}
