package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.event.StellarBlastWaveMath;
import net.bullettrain.xenopixelsmod.space.event.StellarBlastWaveSnapshot;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** One cached low-poly wire shell per active nova, with radius interpolated locally between server snapshots. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class StellarBlastWaveRenderer {
    private static final net.minecraft.resources.ResourceLocation VOIDSEA = PhysicalEarth.HOST;
    /** Matches the established camera-relative float-precision cutoff used by PhysicalPlanetRenderer. */
    private static final double MAX_CAMERA_DISTANCE = 1_000_000.0;
    private static final Matrix4f MODEL = new Matrix4f();
    private static VertexBuffer shell;

    private StellarBlastWaveRenderer() {}

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || (IrisShaderState.packInUse() && IrisShaderState.renderingShadows())
                || !VOIDSEA.equals(mc.level.dimension().location())) return;
        StellarBlastWaveSnapshot packet = StellarBlastWaveClient.current();
        if (packet == null || !packet.enabled() || packet.waves().isEmpty()) return;

        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        float[] shaderColor = RenderSystem.getShaderColor();
        // getShaderColor returns the mutable internal array; copy before changing the shader state.
        float oldRed = shaderColor[0], oldGreen = shaderColor[1];
        float oldBlue = shaderColor[2], oldAlpha = shaderColor[3];
        var oldShader = RenderSystem.getShader();
        var camera = event.getCamera().getPosition();
        long clientTick = mc.level.getGameTime();
        double partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        try {
            ensureShell();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            FogRenderer.setupNoFog();
            shell.bind();
            for (var wave : packet.waves()) {
                double age = StellarBlastWaveClient.ageTicks(wave, clientTick, partial);
                if (age >= packet.durationTicks()) continue;
                double radius = StellarBlastWaveMath.radiusAtAge(age, packet.speedBlocksPerTick());
                if (!(radius > 0.05)) continue;
                double dx = wave.x() - camera.x, dy = wave.y() - camera.y, dz = wave.z() - camera.z;
                if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                        || dx * dx + dy * dy + dz * dz > MAX_CAMERA_DISTANCE * MAX_CAMERA_DISTANCE) continue;
                MODEL.set(event.getModelViewMatrix()).translate((float) dx, (float) dy, (float) dz)
                        .scale((float) radius);
                shell.drawWithShader(MODEL, event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.depthMask(depthWrite);
            RenderSystem.depthFunc(depthFunc);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            RenderSystem.setShaderColor(oldRed, oldGreen, oldBlue, oldAlpha);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            if (oldShader != null) RenderSystem.setShader(() -> oldShader);
        }
    }

    private static void ensureShell() {
        if (shell != null) return;
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);
        int segments = 24;
        int latitudes = 7;
        int longitudes = 12;
        int outer = 0x90D8F5FF;
        int inner = 0x704AAEFF;
        for (int ring = 1; ring <= latitudes; ring++) {
            double latitude = -Math.PI / 2.0 + Math.PI * ring / (latitudes + 1.0);
            addRing(builder, latitude, segments, outer);
        }
        for (int meridian = 0; meridian < longitudes; meridian++) {
            double longitude = Math.PI * meridian / longitudes;
            addMeridian(builder, longitude, segments, inner);
        }
        shell = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            shell.bind();
            shell.upload(builder.buildOrThrow());
        } catch (RuntimeException | Error failure) {
            shell.close();
            shell = null;
            throw failure;
        } finally {
            VertexBuffer.unbind();
        }
    }

    private static void addRing(BufferBuilder builder, double latitude, int segments, int color) {
        double y = Math.sin(latitude), horizontal = Math.cos(latitude);
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0 * i / segments, b = Math.PI * 2.0 * (i + 1) / segments;
            line(builder, horizontal * Math.cos(a), y, horizontal * Math.sin(a),
                    horizontal * Math.cos(b), y, horizontal * Math.sin(b), color);
        }
    }

    private static void addMeridian(BufferBuilder builder, double longitude, int segments, int color) {
        for (int i = 0; i < segments; i++) {
            double a = -Math.PI / 2.0 + Math.PI * i / segments;
            double b = -Math.PI / 2.0 + Math.PI * (i + 1) / segments;
            line(builder, Math.cos(a) * Math.cos(longitude), Math.sin(a), Math.cos(a) * Math.sin(longitude),
                    Math.cos(b) * Math.cos(longitude), Math.sin(b), Math.cos(b) * Math.sin(longitude), color);
        }
    }

    private static void line(BufferBuilder builder, double ax, double ay, double az,
                             double bx, double by, double bz, int color) {
        builder.addVertex((float) ax, (float) ay, (float) az).setColor(color);
        builder.addVertex((float) bx, (float) by, (float) bz).setColor(color);
    }

    public static void closeBuffer() {
        if (shell != null) { shell.close(); shell = null; }
    }
}
