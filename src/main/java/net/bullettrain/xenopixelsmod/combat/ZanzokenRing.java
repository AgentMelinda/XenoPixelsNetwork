package net.bullettrain.xenopixelsmod.combat;

/**
 * When a Zanzoken image dies, and when its whole ring goes with it.
 *
 * <p>Kept free of Minecraft types so the rule is unit tested rather than inferred from a fight.
 */
public final class ZanzokenRing {

    private ZanzokenRing() {
    }

    /**
     * Whether an attacker is allowed to destroy a standing Zanzoken image.
     *
     * @param attackerIsPlayer only players ever pop images (mobs stay fooled)
     * @param hitable          server toggle; false means even a player swing is ignored
     */
    public static boolean canDestroyImage(boolean attackerIsPlayer, boolean hitable) {
        return hitable && attackerIsPlayer;
    }

    /**
     * Whether losing one image ends the whole ring.
     *
     * @param struckByPlayer   a player destroyed this image, rather than it expiring
     * @param remainingImages  how many images still stand after this one is removed
     * @param disperseAll      when true, a player hit takes the rest of the ring
     */
    public static boolean disperseWholeRing(boolean struckByPlayer, int remainingImages,
                                            boolean disperseAll) {
        if (remainingImages <= 0) {
            return true;
        }
        return struckByPlayer && disperseAll;
    }
}
