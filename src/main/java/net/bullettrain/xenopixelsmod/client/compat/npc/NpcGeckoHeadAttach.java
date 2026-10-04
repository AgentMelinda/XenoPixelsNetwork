package net.bullettrain.xenopixelsmod.client.compat.npc;

/**
 * Resolves which gecko bone DMZ hair / halo attach to on a custom-model NPC.
 *
 * <p>GeckoLib 4.9.2 {@code GeoRenderer.renderRecursively} already calls
 * {@code RenderUtil.prepMatrixForBone} before {@code renderForBone}, so layers
 * must not apply that method again. Hair sits on the live pivot in that
 * already-rotated bone space, matching DragonMineZ {@code DMZHairLayer}.
 */
public final class NpcGeckoHeadAttach {
    public static final String DEFAULT_HEAD_BONE = "head";
    /** Verified GeckoLib 4.9.2 method the renderer applies before layers. */
    public static final String RENDERER_BONE_MATRIX = "prepMatrixForBone";
    /** Verified GeckoLib 4.9.2 method DMZ hair uses after that prep. */
    public static final String LAYER_PIVOT = "translateToPivotPoint";

    private NpcGeckoHeadAttach() {}

    public static String resolveBone(String surrogateHeadBone, String appearanceHeadBone) {
        if (notBlank(surrogateHeadBone)) {
            return surrogateHeadBone.trim();
        }
        if (notBlank(appearanceHeadBone)) {
            return appearanceHeadBone.trim();
        }
        return DEFAULT_HEAD_BONE;
    }

    public static boolean matches(String resolved, String boneName) {
        return resolved != null && boneName != null && resolved.equalsIgnoreCase(boneName);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
