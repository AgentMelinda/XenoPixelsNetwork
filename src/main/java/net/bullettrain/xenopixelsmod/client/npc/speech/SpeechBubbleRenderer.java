package net.bullettrain.xenopixelsmod.client.npc.speech;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws a speech bubble above any entity registered with {@link SpeechBubbleQueue}, billboarded to
 * face the camera.
 *
 * <h2>Where this comes from</h2>
 * Ported from the supplied {@code java.zip} integration bundle, which graded its own provenance by
 * confidence tier. That grading is worth keeping, because it is the record of what was verified:
 *
 * <ul>
 *   <li><strong>Decompiled from the real DragonMineZ jar</strong> for 1.21.1: the billboard itself
 *       (translate above the entity, {@code mulPose(entityRenderDispatcher.cameraOrientation())},
 *       then scale down) from {@code RenderBufferUtil#nameplateBillboard}; {@link #drawTexturedQuad}
 *       as a rewrite of {@code RenderBufferUtil#drawTexturedQuad}; the outlined-text technique in
 *       {@link #drawText} (four dark passes offset by a pixel, then one in the main colour) from
 *       {@code KiSenseEvent#drawText}; and hooking {@code RenderLevelStageEvent} at
 *       {@code AFTER_LEVEL} with a {@code PoseStack} seeded from the event's model-view matrix.</li>
 *   <li><strong>Composition</strong> from primitives confirmed in that same decompiled code, not
 *       copied from one existing method: translating by {@code entityPos - cameraPos} before the
 *       billboard, which DMZ's own overlay gets for free from a different call site.</li>
 * </ul>
 *
 * <p>Adapted here to this mod's atlas rather than the bundle's own texture enum, and to a queue fed
 * by {@code XenoNpcSpeechPacket} so every player tracking the NPC sees the same line - the bundle's
 * README is explicit that a server-triggered bubble needs exactly that packet, and that it was not
 * included.
 *
 * <p>The bubble shape itself is an original: neither MyNPCs nor DragonMineZ ships a dialogue
 * bubble texture, so it is generated from the same border and fill code as every other atlas shape
 * rather than reproduced from art that exists.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class SpeechBubbleRenderer {

    /** Profile height is a direct offset from the top of the NPC. */
    private static final float HEIGHT_ABOVE_ENTITY = 0.0f;
    private static final float SIDE_OFFSET = 0.32f;
    private static final float FORWARD_OFFSET = 0.16f;

    /** Shrinks GUI-pixel sized art to something sane in world space. */
    private static final float BUBBLE_SCALE = 0.012f;

    /**
     * How much of the sprite's height is the tail, which text must stay clear of.
     *
     * <p>Defined by {@link SpeechBubbleLayout}, which needs the same number to size a bubble to its
     * text. Two copies could disagree, and the text would then be centred in a body of a different
     * height than the one it was measured against.
     */
    private static final float TAIL_HEIGHT_FRACTION = SpeechBubbleLayout.TAIL_HEIGHT_FRACTION;

    /** Past this, a bubble is invisible and not worth a draw call. */
    private static final float MIN_VISIBLE_ALPHA = 0.01f;

    private SpeechBubbleRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            // Leaving a world must not strand bubbles that would reappear on the next join keyed to
            // entity ids that now mean something else.
            SpeechBubbleQueue.clearAll();
            return;
        }
        SpeechBubbleQueue.tick(mc.level.getGameTime());
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SpeechBubbleQueue.isEmpty()) {
            return;
        }

        Map<Integer, SpeechBubbleQueue.Bubble> active = SpeechBubbleQueue.activeSnapshot();
        long gameTime = mc.level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        mc.getMainRenderTarget().bindWrite(false);

        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set((Matrix4fc) event.getModelViewMatrix());
        poseStack.last().normal().set(new Matrix3f((Matrix4fc) event.getModelViewMatrix()));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        try {
            // Far to near, so the nearest bubble paints last and therefore on top. The map this
            // replaced is a HashMap - unspecified iteration order, free to change between frames -
            // and this pass runs with the depth test off, so which of two overlapping bubbles was
            // in front used to be arbitrary per frame and the pair flickered.
            List<Integer> ordered = SpeechBubbleOrder.farToNear(
                    new ArrayList<>(active.keySet()),
                    id -> {
                        Entity candidate = mc.level.getEntity(id);
                        return candidate == null
                                ? Double.MAX_VALUE
                                : candidate.position().distanceToSqr(cameraPos);
                    });
            for (Integer entityId : ordered) {
                Entity entity = mc.level.getEntity(entityId);
                if (entity == null || !entity.isAlive()) {
                    continue;
                }
                SpeechBubbleQueue.Bubble bubble = active.get(entityId);
                if (bubble == null) {
                    continue;
                }
                float alpha = bubble.alpha(gameTime);
                if (alpha <= MIN_VISIBLE_ALPHA) {
                    continue;
                }
                if (inEntityPass(entity)) {
                    // Drawn by XenoNpcRenderer instead; see renderInEntityPass.
                    continue;
                }
                drawOne(mc, entity, bubble, alpha, poseStack, cameraPos, partialTick, gameTime, buffers);
            }
        } finally {
            // Restored in a finally so one bad entity cannot leave the whole world rendering with
            // depth testing off.
            buffers.endBatch();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    /**
     * The palette this entity's bubble is drawn in.
     *
     * <p>Per NPC, from its profile, so two NPCs standing together can be told apart while they
     * talk. Anything without a profile - a player, a vanilla mob - falls back to the UI's own
     * theme, which is what every bubble used before this was configurable.
     */
    /** This NPC's own bubble height, or the long-standing default when it has none. */
    /** Whether {@link #renderInEntityPass} owns this entity's bubble instead of the level pass. */
    static boolean inEntityPass(Entity entity) {
        return net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcBubblesInEntityPass
                && entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
    }

    /**
     * Draws a native NPC's speech bubble from its own renderer, the way DragonMineZ's ki-sense BP
     * meter draws above a player: in the entity render pass, from the entity's render pose, with
     * the depth test off. {@code entityPose} is the pose the renderer was handed, whose origin is
     * the entity's interpolated position.
     */
    public static void renderInEntityPass(Entity entity, PoseStack entityPose, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !inEntityPass(entity) || SpeechBubbleQueue.isEmpty()) {
            return;
        }
        SpeechBubbleQueue.Bubble bubble = SpeechBubbleQueue.activeSnapshot().get(entity.getId());
        if (bubble == null) {
            return;
        }
        long gameTime = mc.level.getGameTime();
        float alpha = bubble.alpha(gameTime);
        if (alpha <= MIN_VISIBLE_ALPHA) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BillboardDraw.beginOnTop();
        try {
            drawOne(mc, entity, bubble, alpha, entityPose, entity.getPosition(partialTick), partialTick,
                    gameTime, buffers);
        } finally {
            buffers.endBatch();
            BillboardDraw.endOnTop();
            RenderSystem.disableBlend();
        }
    }

    /**
     * One bubble. {@code origin} is the world point {@code poseStack} currently sits at: the camera
     * for the level pass, the entity for the entity pass.
     */
    private static void drawOne(Minecraft mc, Entity entity, SpeechBubbleQueue.Bubble bubble, float alpha,
                                PoseStack poseStack, Vec3 origin, float partialTick, long gameTime,
                                MultiBufferSource.BufferSource buffers) {
        Vec3 entityPos = entity.getPosition(partialTick);
        Vec3 forward = entity.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
        if (forward.lengthSqr() < 1.0e-6) forward = new Vec3(0.0, 0.0, 1.0);
        Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
        int side = SpeechBubbleMotion.side(entity.getUUID().hashCode());
        Vec3 bubblePos = entityPos.add(forward.scale(FORWARD_OFFSET))
                .add(right.scale(side * SIDE_OFFSET));

        poseStack.pushPose();
        poseStack.translate(bubblePos.x - origin.x,
                bubblePos.y + bubble.verticalOffset(gameTime + partialTick) - origin.y,
                bubblePos.z - origin.z);
        poseStack.translate(0.0, heightFor(entity), 0.0);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        // Negative Y because GUI art is top-down and world space is not.
        poseStack.scale(BUBBLE_SCALE, -BUBBLE_SCALE, BUBBLE_SCALE);

        drawBubble(poseStack, bubble, alpha, buffers, paletteOf(entity, bubble),
                shapeOf(entity, bubble));

        poseStack.popPose();
    }

    private static float heightFor(Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) {
            return HEIGHT_ABOVE_ENTITY;
        }
        float stored = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .bubbleProfile(living).bubbleHeight;
        return SpeechBubbleMotion.anchorHeight(living.getBbHeight(), stored, HEIGHT_ABOVE_ENTITY);
    }

    /** The line's own palette when a script gave one, otherwise the NPC's. */
    private static XenoAtlasSprites.Theme paletteOf(Entity entity, SpeechBubbleQueue.Bubble bubble) {
        if (bubble != null && bubble.palette() != null && !bubble.palette().isBlank()) {
            return XenoAtlasSprites.themeForPalette(bubble.palette());
        }
        return paletteOf(entity);
    }

    /** The line's own outline when a script gave one, otherwise the NPC's default shape. */
    private static net.bullettrain.xenopixelsmod.npc.lines.BubbleShape shapeOf(Entity entity, SpeechBubbleQueue.Bubble bubble) {
        if (bubble != null && bubble.shape() != null && bubble.shape() != net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.INHERIT) {
            return bubble.shape();
        }
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            return net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.byName(net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                    .bubbleProfile(living).bubbleShape, net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.ROUNDED);
        }
        return net.bullettrain.xenopixelsmod.npc.lines.BubbleShape.ROUNDED;
    }

    private static XenoAtlasSprites.Theme paletteOf(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living
                && (net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.hasProfile(living)
                || net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                        .get(living.getUUID()) != null)) {
            String palette = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                    .bubbleProfile(living).bubblePalette;
            return XenoAtlasSprites.themeForPalette(palette);
        }
        return XenoAtlasSprites.theme();
    }

    private static void drawBubble(PoseStack poseStack, SpeechBubbleQueue.Bubble bubble, float alpha,
                                   MultiBufferSource.BufferSource buffers,
                                   XenoAtlasSprites.Theme palette, net.bullettrain.xenopixelsmod.npc.lines.BubbleShape shape) {
        Minecraft mc = Minecraft.getInstance();
        List<FormattedCharSequence> lines = SpeechBubbleLayout.wrap(mc.font, bubble.text());
        XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get(
                SpeechBubbleLayout.spriteFor(mc.font, lines, shape), palette);

        float width = sprite.width();
        float height = sprite.height();

        float halfWidth = width / 2.0f;
        // The tail hangs below, so the body sits above the anchor point.
        float top = -height;
        float bottom = 0.0f;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, sprite.rl());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

        BillboardDraw.texturedQuad(poseStack.last().pose(), -halfWidth, top, halfWidth, bottom);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Centred in the body only, so text never sits over the tail. The whole block is centred
        // rather than each line independently: with one line this is where it always was, and with
        // several it grows evenly about the middle instead of hanging off the bottom.
        float bodyHeight = height * (1.0f - TAIL_HEIGHT_FRACTION);
        float blockHeight = SpeechBubbleLayout.blockHeight(lines.size());
        float y = top + (bodyHeight - blockHeight) / 2.0f;
        for (FormattedCharSequence line : lines) {
            BillboardDraw.outlinedLine(poseStack.last().pose(), line, mc.font.width(line),
                    0.0f, y, alpha, buffers);
            y += SpeechBubbleLayout.LINE_HEIGHT;
        }
    }

    /** Rewrite of DragonMineZ's {@code RenderBufferUtil#drawTexturedQuad}. */
}
