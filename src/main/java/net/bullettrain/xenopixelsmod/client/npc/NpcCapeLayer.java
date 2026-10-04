package net.bullettrain.xenopixelsmod.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/**
 * Display &gt; Cape: the player cape part of the model, textured from the NPC's profile.
 *
 * <p>Vanilla's {@code CapeLayer} only draws for {@code AbstractClientPlayer} and animates from the
 * player's cloak physics fields, which an NPC does not have. This draws the same cloak part with a
 * simpler swing taken from the walk animation, so the cape lifts as the NPC moves.
 */
public final class NpcCapeLayer extends RenderLayer<XenoNpcEntity, PlayerModel<XenoNpcEntity>> {

    public NpcCapeLayer(RenderLayerParent<XenoNpcEntity, PlayerModel<XenoNpcEntity>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, XenoNpcEntity npc,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (npc.isInvisible() || npc.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            return;
        }
        NpcCombatProfile profile = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .renderProfile(npc);
        ResourceLocation texture = XenoNpcRenderer.parse(profile.displayCape);
        if (texture == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 0.125F);
        float speed = Mth.clamp(limbSwingAmount, 0.0F, 1.0F);
        float lift = 6.0F + speed * 60.0F + Mth.sin(limbSwing * 0.6662F) * 4.0F * speed;
        if (npc.isCrouching()) {
            lift += 25.0F;
        }
        pose.mulPose(Axis.XP.rotationDegrees(lift));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        getParentModel().renderCloak(pose, buffers.getBuffer(RenderType.entitySolid(texture)), light,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
