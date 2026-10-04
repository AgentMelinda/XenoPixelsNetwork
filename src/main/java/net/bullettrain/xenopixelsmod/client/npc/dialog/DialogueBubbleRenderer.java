package net.bullettrain.xenopixelsmod.client.npc.dialog;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.npc.speech.BillboardDraw;
import net.bullettrain.xenopixelsmod.client.npc.speech.SpeechBubbleLayout;
import net.bullettrain.xenopixelsmod.client.render.WorldToScreenCache;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a conversation as bubbles above the NPC, instead of as a full-screen menu.
 *
 * <p>The NPC's line goes in a bubble the same shape and size as its speech bubble, and each option
 * hangs beneath it as a smaller one. Both use the atlas's four palettes, chosen per NPC, so the art
 * itself changes rather than being tinted.
 *
 * <p>Built on the same billboard the speech bubble uses - translate above the entity, multiply by
 * the camera orientation, scale GUI-pixel art into world space - so a line and an option sit in the
 * same place and face the same way.
 *
 * <h2>Clicking</h2>
 * Each option's world position is projected to the screen through {@link WorldToScreenCache}, the
 * same helper the crosshair HUD uses, and the rectangles are published for
 * {@link DialogueBubbleInput} to hit-test. Projecting is why this is not simply ray-picked: with a
 * cursor on screen the player is not looking at what they are clicking.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class DialogueBubbleRenderer {

    /** Answer tails start at the head; the entire answer group rises clear of the NPC. */
    private static final float OPTION_HEIGHT_FRACTION = 1.0f;

    /** Shrinks GUI-pixel art into world space. Matches the speech bubble. */
    private static final float BUBBLE_SCALE = 0.012f;

    /** Past this the text is unreadable and the conversation should have ended anyway. */
    private static final double MAX_DISTANCE_SQ = 32.0 * 32.0;

    /**
     * Smallest a target may get, in GUI pixels.
     *
     * <p>Only to stop a very distant bubble collapsing to a zero-area box that nothing can ever
     * hit. It is deliberately tiny: a bubble three pixels tall <em>should</em> be hard to aim at,
     * because that is what the player can see.
     */
    private static final float MIN_HALF_EXTENT = 1.5f;

    /** One option's screen rectangle, republished every frame the conversation is drawn. */
    public record OptionHit(int index, float x0, float y0, float x1, float y1) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1;
        }

        /** Whether this rectangle covers any screen area at all. */
        public boolean usable() {
            return x1 > x0 && y1 > y0;
        }

        public float centreX() {
            return (x0 + x1) / 2.0f;
        }

        public float centreY() {
            return (y0 + y1) / 2.0f;
        }
    }

    /**
     * Builds an option's rectangle from its bubble projected top and bottom.
     *
     * <p>Pure arithmetic, separated so it can be tested without a running client - the projection
     * around it cannot be.
     *
     * <p>This used to be a <em>constant</em> 120x18 box around the bubble's projected centre, which
     * is the bug it replaces. A bubble shrinks as the player backs away but a constant box does
     * not, so the boxes grew into one another: option centres sit about 0.35 world metres apart, so
     * at four metres they were roughly 22 screen pixels apart against an 18-pixel-tall box, and by
     * eight metres only 11 apart - the boxes overlapped and the second option could not be selected
     * at all. Deriving the box from the bubble's own projected size keeps the two in step at every
     * distance.
     *
     * @param aspect the sprite's width divided by its height, so the box matches the art
     */
    static OptionHit boxFrom(int index, float topX, float topY, float bottomX, float bottomY,
                             float aspect) {
        float halfHeight = Math.max(MIN_HALF_EXTENT, Math.abs(bottomY - topY) / 2.0f);
        float halfWidth = Math.max(MIN_HALF_EXTENT, halfHeight * Math.max(0.01f, aspect));
        float centreX = (topX + bottomX) / 2.0f;
        float centreY = (topY + bottomY) / 2.0f;
        return new OptionHit(index, centreX - halfWidth, centreY - halfHeight,
                centreX + halfWidth, centreY + halfHeight);
    }

    /** A rectangle that covers nothing, for an option that is off screen or behind the camera. */
    static OptionHit noHit(int index) {
        return new OptionHit(index, 0, 0, -1, -1);
    }

    /**
     * The option under a point, or null.
     *
     * <p>Nearest centre wins when two rectangles still overlap. After the sizing fix they should
     * not at conversation range, but "whichever came first in the list" is a bad way to resolve a
     * tie the player can see.
     */
    public static OptionHit pick(double x, double y) {
        OptionHit best = null;
        double bestDistance = Double.MAX_VALUE;
        for (OptionHit hit : hits) {
            if (!hit.usable() || !hit.contains(x, y)) {
                continue;
            }
            double dx = hit.centreX() - x;
            double dy = hit.centreY() - y;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = hit;
            }
        }
        return best;
    }

    /**
     * The option under the crosshair, or null.
     *
     * <p>The crosshair is the centre of the screen, which is what the player is actually aiming
     * with once the conversation no longer takes the mouse.
     */
    /**
     * How far from the crosshair an option may sit and still be chosen, in GUI pixels.
     *
     * <p>Generous on purpose. The bubbles float above the NPC's head, so a player looking at the
     * NPC has the crosshair on its <em>body</em> and every option well above it. Requiring the
     * crosshair to land inside a bubble meant aiming past the thing you were talking to — the
     * conversation looked completely unresponsive while working exactly as written.
     */
    private static final double CROSSHAIR_REACH = 120.0;

    /**
     * The option a click at the crosshair should take.
     *
     * <p>Containment first, so aiming squarely at a bubble always takes that one. Otherwise the
     * nearest option within {@link #CROSSHAIR_REACH}, which is what makes looking at the NPC and
     * clicking work at all.
     */
    public static OptionHit pickAtCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) {
            return null;
        }
        double centreX = mc.getWindow().getGuiScaledWidth() / 2.0;
        double centreY = mc.getWindow().getGuiScaledHeight() / 2.0;
        OptionHit exact = pick(centreX, centreY);
        return exact != null ? exact : nearest(centreX, centreY, CROSSHAIR_REACH);
    }

    /**
     * The closest usable option within {@code reach}, or null when none is near enough.
     *
     * <p>Separate from {@link #pick} rather than folded into it: a mouse click on a screen means
     * "this exact spot", and should never drift to a neighbour. Only the crosshair, which cannot
     * be aimed at a bubble without looking away from the NPC, gets the tolerance.
     */
    static OptionHit nearest(double x, double y, double reach) {
        OptionHit best = null;
        double bestDistance = reach * reach;
        for (OptionHit hit : hits) {
            if (!hit.usable()) {
                continue;
            }
            double dx = hit.centreX() - x;
            double dy = hit.centreY() - y;
            double distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = hit;
            }
        }
        return best;
    }

    /** Test seam: the hit list is built during a render pass no unit test can run. */
    static void setHitsForTest(java.util.List<OptionHit> replacement) {
        hits = replacement == null ? List.of() : List.copyOf(replacement);
    }

    private static List<OptionHit> hits = List.of();

    private DialogueBubbleRenderer() {
    }

    /**
     * Where each option was drawn last frame.
     *
     * <p>Empty when nothing was drawn - off screen, too far, or no conversation - which is exactly
     * when a click should not select anything.
     */
    public static List<OptionHit> hits() {
        return hits;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        DialogueBubbleSession session = DialogueBubbleSession.active();
        Minecraft mc = Minecraft.getInstance();
        if (session == null || mc.level == null) {
            hits = List.of();
            return;
        }
        Entity entity = mc.level.getEntity(session.entityId());
        if (!(entity instanceof LivingEntity npc) || !npc.isAlive()) {
            // The NPC died or left the chunk mid-conversation. Ending it is kinder than leaving
            // bubbles floating over nothing.
            DialogueBubbleSession.endFor(session.entityId());
            hits = List.of();
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        if (npc.distanceToSqr(cameraPos) > MAX_DISTANCE_SQ) {
            hits = List.of();
            return;
        }

        if (inEntityPass(npc)) {
            // Drawn by XenoNpcRenderer (renderInEntityPass), which runs earlier in this frame. If it
            // did not draw - the NPC was culled - its answers are not on screen to be clicked.
            if (!drawnThisFrame) hits = List.of();
            drawnThisFrame = false;
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set((Matrix4fc) event.getModelViewMatrix());
        poseStack.last().normal().set(new Matrix3f((Matrix4fc) event.getModelViewMatrix()));

        mc.getMainRenderTarget().bindWrite(false);
        hits = draw(mc, npc, session, partialTick, poseStack, cameraPos);
    }

    /** Whether {@link #renderInEntityPass} owns this NPC's dialogue instead of the level pass. */
    static boolean inEntityPass(Entity entity) {
        return net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcBubblesInEntityPass
                && entity instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
    }

    private static boolean drawnThisFrame;

    /**
     * Draws a native NPC's dialogue from its own renderer, in the entity render pass, the way
     * DragonMineZ's ki-sense BP meter draws above a player. {@code entityPose} is the pose the
     * renderer was handed, whose origin is the NPC's interpolated position. Option hit boxes are
     * projected from world positions, so they do not depend on which pass drew them.
     */
    public static void renderInEntityPass(LivingEntity npc, PoseStack entityPose, float partialTick) {
        if (!inEntityPass(npc)) return;
        DialogueBubbleSession session = DialogueBubbleSession.active();
        Minecraft mc = Minecraft.getInstance();
        if (session == null || mc.level == null || session.entityId() != npc.getId() || !npc.isAlive()) {
            return;
        }
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        if (npc.distanceToSqr(cameraPos) > MAX_DISTANCE_SQ) {
            return;
        }
        hits = draw(mc, npc, session, partialTick, entityPose, npc.getPosition(partialTick));
        drawnThisFrame = true;
    }

    /**
     * The speaker line and the answers. {@code origin} is the world point {@code poseStack}
     * currently sits at: the camera for the level pass, the NPC for the entity pass.
     */
    private static List<OptionHit> draw(Minecraft mc, LivingEntity npc, DialogueBubbleSession session,
                                        float partialTick, PoseStack poseStack, Vec3 origin) {
        NpcCombatProfile profile = net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient
                .bubbleProfile(npc);
        // A node may name its own palette; blank means inherit the NPC's, which is what every
        // dialogue written before the field existed does.
        XenoDialogue.Node current = session.node();
        String nodePalette = current == null ? "" : current.palette();
        XenoAtlasSprites.Theme lineTheme = theme(
                nodePalette.isBlank() ? profile.bubblePalette : nodePalette);
        List<XenoDialogue.Option> options = session.options();
        boolean rowChoices = options.size() >= 2;
        XenoAtlasSprites.Sprite lineSprite = XenoAtlasSprites.get(
                SpeechBubbleLayout.spriteFor(mc.font, SpeechBubbleLayout.wrap(mc.font, session.text())),
                lineTheme);
        Vec3 pos = npc.getPosition(partialTick);
        Vec3 forward = npc.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
        if (forward.lengthSqr() < 1.0e-6) forward = new Vec3(0.0, 0.0, 1.0);
        Vec3 bubblePos = pos.add(forward.scale(0.16));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        List<XenoAtlasSprites.Sprite> optionSprites = new ArrayList<>(options.size());
        List<Integer> optionHeights = new ArrayList<>(options.size());
        List<Integer> optionWidths = new ArrayList<>(options.size());
        for (XenoDialogue.Option option : options) {
            XenoAtlasSprites.Theme optionTheme = theme(
                    option.palette().isBlank() ? profile.optionPalette : option.palette());
            XenoAtlasSprites.Sprite sprite = optionSprite(session.resolve(option.text()), optionTheme);
            optionSprites.add(sprite);
            optionHeights.add(sprite.height());
            optionWidths.add(sprite.width());
        }
        List<Integer> optionTops = rowChoices
                ? DialogueBubbleLayout.rowTops(optionHeights)
                : DialogueBubbleLayout.stackTops(optionHeights);
        List<Float> rowCentres = rowChoices
                ? DialogueBubbleLayout.rowCentres(optionWidths) : List.of();
        double optionAnchorHeight = npc.getBbHeight() * OPTION_HEIGHT_FRACTION;
        double speakerAnchorHeight = optionAnchorHeight
                + (DialogueBubbleLayout.groupHeight(optionHeights, rowChoices)
                + DialogueBubbleLayout.GAP_BELOW_LINE) * BUBBLE_SCALE;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        net.bullettrain.xenopixelsmod.client.npc.speech.BillboardDraw.beginOnTop();

        List<OptionHit> found = new ArrayList<>();
        try {
            // The speaker line sits above the complete answer group with a clear gap.
            poseStack.pushPose();
            translateBillboard(poseStack, bubblePos, origin,
                    speakerAnchorHeight,
                    mc);
            drawLineBubble(poseStack, buffers, session.text(), lineTheme);
            poseStack.popPose();

            // Answers form one non-overlapping group immediately below the speaker line.
            poseStack.pushPose();
            translateBillboard(poseStack, bubblePos, origin,
                    optionAnchorHeight,
                    mc);
            for (int i = 0; i < options.size(); i++) {
                // The sprite is resolved once and shared: the box has to describe the art that was
                // actually drawn, and re-resolving it risks the two drifting apart.
                String optionText = session.resolve(options.get(i).text());
                XenoAtlasSprites.Sprite sprite = optionSprites.get(i);
                float optionX = rowChoices ? rowCentres.get(i) : 0.0f;
                float optionY = optionTops.get(i);
                drawOption(poseStack, buffers, optionText, optionY, sprite, optionX);
                found.add(hitFor(i, bubblePos, npc, sprite, optionX, optionY));
            }
            poseStack.popPose();
        } finally {
            buffers.endBatch();
            net.bullettrain.xenopixelsmod.client.npc.speech.BillboardDraw.endOnTop();
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
        return List.copyOf(found);
    }

    private static void translateBillboard(PoseStack poseStack, Vec3 position, Vec3 cameraPosition,
                                           double anchorHeight, Minecraft mc) {
        poseStack.translate(position.x - cameraPosition.x, position.y - cameraPosition.y,
                position.z - cameraPosition.z);
        poseStack.translate(0.0, anchorHeight, 0.0);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        // Negative Y because GUI art is top-down and world space is not.
        poseStack.scale(BUBBLE_SCALE, -BUBBLE_SCALE, BUBBLE_SCALE);
    }

    /**
     * Draws the NPC's line and answers how tall it was.
     *
     * <p>Sits above the anchor, the way the speech bubble does, so its tail points at the head.
     */
    private static float drawLineBubble(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                        String text, XenoAtlasSprites.Theme palette) {
        Minecraft mc = Minecraft.getInstance();
        List<FormattedCharSequence> lines = SpeechBubbleLayout.wrap(mc.font, text);
        XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get(
                SpeechBubbleLayout.spriteFor(mc.font, lines), palette);

        float half = sprite.width() / 2.0f;
        float top = -sprite.height();

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, sprite.rl());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        BillboardDraw.texturedQuad(poseStack.last().pose(), -half, top, half, 0.0f);

        float bodyHeight = sprite.height() * (1.0f - SpeechBubbleLayout.TAIL_HEIGHT_FRACTION);
        float blockHeight = SpeechBubbleLayout.blockHeight(lines.size());
        float y = top + (bodyHeight - blockHeight) / 2.0f;
        for (FormattedCharSequence line : lines) {
            BillboardDraw.outlinedLine(poseStack.last().pose(), line, mc.font.width(line),
                    0.0f, y, 1.0f, buffers);
            y += SpeechBubbleLayout.LINE_HEIGHT;
        }
        return 0.0f;
    }

    /** The sprite one option's text resolves to, so the drawing and the hit box agree. */
    private static XenoAtlasSprites.Sprite optionSprite(String text,
                                                        XenoAtlasSprites.Theme palette) {
        Minecraft mc = Minecraft.getInstance();
        return XenoAtlasSprites.get(
                DialogueBubbleLayout.spriteFor(mc.font, DialogueBubbleLayout.wrap(mc.font, text)),
                palette);
    }

    /** Draws one option at {@code top}, and answers its height. */
    private static float drawOption(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                    String text, float top, XenoAtlasSprites.Sprite sprite,
                                    float centreX) {
        Minecraft mc = Minecraft.getInstance();
        List<FormattedCharSequence> lines = DialogueBubbleLayout.wrap(mc.font, text);

        float half = sprite.width() / 2.0f;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, sprite.rl());
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.pushPose();
        poseStack.translate(centreX, 0.0f, 0.0f);
        BillboardDraw.texturedQuad(poseStack.last().pose(), -half, top, half, top + sprite.height());

        float bodyHeight = sprite.height() * (1.0f - SpeechBubbleLayout.TAIL_HEIGHT_FRACTION);
        float blockHeight = SpeechBubbleLayout.blockHeight(lines.size());
        float y = top + (bodyHeight - blockHeight) / 2.0f;
        for (FormattedCharSequence line : lines) {
            BillboardDraw.outlinedLine(poseStack.last().pose(), line, mc.font.width(line),
                    0.0f, y, 1.0f, buffers);
            y += SpeechBubbleLayout.LINE_HEIGHT;
        }
        poseStack.popPose();
        return sprite.height();
    }

    /**
     * The screen rectangle for an option drawn at {@code localTop}.
     *
     * <p>The bubble's top and bottom edges are projected, not just its centre, and the rectangle is
     * sized from the distance between them. A billboard faces the camera, so its <em>vertical</em>
     * extent is the same world segment whichever way the camera is turned - which makes it a
     * reliable measure of how big the bubble currently looks - while its width follows from the
     * sprite's own aspect ratio.
     *
     * <p>Measuring it, rather than assuming a constant, is the whole point: see
     * {@link #boxFrom} for what the constant did at range.
     */
    private static OptionHit hitFor(int index, Vec3 base, LivingEntity npc,
                                    XenoAtlasSprites.Sprite sprite, float centreX, float actualTop) {
        double anchorY = base.y + npc.getBbHeight() * OPTION_HEIGHT_FRACTION;
        // Local Y grows downward (the pose stack negates it), so the top edge is the smaller
        // local offset and therefore the higher world position.
        double worldTop = anchorY - actualTop * BUBBLE_SCALE;
        double worldBottom = anchorY - (actualTop + sprite.height()) * BUBBLE_SCALE;
        Vector3f side = new Vector3f(centreX * BUBBLE_SCALE, 0.0f, 0.0f)
                .rotate(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        // Scaled GUI units, not raw pixels: a click from a Screen and the crosshair's own centre
        // are both expressed in that space.
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        WorldToScreenCache.ScreenPoint top =
                WorldToScreenCache.project(new Vec3(base.x + side.x, worldTop, base.z + side.z), width, height);
        WorldToScreenCache.ScreenPoint bottom =
                WorldToScreenCache.project(new Vec3(base.x + side.x, worldBottom, base.z + side.z), width, height);
        if (top == null || bottom == null || top.behindCamera() || bottom.behindCamera()) {
            return noHit(index);
        }
        float aspect = sprite.height() <= 0
                ? 1.0f : (float) sprite.width() / (float) sprite.height();
        return boxFrom(index, top.x(), top.y(), bottom.x(), bottom.y(), aspect);
    }

    /** Folds a stored palette name onto an atlas theme. */
    private static XenoAtlasSprites.Theme theme(String palette) {
        return XenoAtlasSprites.themeForPalette(palette);
    }
}
