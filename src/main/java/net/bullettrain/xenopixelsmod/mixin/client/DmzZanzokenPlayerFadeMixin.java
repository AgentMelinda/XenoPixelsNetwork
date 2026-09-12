package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.render.DMZPlayerRenderer;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.client.combat.CombatBodyFade;
import net.bullettrain.xenopixelsmod.client.combat.HakaiFade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fades a fighter's real body while their Zanzoken images stand around them.
 *
 * <p><b>Target:</b> {@code DMZPlayerRenderer#render(AbstractClientPlayer, float, float, PoseStack,
 * MultiBufferSource, int)}. <b>Written for:</b> Minecraft 1.21.1 / NeoForge 21.1.238,
 * DragonMineZ 2.1.3, GeckoLib 4.9.2, MixinExtras 0.5.3. <b>Side:</b> client.
 *
 * <p><b>The bug.</b> Zanzoken surrounds the attacker with copies of the dodger and puts the dodger
 * in one of the slots. Every copy renders through {@code AfterimageFade} and dims as it ages; the
 * dodger's own body did not, because nothing on the player's render path knew the ring was up. One
 * solid body among six dim ones is not a disguise — it is a label.
 *
 * <p><b>The fix.</b> Wrap the buffer source in the same {@code AlphaMultiBufferSource} the images
 * use, at the same alpha, for the frames the ring stands. Every layer the renderer draws through
 * that source — model, skin, hair, aura — dims with it, which is why this wraps the source rather
 * than tinting anything itself.
 *
 * <p><b>Why a mixin.</b> DragonMineZ replaces the player renderer with this GeckoLib renderer, so
 * NeoForge's {@code RenderPlayerEvent} — which vanilla's {@code PlayerRenderer} fires — is not on
 * this path, and no event exposes the buffer source for replacement in any case.
 *
 * <p><b>What it must not fade.</b> Two other things reach this same renderer:
 * <ul>
 *   <li>the copies themselves, which {@code NpcFullDmzRenderer#renderPlayerCopy} draws by calling
 *       this renderer with a <i>proxy</i> player and a buffer source that is already faded. Fading
 *       again would square the alpha and leave the images fainter than intended. Proxies are not in
 *       the level's entity table, so the identity check below excludes them without needing to name
 *       the proxy class.
 *   <li>the HUD portrait, which is the fighter's own face in the corner and has no business
 *       dimming because their body in the world is.
 * </ul>
 *
 * <p><b>Guideline notes</b> (§18): {@code @WrapMethod} because the whole call needs one substituted
 * argument, which no narrower injector on this method can do. The body is two boolean reads, a map
 * lookup and, in the common case, no allocation at all — {@code ZanzokenFade.wrap} returns the
 * original source untouched when there is nothing to fade, so a normal frame costs a lookup.
 * {@code require = 0} because the target belongs to another mod: if DragonMineZ changes this
 * renderer, the fade is lost rather than the game failing to start. DragonMineZ is not modified.
 */
@Mixin(value = DMZPlayerRenderer.class, remap = false)
public abstract class DmzZanzokenPlayerFadeMixin {

    @WrapMethod(
            // Spelled out because this renderer is generic: the erased signature is what is on the
            // class, and naming it leaves no room for a bridge method to be matched instead.
            method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            remap = false,
            require = 0
    )
    private void xeno$fadeBehindAfterimages(AbstractClientPlayer player, float entityYaw,
                                            float partialTick, PoseStack pose,
                                            MultiBufferSource buffers, int packedLight,
                                            Operation<Void> original) {
        MultiBufferSource faded = buffers;
        try {
            faded = xeno$faded(player, partialTick, buffers);
        } catch (RuntimeException ignored) {
            faded = buffers;
        }
        boolean fading = faded != buffers;
        if (fading) CombatBodyFade.begin(player);
        try {
            original.call(player, entityYaw, partialTick, pose, faded, packedLight);
        } finally {
            if (fading) CombatBodyFade.end();
        }
    }

    @Inject(
            method = "getRenderType",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void xeno$translucentWhenFading(AbstractClientPlayer animatable, ResourceLocation texture,
                                            MultiBufferSource bufferSource, float partialTick,
                                            CallbackInfoReturnable<RenderType> cir) {
        if (texture == null || CombatBodyFade.outlinePass(bufferSource)) return;
        if (CombatBodyFade.isWrapped(bufferSource) || CombatBodyFade.fading(animatable)) {
            cir.setReturnValue(RenderType.entityTranslucent(texture, true));
        }
    }

    private static MultiBufferSource xeno$faded(AbstractClientPlayer player, float partialTick,
                                                MultiBufferSource buffers) {
        if (player == null || EntityPreviewRenderContext.isHudPortrait()) {
            return buffers;
        }
        Minecraft mc = Minecraft.getInstance();
        // A render proxy carries a copy's identity but never lives in the level, so this is what
        // separates "the player standing in the ring" from "one of the images being drawn".
        if (mc.level == null || mc.level.getEntity(player.getId()) != player) {
            return buffers;
        }
        if (HakaiFade.dissolving(player)) {
            return CombatBodyFade.wrapHakai(buffers, player, partialTick);
        }
        return CombatBodyFade.wrap(buffers, CombatBodyFade.alpha(player, partialTick));
    }
}
