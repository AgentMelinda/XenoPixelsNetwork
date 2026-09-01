package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import com.dragonminez.client.render.layer.DMZHairLayer;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps synthetic NPC hair morphing aligned with the authoritative transform hold. */
@Mixin(value = DMZHairLayer.class, remap = false)
public abstract class DmzNpcHairProgressMixin {
    private static final String HAIR_RENDER =
            "Lcom/dragonminez/client/render/hair/HairRenderer;render(" +
            "Lcom/mojang/blaze3d/vertex/PoseStack;" +
            "Lnet/minecraft/client/renderer/MultiBufferSource;" +
            "Lcom/dragonminez/common/hair/CustomHair;" +
            "Lcom/dragonminez/common/hair/CustomHair;" +
            "FLcom/dragonminez/common/stats/character/Character;" +
            "Lcom/dragonminez/common/stats/StatsData;" +
            "Lnet/minecraft/client/player/AbstractClientPlayer;" +
            "[F[FZZFIIFFF)V";

    @ModifyArg(method = "renderHair", at = @At(value = "INVOKE", target = HAIR_RENDER),
            index = 4, require = 0)
    private float xenopixels$useNpcTransformProgress(float original) {
        return NpcFullDmzRenderer.hairTransitionFactor(original);
    }
}
