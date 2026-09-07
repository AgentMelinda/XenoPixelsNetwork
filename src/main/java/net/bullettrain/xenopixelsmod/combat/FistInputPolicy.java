package net.bullettrain.xenopixelsmod.combat;

/** Shared ownership boundary: native weapons never also produce Xeno fists. */
public final class FistInputPolicy {
    private FistInputPolicy() {}

    public static boolean emptyHands(boolean mainEmpty, boolean offEmpty, boolean kiWeapon) {
        return mainEmpty && offEmpty && !kiWeapon;
    }

    /**
     * Whether Xeno fists and the charged fist run at all. Deliberately blind to what the crosshair
     * is pointing at: a punch still swings while facing a wall, exactly like vanilla left click,
     * and a charge in progress must not die because the player glanced at the ground.
     */
    public static boolean fistsActive(boolean eligibleHands, boolean combat, boolean combo,
                                      boolean charge, boolean usableContext) {
        return eligibleHands && combat && (combo || charge) && usableContext;
    }

    /**
     * Whether Xeno owns the native attack <em>exclusively</em>, so the vanilla and DragonMineZ
     * attack paths must be suppressed. A block under the crosshair is never suppressed: the native
     * path only starts and continues a dig there, which damages nothing, so mining and the punch
     * can both run off one click.
     */
    public static boolean ownsAttack(boolean eligibleHands, boolean blockTarget, boolean combat,
                                     boolean combo, boolean charge, boolean usableContext) {
        return fistsActive(eligibleHands, combat, combo, charge, usableContext) && !blockTarget;
    }
}
