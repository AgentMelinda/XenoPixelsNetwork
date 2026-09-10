package net.bullettrain.xenopixelsmod.mixin.compat.shared;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CustomNPCs' Parts-creation GUI ({@code GuiCreationNewParts$GuiMpmPart.renderModel}) reads
 * {@code EntityCustomNpc.textureLocation} straight into {@code RenderType.entityCutoutNoCull}
 * with no null check. When that field is null (e.g. an NPC whose texture never resolved),
 * {@code RenderStateShard$TextureStateShard}'s constructor does {@code Optional.of(texture)},
 * which throws and crashes the whole screen. CustomNPCs' own {@code MpmPartData} already falls
 * back to {@link MissingTextureAtlasSprite#getLocation()} for the same "texture is null" case
 * elsewhere in the same mod; this applies that same fallback at the one call site that skips it.
 *
 * <p>Lives in {@code compat.shared} rather than {@code compat.customnpcs} because it names no NPC
 * type at all -- it targets DragonMineZ, and only needs *an* NPC mod present. That package's gate is
 * {@code customnpcs || mynpcs}, so one copy serves CustomNPCs and its My NPCs fork alike.
 */
@Mixin(RenderType.class)
public abstract class RenderTypeNullTextureGuardMixin {
    @Inject(method = "entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private static void xenopixels$guardNullTexture(ResourceLocation texture,
                                                     CallbackInfoReturnable<RenderType> cir) {
        if (texture == null) {
            cir.setReturnValue(RenderType.entityCutoutNoCull(MissingTextureAtlasSprite.getLocation()));
        }
    }
}
