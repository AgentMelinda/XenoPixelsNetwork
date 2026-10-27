package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bullettrain.xenopixelsmod.space.math.FallenStarPlan;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** One cached, world-seeded ambient streak at most; no particles or per-frame geometry allocation. */
final class FallenStarSkyLayer {
    private static VertexBuffer buffer;
    private static FallenStarPlan.Streak built;
    private static long builtSeed = Long.MIN_VALUE;

    private FallenStarSkyLayer() {}

    static void draw(Matrix4f view, Matrix4f projection, double worldTick, long worldSeed, int density) {
        draw(view, projection, worldTick, worldSeed, density, 1.0f);
    }

    static void draw(Matrix4f view, Matrix4f projection, double worldTick, long worldSeed, int density,
                     float visibility) {
        density = net.bullettrain.xenopixelsmod.space.math.FallenStarVisibility.effectiveDensity(
                FallenStarServerSettings.enabled(), density);
        float nightVisibility = net.bullettrain.xenopixelsmod.space.math.FallenStarVisibility
                .clampVisibility(visibility);
        if (density <= 0 || nightVisibility <= 0 || !Double.isFinite(worldTick) || worldTick < 0) {
            close();
            return;
        }
        long tick = (long) Math.floor(worldTick);
        var streak = FallenStarPlan.at(tick, worldSeed, density);
        if (streak == null) {
            close();
            return;
        }
        if (!streak.equals(built) || worldSeed != builtSeed || buffer == null) rebuild(streak, worldSeed);
        if (buffer == null) return;
        double localTick = worldTick - streak.windowIndex() * FallenStarPlan.WINDOW_TICKS;
        float alpha = (float) FallenStarPlan.alpha(streak, localTick) * nightVisibility;
        if (alpha <= 0) return;

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float[] liveColor = RenderSystem.getShaderColor();
        float oldRed = liveColor[0], oldGreen = liveColor[1], oldBlue = liveColor[2], oldAlpha = liveColor[3];
        var oldShader = RenderSystem.getShader();
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, alpha);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer.bind();
            buffer.drawWithShader(view, projection, GameRenderer.getPositionColorShader());
        } finally {
            VertexBuffer.unbind();
            RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            RenderSystem.setShaderColor(oldRed, oldGreen, oldBlue, oldAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (oldShader != null) RenderSystem.setShader(() -> oldShader);
        }
    }

    static void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        built = null;
        builtSeed = Long.MIN_VALUE;
    }

    private static void rebuild(FallenStarPlan.Streak streak, long seed) {
        close();
        var start = streak.start();
        var end = streak.end();
        double dx = end.x() - start.x(), dy = end.y() - start.y(), dz = end.z() - start.z();
        double mx = (start.x() + end.x()) * 0.5, my = (start.y() + end.y()) * 0.5, mz = (start.z() + end.z()) * 0.5;
        double sideX = my * dz - mz * dy, sideY = mz * dx - mx * dz, sideZ = mx * dy - my * dx;
        double sideLength = Math.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
        if (!(sideLength > 1.0e-8) || !Double.isFinite(sideLength)) return;
        double halfWidth = 0.12 / sideLength;
        sideX *= halfWidth; sideY *= halfWidth; sideZ *= halfWidth;
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex((float)(start.x() - sideX), (float)(start.y() - sideY), (float)(start.z() - sideZ)).setColor(0xFFFFFFFF);
        builder.addVertex((float)(end.x() - sideX), (float)(end.y() - sideY), (float)(end.z() - sideZ)).setColor(0xFFFFFFFF);
        builder.addVertex((float)(end.x() + sideX), (float)(end.y() + sideY), (float)(end.z() + sideZ)).setColor(0xFFFFFFFF);
        builder.addVertex((float)(start.x() + sideX), (float)(start.y() + sideY), (float)(start.z() + sideZ)).setColor(0xFFFFFFFF);
        VertexBuffer next = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            next.bind();
            next.upload(builder.buildOrThrow());
            buffer = next;
            built = streak;
            builtSeed = seed;
        } catch (RuntimeException | Error failure) {
            next.close();
            throw failure;
        } finally {
            VertexBuffer.unbind();
        }
    }
}
