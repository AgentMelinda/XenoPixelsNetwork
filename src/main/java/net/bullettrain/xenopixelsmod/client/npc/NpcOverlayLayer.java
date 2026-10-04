package net.bullettrain.xenopixelsmod.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Display &gt; Overlay: a second texture drawn over the skin with the same model and pose, as
 * CustomNPCs' overlay does - eyes, markings, a glow. Translucent, so its transparent pixels show
 * the skin beneath; "Overlay glows" draws it full-bright, like a spider's eyes.
 */
public final class NpcOverlayLayer extends RenderLayer<XenoNpcEntity, PlayerModel<XenoNpcEntity>> {

    public NpcOverlayLayer(RenderLayerParent<XenoNpcEntity, PlayerModel<XenoNpcEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, XenoNpcEntity npc,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (npc.isInvisible()) {
            return;
        }
        NpcCombatProfile profile = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .renderProfile(npc);
        ResourceLocation texture = XenoNpcRenderer.parse(profile.displayOverlay);
        if (texture == null) {
            return;
        }
        RenderType type = profile.displayOverlayGlow
                ? RenderType.eyes(texture) : RenderType.entityTranslucent(texture);
        int packedLight = profile.displayOverlayGlow ? LightTexture.FULL_BRIGHT : light;
        getParentModel().renderToBuffer(pose, buffers.getBuffer(type), packedLight,
                LivingEntityRenderer.getOverlayCoords(npc, 0.0F));
    }
}
