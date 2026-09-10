package net.bullettrain.xenopixelsmod.combat;

/**
 * When a Zanzoken image dies, and when its whole ring goes with it.
 *
 * <p>The ring used to end the instant <b>anything</b> removed <b>any</b> image, and each image had
 * one point of health. In a real fight that meant the first mob to swing — at an image, which is
 * exactly what {@link ZanzokenConfusion} now points them at — destroyed one body and took the other
 * five with it. A disguise configured to stand for ten seconds was over in a tick, which is what
 * "the images vanish early" was.
 *
 * <p>The rule is that <b>only a player can see through it</b>:
 *
 * <ul>
 *   <li>A player striking an image has committed and guessed. That is the read the technique is
 *       built around, so it resolves the whole trick and the ring goes.
 *   <li>Anything else — a mob's swing, splash damage, the environment — cannot destroy an image at
 *       all. AI is supposed to be fooled by these; letting it delete them one hit at a time is the
 *       opposite of what they are for.
 * </ul>
 *
 * <p>Kept free of Minecraft types so the rule is unit tested rather than inferred from a fight.
 */
public final class ZanzokenRing {

    private ZanzokenRing() {
    }

    /**
     * Whether an attacker is allowed to destroy a standing Zanzoken image.
     *
     * <p>Only a player. A mob may swing at an image all it likes — being drawn onto one and kept
     * there is the whole point — but it never removes it.
     */
    public static boolean canDestroyImage(boolean attackerIsPlayer) {
        return attackerIsPlayer;
    }

    /**
     * Whether losing one image ends the whole ring.
     *
     * @param struckByPlayer   a player destroyed this image, rather than it expiring or being
     *                         cleaned up
     * @param remainingImages  how many images still stand after this one is removed
     */
    public static boolean disperseWholeRing(boolean struckByPlayer, int remainingImages) {
        return struckByPlayer || remainingImages <= 0;
    }
}
