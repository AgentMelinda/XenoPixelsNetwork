package net.bullettrain.xenopixelsmod.client.compat.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Transparent DMZ face/tattoo overlays for standard 64x64 humanoid NPC skins. */
public final class NpcHumanoidAppearanceLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    public NpcHumanoidAppearanceLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, T owner,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        NpcAppearanceClient.State state = NpcAppearanceClient.get(owner.getUUID());
        if (state == null || state.appearance().mode != NpcDmzAppearance.Mode.OVERLAY) return;
        NpcDmzAppearance a = state.appearance();
        // Tattoo 0 is a real DMZ choice, not a sentinel for "none".
        draw(pose, buffers, packedLight, path("tattoos/tattoo_" + a.tattooType + ".png"), 0xFFFFFFFF);
        String race = state.race().toLowerCase(java.util.Locale.ROOT);
        if (!race.equals("human") && !race.equals("saiyan") && !race.equals("halfsaiyan")) return;
        String base = "humansaiyan/faces/humansaiyan_";
        draw(pose, buffers, packedLight, path(base + "eye_" + a.eyesType + "_0.png"), 0xFFFFFFFF);
        draw(pose, buffers, packedLight, path(base + "eye_" + a.eyesType + "_1.png"), argb(a.eye1Color));
        draw(pose, buffers, packedLight, path(base + "eye_" + a.eyesType + "_2.png"), argb(a.eye2Color));
        draw(pose, buffers, packedLight, path(base + "eye_" + a.eyebrowsType + "_3.png"),
                argb(state.hairColor().isBlank() ? "#FFFFFF" : state.hairColor()));
        draw(pose, buffers, packedLight, path(base + "nose_" + a.noseType + ".png"), argb(a.bodyColor));
        draw(pose, buffers, packedLight, path(base + "mouth_" + a.mouthType + ".png"), argb(a.bodyColor));
    }

    private void draw(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation texture, int color) {
        if (Minecraft.getInstance().getResourceManager().getResource(texture).isEmpty()) return;
        getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),
                light, OverlayTexture.NO_OVERLAY, color);
    }

    private static ResourceLocation path(String relative) {
        return ResourceLocation.fromNamespaceAndPath("dragonminez", "textures/entity/races/" + relative);
    }

    private static int argb(String value) {
        int rgb = NpcCombatProfile.parseHexColor(value).orElse(0xFFFFFF);
        return 0xFF000000 | rgb;
    }
}
