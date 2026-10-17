package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiBurn;
import net.bullettrain.xenopixelsmod.space.math.PlanetSurfaceMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client half of planet Hakai (owner 2026-10-16: "effects around the planet and slowly disappear from top to bottom,
 * smooth"). While a body burns, {@link PhysicalPlanetRenderer} hands its HD sphere to {@link #drawBurning}: the sphere is
 * clipped against a plane that sweeps from the top to the bottom (world up), polygon by polygon, so the cut is a smooth
 * circle rather than whole bands. The opening is closed by a dark violet cap and edged by a glowing Hakai rim.
 */
@EventBusSubscriber(modid = "xenopixelsmod", value = Dist.CLIENT)
public final class PlanetHakaiClient {
    private record Burn(long startTick, int durationTicks, long hideUntil) {}

    private static final Map<String, Burn> BURNS = new ConcurrentHashMap<>();
    private static final PlanetSurfaceMesh.Mesh MESH = PlanetSurfaceMesh.create();
    private static final int RIM_SEGMENTS = 96;
    private static final Matrix4f MODEL = new Matrix4f();
    private static VertexBuffer surface, cap, rim;

    private PlanetHakaiClient() {}

    /** After the server says the burn finished, keep the body hidden this long so it cannot flash back as a "shadow"
     * before the updated chart snapshot (and Sable's body removal) reach this client. */
    private static final int HIDE_AFTER_TICKS = 100;

    public static void apply(PlanetHakaiBurn packet) {
        var level = Minecraft.getInstance().level;
        if (level == null) { BURNS.remove(packet.bodyId()); return; }
        if (!packet.active()) {
            // Finished: fully cut (progress 1) until the hide window runs out.
            BURNS.put(packet.bodyId(), new Burn(level.getGameTime() - 1_000_000L, 1, level.getGameTime() + HIDE_AFTER_TICKS));
            return;
        }
        BURNS.put(packet.bodyId(), new Burn(level.getGameTime(), packet.durationTicks(), Long.MAX_VALUE));
    }

    /** Progress 0..1 of a burning physical body, or -1 when it is not burning. */
    public static double progress(String id, float partial) {
        Burn burn = BURNS.get(id);
        var level = Minecraft.getInstance().level;
        if (burn == null || level == null) return -1;
        if (level.getGameTime() > burn.hideUntil) { BURNS.remove(id); return -1; }
        double t = (level.getGameTime() - burn.startTick + partial) / burn.durationTicks;
        // A burn whose stop packet never arrived stays hidden for a short grace period, then lets go.
        if (burn.hideUntil == Long.MAX_VALUE && t > 1 + HIDE_AFTER_TICKS / (double) burn.durationTicks) { BURNS.remove(id); return -1; }
        return Math.max(0, Math.min(1, t));
    }

    /**
     * Draws the clipped sphere with the shader state the caller already set up (position-tex-colour, texture bound);
     * then the cap and rim with position-colour, restoring the caller's shader. {@code orientation} maps body-local to world.
     */
    static void drawBurning(RenderLevelStageEvent event, Matrix4f model, Quaternionfc orientation, double progress) {
        // Plane height in unit-sphere coordinates along world up: +1 (nothing cut) -> -1 (all gone).
        float cut = (float) (1.0 - 2.0 * smooth(progress));
        if (cut <= -0.999f) return;
        Vector3f up = new Quaternionf(orientation).conjugate().transform(new Vector3f(0, 1, 0));
        // 1. Surface below the plane.
        BufferBuilder tri = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        int emitted = 0;
        float[] p = MESH.positions(), uv = MESH.uv();
        float[] px = new float[8], py = new float[8], pz = new float[8], pu = new float[8], pv = new float[8];
        for (int q = 0; q < p.length / 12; q++) {
            int n = 0;
            for (int i = 0; i < 4; i++) {
                int a = q * 4 + i, b = q * 4 + (i + 1) % 4;
                float da = cut - dot(p, a, up), db = cut - dot(p, b, up);
                if (da >= 0) { px[n] = p[a * 3]; py[n] = p[a * 3 + 1]; pz[n] = p[a * 3 + 2]; pu[n] = uv[a * 2]; pv[n] = uv[a * 2 + 1]; n++; }
                if ((da >= 0) != (db >= 0)) {
                    float t = da / (da - db);
                    px[n] = lerp(p[a * 3], p[b * 3], t); py[n] = lerp(p[a * 3 + 1], p[b * 3 + 1], t); pz[n] = lerp(p[a * 3 + 2], p[b * 3 + 2], t);
                    pu[n] = lerp(uv[a * 2], uv[b * 2], t); pv[n] = lerp(uv[a * 2 + 1], uv[b * 2 + 1], t);
                    n++;
                }
            }
            for (int i = 1; i + 1 < n; i++) {
                tri.addVertex(px[0], py[0], pz[0]).setUv(pu[0], pv[0]).setColor(0xffffffff);
                tri.addVertex(px[i], py[i], pz[i]).setUv(pu[i], pv[i]).setColor(0xffffffff);
                tri.addVertex(px[i + 1], py[i + 1], pz[i + 1]).setUv(pu[i + 1], pv[i + 1]).setColor(0xffffffff);
                emitted++;
            }
        }
        if (emitted > 0) surface = draw(surface, tri, model, event, GameRenderer.getPositionTexColorShader());
        else tri.build();
        // 2. Cap and rim on the cut circle (centre up*cut, radius sqrt(1-cut^2)), in the plane perpendicular to up.
        float ring = (float) Math.sqrt(Math.max(0, 1 - cut * cut));
        if (ring < 1e-3f) return;
        Vector3f e1 = new Vector3f(up).cross(Math.abs(up.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
        Vector3f e2 = new Vector3f(up).cross(e1).normalize();
        Vector3f c = new Vector3f(up).mul(cut);
        var shader = RenderSystem.getShader();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        BufferBuilder capBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < RIM_SEGMENTS; i++) {
            Vector3f a = point(c, e1, e2, ring, i), b = point(c, e1, e2, ring, i + 1);
            capBuilder.addVertex(c.x, c.y, c.z).setColor(0xff0b0014);
            capBuilder.addVertex(a.x, a.y, a.z).setColor(0xff3a0a66);
            capBuilder.addVertex(b.x, b.y, b.z).setColor(0xff3a0a66);
        }
        cap = draw(cap, capBuilder, model, event, GameRenderer.getPositionColorShader());
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        float pulse = 0.75f + 0.25f * (float) Math.sin((Minecraft.getInstance().level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.6);
        BufferBuilder rimBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float inner = ring * 0.96f, outer = ring * 1.06f + 0.02f;
        int core = argb((int) (230 * pulse), 236, 170, 255), edge = argb(0, 140, 40, 255);
        for (int i = 0; i < RIM_SEGMENTS; i++) {
            Vector3f a0 = point(c, e1, e2, inner, i), a1 = point(c, e1, e2, inner, i + 1);
            Vector3f b0 = point(c, e1, e2, outer, i), b1 = point(c, e1, e2, outer, i + 1);
            // Inner half of the band glows, outer half fades out; a thin vertical lip lifts it off the sphere.
            Vector3f l0 = new Vector3f(a0).fma(0.05f, up), l1 = new Vector3f(a1).fma(0.05f, up);
            rimBuilder.addVertex(a0.x, a0.y, a0.z).setColor(core); rimBuilder.addVertex(b0.x, b0.y, b0.z).setColor(edge);
            rimBuilder.addVertex(b1.x, b1.y, b1.z).setColor(edge); rimBuilder.addVertex(a1.x, a1.y, a1.z).setColor(core);
            rimBuilder.addVertex(a0.x, a0.y, a0.z).setColor(core); rimBuilder.addVertex(a1.x, a1.y, a1.z).setColor(core);
            rimBuilder.addVertex(l1.x, l1.y, l1.z).setColor(edge); rimBuilder.addVertex(l0.x, l0.y, l0.z).setColor(edge);
        }
        rim = draw(rim, rimBuilder, model, event, GameRenderer.getPositionColorShader());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        if (shader != null) RenderSystem.setShader(() -> shader);
    }

    private static VertexBuffer draw(VertexBuffer buffer, BufferBuilder builder, Matrix4f model, RenderLevelStageEvent event,
                                     net.minecraft.client.renderer.ShaderInstance shader) {
        var mesh = builder.build();
        if (mesh == null) return buffer;
        if (buffer == null) buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        buffer.bind();
        buffer.upload(mesh);
        buffer.drawWithShader(MODEL.set(model), event.getProjectionMatrix(), shader);
        VertexBuffer.unbind();
        return buffer;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private static float dot(float[] p, int v, Vector3f up) { return p[v * 3] * up.x + p[v * 3 + 1] * up.y + p[v * 3 + 2] * up.z; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static int argb(int a, int r, int g, int b) { return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b; }

    private static Vector3f point(Vector3f c, Vector3f e1, Vector3f e2, float r, int i) {
        double a = Math.PI * 2 * i / RIM_SEGMENTS;
        return new Vector3f(c).fma((float) (Math.cos(a) * r), e1).fma((float) (Math.sin(a) * r), e2);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { BURNS.clear(); }
}
