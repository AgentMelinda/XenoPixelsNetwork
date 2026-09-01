package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import com.dragonminez.client.render.layer.DMZSkinLayer;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Separates human/Saiyan eyebrow textures for synthetic NPC players only. */
@Mixin(value = DMZSkinLayer.class, remap = false)
public abstract class DmzNpcEyebrowMixin {
    private static final String COLORED_LAYER =
            "Lcom/dragonminez/client/render/layer/DMZSkinLayer;renderColoredLayer(" +
            "Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;" +
            "Lcom/mojang/blaze3d/vertex/PoseStack;" +
            "Lnet/minecraft/client/player/AbstractClientPlayer;" +
            "Lnet/minecraft/client/renderer/MultiBufferSource;" +
            "Ljava/lang/String;[FFIIF)V";

    @ModifyArg(method = "renderHumanFace", at = @At(value = "INVOKE", target = COLORED_LAYER, ordinal = 3),
            index = 4, require = 0)
    private String xenopixels$baseEyebrows(String path) {
        return replaceIndex(path, "humansaiyan_eye_", "_3.png");
    }

    @ModifyArg(method = "renderHumanFace", at = @At(value = "INVOKE", target = COLORED_LAYER, ordinal = 4),
            index = 4, require = 0)
    private String xenopixels$ssj3Eyebrows(String path) {
        return replaceIndex(path, "ssj3eyebrows_eye_", ".png");
    }

    private static String replaceIndex(String path, String marker, String suffix) {
        int eyebrow = NpcFullDmzRenderer.eyebrowOverride();
        if (eyebrow < 0 || path == null || !path.endsWith(suffix)) return path;
        int markerAt = path.lastIndexOf(marker);
        if (markerAt < 0) return path;
        return path.substring(0, markerAt + marker.length()) + eyebrow + suffix;
    }
}
