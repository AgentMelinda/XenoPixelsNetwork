package net.bullettrain.xenopixelsmod.client.compat.dmz;

import com.dragonminez.client.render.util.IrisCompat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Selects a shader-pack-safe standard entity pipeline for DragonMineZ KI weapons. */
public final class KiWeaponRenderTypes {
    private KiWeaponRenderTypes() {}

    /** Preserve DMZ rendering normally; avoid its eyes/beacon shaders while Iris is active. */
    public static RenderType select(RenderType original, ResourceLocation texture) {
        if (texture == null || !isShaderPackInUse()) return original;
        // Iris and shader packs are required to support the ordinary translucent entity path.
        // It uses NEW_ENTITY, matching the weapon vertices, without eyes/beacon assumptions.
        return RenderType.entityTranslucent(texture);
    }

    private static boolean isShaderPackInUse() {
        try {
            return IrisCompat.isShaderPackInUse();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
