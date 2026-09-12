package net.bullettrain.xenopixelsmod.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.client.combat.CombatBodyFade;
import net.bullettrain.xenopixelsmod.client.combat.HakaiFade;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fades a non-player, non-CustomNPC living target while Hakai is charging.
 *
 * <p>Players go through {@code DMZPlayerRenderer}; FULL-appearance profiled NPCs go through
 * {@code NpcFullDmzRenderer}. This catches everything else (mobs) so a Hakai on a
 * zombie still reads as a dissolve.
 *
 * <p>Cutout render types discard vertex alpha, so the <em>body</em> {@code getRenderType}
 * is forced to {@code entityTranslucent}. The glowing outline pass ({@code glowing == true})
 * is left alone so fade and stencil glow can run together.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingHakaiFadeMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/LivingEntity;FF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void xeno$fadeHakaiTarget(LivingEntity entity, float entityYaw, float partialTick,
                                      PoseStack pose, MultiBufferSource buffers, int packedLight,
                                      Operation<Void> original) {
        MultiBufferSource faded = buffers;
        boolean fading = entity != null && !(entity instanceof Player)
                && !(NpcCounterpartSync.isCustomNpc(entity) && NpcFullDmzRenderer.isFull(entity))
                && HakaiFade.dissolving(entity);
        if (fading) {
            CombatBodyFade.begin(entity);
            try {
                faded = CombatBodyFade.wrapHakai(buffers, entity, partialTick);
            } catch (RuntimeException ignored) {
                faded = buffers;
            }
        }
        try {
            original.call(entity, entityYaw, partialTick, pose, faded, packedLight);
        } finally {
            if (fading) CombatBodyFade.end();
        }
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void xeno$translucentWhenFading(LivingEntity entity, boolean bodyVisible,
                                            boolean translucent, boolean glowing,
                                            CallbackInfoReturnable<RenderType> cir) {
        if (!bodyVisible || glowing || !CombatBodyFade.fading(entity)) return;
        @SuppressWarnings("unchecked")
        ResourceLocation texture = ((LivingEntityRenderer<LivingEntity, ?>) (Object) this)
                .getTextureLocation(entity);
        cir.setReturnValue(RenderType.entityTranslucent(texture, true));
    }
}
