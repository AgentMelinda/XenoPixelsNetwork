package net.bullettrain.xenopixelsmod.client.anim.studio;

/**
 * Converts between the studio's numbers and the values a live {@code GeoBone} holds.
 *
 * <p>The studio stores what a GeckoLib 1.8 animation file stores: rotation in degrees, Bedrock
 * sign convention, relative to the bone's rest pose. {@code XenoAnimClip.toGeckoJson} writes those
 * numbers out verbatim and {@code XenoClipSources} reads shipped clips back in verbatim, so the
 * file and the studio agree by construction. The one consumer that does not read the file is the
 * live preview, and it has to reproduce what GeckoLib does when it plays the file:
 *
 * <ul>
 *   <li>{@code BakedAnimationsAdapter.buildKeyframeStack} loads rotation X and Y as
 *       {@code toRadians(-value)} and Z as {@code toRadians(value)}.
 *   <li>{@code AnimationProcessor.tickAnimation} then writes
 *       {@code bone.setRot*(value + bone.getInitialSnapshot().getRot*())}.
 *   <li>Position and scale are written absolute, with no negation and no snapshot offset;
 *       {@code RenderUtil.translateMatrixToBone} negates X at draw time for both paths alike.
 * </ul>
 *
 * <p>Verified with {@code javap} against {@code geckolib-neoforge-1.21.1-4.9.2.jar} on 2026-09-12.
 * Before this helper the preview wrote {@code toRadians(value)} straight into the bone, so PV POSE
 * showed X and Y rotations mirrored against PV BAKED, and a shipped clip opened through SRC
 * looked mirrored against the same clip playing in game.
 *
 * <p>Pure arithmetic, no Minecraft imports, so the round trip is unit-tested.
 */
public final class StudioBoneSpace {
    private StudioBoneSpace() {}

    /** Bedrock negates X and Y rotation on load; Z is left alone. */
    private static float sign(int axis) {
        return axis == 2 ? 1f : -1f;
    }

    /**
     * The radians a {@code GeoBone} should hold for a studio rotation.
     *
     * @param axis        0 = X, 1 = Y, 2 = Z
     * @param studioDeg   studio / animation-file degrees
     * @param initRad     {@code bone.getInitialSnapshot().getRot*()}
     */
    public static float toBoneRadians(int axis, float studioDeg, float initRad) {
        return initRad + (float) Math.toRadians(sign(axis) * studioDeg);
    }

    /**
     * The studio degrees that reproduce a live bone rotation; inverse of
     * {@link #toBoneRadians(int, float, float)}.
     */
    public static float toStudioDegrees(int axis, float boneRad, float initRad) {
        return sign(axis) * (float) Math.toDegrees(boneRad - initRad);
    }
}
