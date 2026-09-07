package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.client.render.util.ModRenderTypes;
import net.bullettrain.xenopixelsmod.client.compat.dmz.KiWeaponRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes third-person DragonMineZ ki weapons render under Iris.
 *
 * <p>{@code DMZWeaponsLayer.renderForBone} draws the weapon through
 * {@code ModRenderTypes.energy2}, whose {@code RENDERTYPE_EYES_SHADER} maps to
 * {@code gbuffers_spidereye} under a shader pack — a program packs commonly override in a way
 * that does not survive being handed GeckoLib weapon geometry. See {@link KiWeaponRenderTypes}
 * for why this is the one DMZ emissive effect without a shader-pack variant of its own.
 */
@Mixin(targets = "com.dragonminez.client.render.layer.DMZWeaponsLayer", remap = false)
public abstract class DmzWeaponsLayerIrisMixin {
    @Redirect(
            method = "lambda$renderForBone$0",
            at = @At(value = "INVOKE", target =
                    "Lcom/dragonminez/client/render/util/ModRenderTypes;energy2("
                    + "Lnet/minecraft/resources/ResourceLocation;)"
                    + "Lnet/minecraft/client/renderer/RenderType;"),
            require = 0)
    private RenderType xenopixels$shaderSafeWeaponType(ResourceLocation texture) {
        return KiWeaponRenderTypes.select(ModRenderTypes.energy2(texture), texture);
    }
}
