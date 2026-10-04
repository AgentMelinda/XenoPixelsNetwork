package net.bullettrain.xenopixelsmod.client.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAnimationClient;
import net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimationBinding;
import software.bernie.geckolib.cache.GeckoLibCache;

/**
 * The GeckoLib arm of {@link XenoNpcRenderer}.
 *
 * <p>This is never registered on its own: {@link XenoNpcRenderer} owns an instance and delegates to
 * it when the profile selects the GECKOLIB model kind. Keeping it a real {@code GeoEntityRenderer}
 * means GeckoLib's own render layers, animation processing and bone handling all work unchanged.
 *
 * <p>Tint is applied here because {@code getRenderColor} is a supported GeckoLib hook, whereas
 * vanilla's {@code LivingEntityRenderer} hard-codes its model colour.
 */
public final class XenoNpcGeoRenderer extends GeoEntityRenderer<XenoNpcEntity> {

    public XenoNpcGeoRenderer(EntityRendererProvider.Context context) {
        super(context, new XenoNpcGeoModel());
    }

    @Override
    public void render(XenoNpcEntity entity, float entityYaw, float partialTick,
                       com.mojang.blaze3d.vertex.PoseStack pose,
                       net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        NpcAnimationClient.Pending pending = NpcAnimationClient.peek(entity.getUUID());
        if (pending != null) {
            // A clip can arrive before GeckoLib finishes its resource reload. Keep it queued
            // until the fallback file is baked, then hand it to the visible Gecko controller.
            boolean ready = pending.stop()
                    || GeckoLibCache.getBakedAnimations().containsKey(
                            Bt3AnimationBinding.DMZ_ANIMATION_FILE);
            if (ready && NpcAnimationClient.consume(entity.getUUID(), pending)) {
                entity.acceptScriptAnimation(pending.animation(), pending.hold(), pending.stop());
                NpcAnimationClient.traceGeoDelivery(pending.animation());
            }
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public Color getRenderColor(XenoNpcEntity entity, float partialTick, int light) {
        int tint = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .renderProfile(entity).modelTint & 0xFFFFFF;
        if (tint == 0xFFFFFF) {
            return super.getRenderColor(entity, partialTick, light);
        }
        return Color.ofRGBA((tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, 0xFF);
    }

    @Override
    protected float getDeathMaxRotation(XenoNpcEntity entity) {
        // A rigged model tipping over on death usually clips through its own geometry, and a pack
        // that wants a death animation should supply one rather than rely on the vanilla flop.
        return 0.0f;
    }

    /** XenoNpcRenderer draws the nameplate (name, title, raised) for every model kind. */
    @Override
    protected void renderNameTag(XenoNpcEntity entity, net.minecraft.network.chat.Component name,
                                 com.mojang.blaze3d.vertex.PoseStack pose,
                                 net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partialTick) {
    }
}
