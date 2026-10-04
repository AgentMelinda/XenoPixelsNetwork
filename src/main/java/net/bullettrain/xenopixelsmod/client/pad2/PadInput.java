package net.bullettrain.xenopixelsmod.client.pad2;

/**
 * The physical gamepad inputs the BT3 layout uses, named symbolically.
 *
 * <p>Controlify names these as {@code ResourceLocation} constants on {@code GamepadInputs}. The
 * layout table refers to <em>this</em> enum instead, which is what keeps {@link PadLayout} free of
 * Controlify types — so the layout can be read, diffed and tested without Controlify installed,
 * and a mistake in it shows up as a compile error rather than as a dead button in play.
 *
 * <p>{@link #NONE} is not "no input assigned by accident": it is how a row says <em>this action
 * ships unbound and lives in the radial menu</em>. Roughly two thirds of the rows are that, because
 * a pad has about sixteen inputs and this mod has about forty actions.
 */
public enum PadInput {

    /** X on an Xbox pad. Melee, and the moves layered on it. */
    WEST,
    /** A. Dash. */
    SOUTH,
    /** B. Guard. */
    EAST,
    /** Y. Ki blast. */
    NORTH,

    /** LT. Held ki charge — the first chord modifier. */
    LEFT_TRIGGER,
    /** RT. Held descend — the third chord modifier. */
    RIGHT_TRIGGER,
    /** LB. Held lock-on — the second chord modifier. */
    LEFT_SHOULDER,
    /** RB. Flight toggle, and ascend while flying. */
    RIGHT_SHOULDER,

    /** L3. Flight mode. */
    LEFT_STICK_BUTTON,
    /** R3. Transform. */
    RIGHT_STICK_BUTTON,
    /** Back / View. The stats menu. */
    BACK,

    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,

    /** Left stick directions, emulating the movement keys DragonMineZ flight reads. */
    LEFT_STICK_UP,
    LEFT_STICK_DOWN,
    LEFT_STICK_LEFT,
    LEFT_STICK_RIGHT,

    /** Ships unbound. The action is reachable from the radial menu, or from a bind the player sets. */
    NONE
}
