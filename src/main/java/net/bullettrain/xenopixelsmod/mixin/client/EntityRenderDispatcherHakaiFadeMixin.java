package net.bullettrain.xenopixelsmod.mixin.client;

import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.client.combat.CombatBodyFade;
import net.bullettrain.xenopixelsmod.client.combat.HakaiFade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Wraps every living-entity render so a Hakai fade reaches GeckoLib / other-mod
 * renderers that never enter {@code LivingEntityRenderer}.
 *
 * <p>{@code CombatBodyFade.isWrapped} keeps vanilla and DMZ paths from stacking a
 * second alpha wrapper. {@code EntityPreviewRenderContext} GUI draws are left
 * unwrapped — those one-shot buffers threw {@code Not building!} when faded
 * (crash 2026-09-13). Descriptor verified with {@code javap} against
 * {@code neoforge-21.1.248-merged.jar}.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherHakaiFadeMixin {
    @WrapMethod(method = "render(Lnet/minecraft/world/entity/Entity;DDDFF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void xeno$fadeHakaiTarget(Entity entity, double x, double y, double z,
                                      float yaw, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, int packedLight,
                                      Operation<Void> original) {
        boolean fading = entity instanceof LivingEntity living
                && HakaiFade.dissolving(living)
                && !CombatBodyFade.isWrapped(buffers)
                && !EntityPreviewRenderContext.isRendering()
                && !EntityPreviewRenderContext.isHudPortrait();
        MultiBufferSource faded = buffers;
        if (fading) {
            CombatBodyFade.begin(entity);
            try {
                faded = CombatBodyFade.wrapHakai(buffers, entity, partialTick);
            } catch (RuntimeException ignored) {
                faded = buffers;
            }
        }
        try {
            original.call(entity, x, y, z, yaw, partialTick, pose, faded, packedLight);
        } finally {
            if (fading) CombatBodyFade.end();
        }
    }
}
