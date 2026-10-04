package net.bullettrain.xenopixelsmod.client.npc.speech;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/**
 * The two primitives every billboarded bubble is built from.
 *
 * <p>Both were private inside {@link SpeechBubbleRenderer} until dialogue options needed the same
 * two. Shared rather than copied: a second copy of the outlined-text pass would drift from this one
 * the first time either was touched, and they would stop looking like the same UI.
 *
 * <p>Provenance is worth keeping. {@link #texturedQuad} is a rewrite of DragonMineZ's
 * {@code RenderBufferUtil#drawTexturedQuad}, and {@link #outlinedLine} is the technique from its
 * {@code KiSenseEvent#drawText} - four dark passes offset by a pixel, then one in the main colour.
 * Both were read out of the decompiled jar, not guessed at.
 */
public final class BillboardDraw {

    /** Full-bright, so a bubble is readable in a cave at night. */
    private static final int LIGHT = 0xF000F0;

    private BillboardDraw() {
    }

    /**
     * Bubble art always drawn over the world, but with its depth written. With the depth test simply
     * off nothing is written, so anything drawn later in the frame - clouds, weather - painted over a
     * bubble drawn in the entity pass (2026-09-30 owner: "clouds can be seend inside the bubels").
     * Text stays {@code SEE_THROUGH}, so it is unaffected by the bubble's own depth.
     */
    public static void beginOnTop() {
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_ALWAYS);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
    }

    /** Back to vanilla's depth function after {@link #beginOnTop}. */
    public static void endOnTop() {
        com.mojang.blaze3d.systems.RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    /** One textured quad in the billboard's local space. */
    public static void texturedQuad(Matrix4f matrix, float x0, float y0, float x1, float y1,
                                    float z, float minU, float minV, float maxU, float maxV) {
        // Every quad, not once per bubble: outlinedLine flushes the text batch, and the text render
        // type's clear state restores vanilla depth, so a quad drawn after any text - the dialogue's
        // answer bubbles, after the speaker line - let clouds and the nameplate show through it.
        // (2026-09-30 owner: the blue line bubble was fixed but the answers still showed clouds.)
        beginOnTop();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x0, y1, z).setUv(minU, maxV);
        buffer.addVertex(matrix, x1, y1, z).setUv(maxU, maxV);
        buffer.addVertex(matrix, x1, y0, z).setUv(maxU, minV);
        buffer.addVertex(matrix, x0, y0, z).setUv(minU, minV);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** The whole sprite, which is what every bubble wants. */
    public static void texturedQuad(Matrix4f matrix, float x0, float y0, float x1, float y1) {
        texturedQuad(matrix, x0, y0, x1, y1, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f);
    }

    /**
     * One line of outlined text, centred on {@code centerX}.
     *
     * <p>Takes a {@link FormattedCharSequence} rather than a {@code String} because the caller has
     * already wrapped it - measuring and drawing a whole message as one line is what used to make
     * long text overflow its bubble.
     */
    public static void outlinedLine(Matrix4f matrix, FormattedCharSequence line, float lineWidth,
                                    float centerX, float top, float alpha,
                                    MultiBufferSource.BufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        float x = centerX - lineWidth / 2.0f;

        int a = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
        int textColor = a << 24 | 0xFFFFFF;
        int outlineColor = a << 24;
        FormattedCharSequence outline = forceColor(line, 0x000000);

        mc.font.drawInBatch(outline, x + 1, top, outlineColor, false, matrix, buffers,
                Font.DisplayMode.SEE_THROUGH, 0, LIGHT);
        mc.font.drawInBatch(outline, x - 1, top, outlineColor, false, matrix, buffers,
                Font.DisplayMode.SEE_THROUGH, 0, LIGHT);
        mc.font.drawInBatch(outline, x, top + 1, outlineColor, false, matrix, buffers,
                Font.DisplayMode.SEE_THROUGH, 0, LIGHT);
        mc.font.drawInBatch(outline, x, top - 1, outlineColor, false, matrix, buffers,
                Font.DisplayMode.SEE_THROUGH, 0, LIGHT);
        buffers.endBatch();
        mc.font.drawInBatch(line, x, top, textColor, false, matrix, buffers,
                Font.DisplayMode.SEE_THROUGH, 0, LIGHT);
        buffers.endBatch();
    }

    /** Preserves formatting flags while making every formatted run use the same outline colour. */
    static FormattedCharSequence forceColor(FormattedCharSequence text, int color) {
        return sink -> text.accept((index, style, codePoint) ->
                sink.accept(index, style.withColor(color), codePoint));
    }
}
