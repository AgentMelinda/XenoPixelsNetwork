package net.bullettrain.xenopixelsmod.client.render;

import net.minecraft.core.Direction;

/**
 * Live-tunable overrides for {@link WingPanelBlockEntityRenderer}'s deflection rotation, set via
 * {@code /xenowing} ({@link net.bullettrain.xenopixelsmod.client.command.WingPanelDebugCommands}).
 *
 * <p>Exists because every prior fix to this renderer needed a recompile, a rebuild, and a full
 * client relaunch to see — an extremely slow loop for something that has to be judged by eye.
 * This lets the deflection axis and sign be tried live, in-game, with no rebuild in between.
 * Client-only, in-memory, not persisted — deliberately a scratch pad, not a config: once a
 * combination is confirmed correct it belongs back in {@link WingPanelBlockEntityRenderer} as the
 * real default, not left depending on this class.
 */
public final class WingPanelDebugRotation {

    /** Which axis the flap's live deflection rotates around. Defaults to Z, this session's
     * current best guess — {@code /xenowing deflectaxis <x|y|z>} to try another. */
    public static volatile Direction.Axis deflectAxis = Direction.Axis.Z;

    /** Flips the sign of the deflection angle on top of {@link #deflectAxis} — independent of
     * {@code INVERT}/role sign, purely for finding the right raw rotation direction.
     * {@code /xenowing deflectsign <+|->}. */
    public static volatile boolean deflectNegated = false;

    /**
     * Extra rotation stacked on top of the panel's normal static (axis-alignment) mounting, in
     * whole degrees — a live version of the "twist it another 90/180" edits this session kept
     * needing a recompile for. Applied about world X then world Y, in that vertex order, after
     * {@code alignToAxis}'s own rotation. {@code /xenowing statictwist <x|y> <+90|-90|reset>}.
     */
    public static volatile int staticTwistXDegrees = 0;
    public static volatile int staticTwistYDegrees = 0;

    private WingPanelDebugRotation() {
    }
}
