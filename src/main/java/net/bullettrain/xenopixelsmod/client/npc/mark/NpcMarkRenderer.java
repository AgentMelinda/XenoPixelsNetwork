package net.bullettrain.xenopixelsmod.client.npc.mark;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Draws the Advanced &gt; Marks icon above an NPC's head.
 *
 * <p>The same billboard the speech bubble uses - translate above the entity, multiply by the camera
 * orientation, scale GUI-pixel art down into world space - so a mark and a bubble sit in the same
 * place and face the same way. It is a separate renderer rather than another branch inside the
 * bubble one because a mark is persistent state read off the profile, while a bubble is a queued,
 * expiring message; sharing a loop would mean one of the two carrying the other's lifetime rules.
 *
 * <p>The icons are generated, not reproduced: see {@code render_mark} in the atlas generator.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class NpcMarkRenderer {

    /** World units above the head, clear of where a speech bubble sits. */
    private static final float HEIGHT_ABOVE_ENTITY = 1.05f;

    /** Shrinks GUI-pixel art into world space. Matches the speech bubble's scale. */
    private static final float MARK_SCALE = 0.012f;

    /** Past this, a mark is a couple of pixels and not worth the draw call. */
    private static final double MAX_DISTANCE_SQ = 48.0 * 48.0;

    private NpcMarkRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set((Matrix4fc) event.getModelViewMatrix());
        poseStack.last().normal().set(new Matrix3f((Matrix4fc) event.getModelViewMatrix()));

        boolean drewAny = false;
        try {
            for (XenoNpcEntity npc : mc.level.getEntitiesOfClass(XenoNpcEntity.class,
                    mc.player == null ? null : mc.player.getBoundingBox().inflate(48.0))) {
                if (!npc.isAlive() || npc.distanceToSqr(cameraPos) > MAX_DISTANCE_SQ) {
                    continue;
                }
                NpcCombatProfile profile = NpcCombatProfile.read(npc);
                String icon = NpcCombatProfile.canonicalMarkIcon(profile.markIcon);
                if (icon.isEmpty()) {
                    continue;
                }
                String sprite = "mark_" + icon;
                if (!XenoAtlasSprites.shapes().contains(sprite)) {
                    // A mark whose art was never generated is skipped rather than drawn as a
                    // missing texture. Nothing invents a sprite here.
                    continue;
                }

                if (!drewAny) {
                    mc.getMainRenderTarget().bindWrite(false);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.disableDepthTest();
                    drewAny = true;
                }

                Vec3 pos = npc.getPosition(partialTick);
                poseStack.pushPose();
                poseStack.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
                poseStack.translate(0.0, npc.getBbHeight() + HEIGHT_ABOVE_ENTITY, 0.0);
                poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                // Negative Y because GUI art is top-down and world space is not.
                poseStack.scale(MARK_SCALE, -MARK_SCALE, MARK_SCALE);

                draw(poseStack, sprite, profile.markColor);

                poseStack.popPose();
            }
        } finally {
            // Restored in a finally so one bad entity cannot leave the whole world rendering with
            // depth testing off.
            if (drewAny) {
                RenderSystem.enableDepthTest();
                RenderSystem.disableBlend();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }
    }

    private static void draw(PoseStack poseStack, String sprite, int tint) {
        XenoAtlasSprites.Sprite art = XenoAtlasSprites.get(sprite);
        float half = art.width() / 2.0f;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, art.rl());
        RenderSystem.setShaderColor(
                ((tint >> 16) & 0xFF) / 255.0f,
                ((tint >> 8) & 0xFF) / 255.0f,
                (tint & 0xFF) / 255.0f,
                1.0f);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, -half, 0.0f, 0.0f).setUv(0.0f, 1.0f);
        buffer.addVertex(matrix, half, 0.0f, 0.0f).setUv(1.0f, 1.0f);
        buffer.addVertex(matrix, half, -art.height(), 0.0f).setUv(1.0f, 0.0f);
        buffer.addVertex(matrix, -half, -art.height(), 0.0f).setUv(0.0f, 0.0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
