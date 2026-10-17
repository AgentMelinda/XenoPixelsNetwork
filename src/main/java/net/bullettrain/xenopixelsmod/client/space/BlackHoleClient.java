package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.blackhole.BlackHoleRules;
import net.bullettrain.xenopixelsmod.space.blackhole.BlackHoleSnapshot;
import net.bullettrain.xenopixelsmod.space.blackhole.BlackHoleStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Client side of design-doc 15 black holes: the synced hole list, the ESCAPE meter on the HUD while in a tidal band, and
 * the hole itself in the Voidsea - a camera-facing black event-horizon disc inside a flat accretion ring (orange inner
 * edge fading to violet). A hole beyond {@link #PROXY_DISTANCE} is drawn at that distance in the same direction, scaled to
 * keep its apparent size (it would otherwise sit past the far plane). Two static meshes, built once; one draw each.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class BlackHoleClient {
    private static final ResourceLocation VOIDSEA = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "voidsea");
    private static final double PROXY_DISTANCE = 1_000;
    private static final int SEGMENTS = 64;
    private static final float RING_INNER = 1.25f, RING_OUTER = 4.0f;
    private static final Matrix4f MODEL = new Matrix4f();

    private static volatile BlackHoleSnapshot snapshot;
    private static volatile BlackHoleStatus status = BlackHoleStatus.NONE;
    private static VertexBuffer ring, disc;

    private BlackHoleClient() {}

    public static void apply(BlackHoleSnapshot packet) { snapshot = packet; }

    public static void apply(BlackHoleStatus packet) { status = packet; }

    /** HUD layer: ESCAPE meter, shown only while the server reports a hole near. */
    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        BlackHoleStatus s = status;
        Minecraft mc = Minecraft.getInstance();
        if (!s.active() || mc.player == null || mc.options.hideGui) return;
        int w = 120, h = 6, x = g.guiWidth() / 2 - w / 2, y = g.guiHeight() / 2 + 44;
        float escape = s.escape();
        int colour = escape > 0.5f ? 0xFF7CD6FF : escape > 0.2f ? 0xFFFFC46B : 0xFFE04848;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
        g.fill(x, y, x + Math.round(w * escape), y + h, colour);
        g.drawCenteredString(mc.font, Component.translatable("hud.xenopixelsmod.black_hole_escape"), g.guiWidth() / 2, y - 10, colour);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        boolean pack = IrisShaderState.packInUse();
        RenderLevelStageEvent.Stage wanted = pack ? RenderLevelStageEvent.Stage.AFTER_LEVEL : RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (event.getStage() != wanted || (pack && IrisShaderState.renderingShadows())) return;
        var mc = Minecraft.getInstance();
        BlackHoleSnapshot snap = snapshot;
        if (mc.level == null || snap == null || snap.holes().isEmpty() || !VOIDSEA.equals(mc.level.dimension().location())) return;
        var camera = event.getCamera().getPosition();
        if (pack) mc.getMainRenderTarget().bindWrite(false);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        var shader = RenderSystem.getShader();
        float[] c = RenderSystem.getShaderColor();
        float r = c[0], gr = c[1], b = c[2], a = c[3];
        try {
            ensureMeshes();
            FogRenderer.setupNoFog();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            for (BlackHoleRules.Hole hole : snap.holes()) {
                double dx = hole.x() - camera.x, dy = hole.y() - camera.y, dz = hole.z() - camera.z;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance < 1e-3) continue;
                double scale = distance > PROXY_DISTANCE ? PROXY_DISTANCE / distance : 1.0;
                float ox = (float) (dx * scale), oy = (float) (dy * scale), oz = (float) (dz * scale);
                float radius = (float) (hole.horizon() * scale);
                MODEL.set(event.getModelViewMatrix()).translate(ox, oy, oz).scale(radius);
                ring.bind();
                ring.drawWithShader(MODEL, event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
                // Event horizon: a disc turned to face the camera (yaw then pitch toward the viewer).
                float yaw = (float) Math.atan2(-ox, -oz), pitch = (float) Math.atan2(oy, Math.sqrt(ox * ox + oz * oz));
                MODEL.set(event.getModelViewMatrix()).translate(ox, oy, oz).rotateY(yaw).rotateX(pitch).scale(radius);
                disc.bind();
                disc.drawWithShader(MODEL, event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.depthMask(write);
            RenderSystem.depthFunc(depthFunction);
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(r, gr, b, a);
            if (shader != null) RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    /** Unit meshes (horizon radius 1): the ring in the XZ plane, the disc in the XY plane facing +Z. */
    private static void ensureMeshes() {
        if (ring != null && disc != null) return;
        BufferBuilder rb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / SEGMENTS, a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            int inner = 0xF0FFB347, outer = 0x009A5CFF;
            rb.addVertex(c0 * RING_INNER, 0, s0 * RING_INNER).setColor(inner);
            rb.addVertex(c0 * RING_OUTER, 0, s0 * RING_OUTER).setColor(outer);
            rb.addVertex(c1 * RING_OUTER, 0, s1 * RING_OUTER).setColor(outer);
            rb.addVertex(c1 * RING_INNER, 0, s1 * RING_INNER).setColor(inner);
        }
        ring = upload(rb);
        BufferBuilder db = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / SEGMENTS, a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            db.addVertex(0, 0, 0).setColor(0xFF000000);
            db.addVertex(c0, s0, 0).setColor(0xFF000000);
            db.addVertex(c1, s1, 0).setColor(0xFF000000);
            db.addVertex(0, 0, 0).setColor(0xFF000000);
        }
        disc = upload(db);
    }

    private static VertexBuffer upload(BufferBuilder builder) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try { buffer.bind(); buffer.upload(builder.buildOrThrow()); return buffer; }
        catch (RuntimeException | Error failure) { buffer.close(); throw failure; }
        finally { VertexBuffer.unbind(); }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        snapshot = null;
        status = BlackHoleStatus.NONE;
    }
}
