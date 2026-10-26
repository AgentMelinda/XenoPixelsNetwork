package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiBurn;
import net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiTargetGlowPolicy;
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
    private record Burn(double startTick, int durationTicks, long hideUntil, Vector3f impact) {}

    private static final Map<String, Burn> BURNS = new ConcurrentHashMap<>();
    private static final Map<String, net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiSync> SYNC = new ConcurrentHashMap<>();
    private static final PlanetSurfaceMesh.Mesh MESH = PlanetSurfaceMesh.create();
    private static final int RIM_SEGMENTS = 96;
    private static final int TARGET_GLOW_SEGMENTS = 48;
    private static final Matrix4f MODEL = new Matrix4f();
    private static VertexBuffer surface, cap, rim;

    private PlanetHakaiClient() {}

    /** After the server says the burn finished, keep the body hidden this long so it cannot flash back as a "shadow"
     * before the updated chart snapshot (and Sable's body removal) reach this client. */
    private static final int HIDE_AFTER_TICKS = 100;

    public static void apply(net.bullettrain.xenopixelsmod.space.hakai.PlanetHakaiSync packet) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        String id = packet.burn().bodyId();
        var previous = SYNC.get(id);
        if (!packet.newerThan(previous)) return;
        SYNC.put(id, packet);
        if (!packet.burn().active()) { apply(packet.burn()); return; }
        double now = level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double progress = packet.progressAt(now);
        Burn shown = BURNS.get(id);
        // Refreshing the same server burn must never rewind its cut due to a late sample.
        if (shown != null && previous != null && previous.startedTick() == packet.startedTick())
            progress = Math.max(progress, (now - shown.startTick) / shown.durationTicks);
        BURNS.put(id, new Burn(now - Math.clamp(progress, 0, 1) * packet.burn().durationTicks(),
                packet.burn().durationTicks(), Long.MAX_VALUE,
                new Vector3f(packet.burn().impactX(), packet.burn().impactY(), packet.burn().impactZ())));
    }

    public static void apply(PlanetHakaiBurn packet) {
        var level = Minecraft.getInstance().level;
        if (level == null) { BURNS.remove(packet.bodyId()); return; }
        if (!packet.active()) {
            // Finished: fully cut (progress 1) until the hide window runs out.
            BURNS.put(packet.bodyId(), new Burn(level.getGameTime() - 1_000_000L, 1, level.getGameTime() + HIDE_AFTER_TICKS,
                    new Vector3f(packet.impactX(), packet.impactY(), packet.impactZ())));
            return;
        }
        BURNS.put(packet.bodyId(), new Burn(level.getGameTime(), packet.durationTicks(), Long.MAX_VALUE,
                new Vector3f(packet.impactX(), packet.impactY(), packet.impactZ())));
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

    public static Vector3f impactDirection(String id) {
        Burn burn = BURNS.get(id);
        return burn == null ? new Vector3f(0, 1, 0) : new Vector3f(burn.impact);
    }

    /**
     * Draws the clipped sphere with the shader state the caller already set up (position-tex-colour, texture bound);
     * then the cap and rim with position-colour, restoring the caller's shader. {@code orientation} maps body-local to world.
     */
    static void drawBurning(RenderLevelStageEvent event, String id, Matrix4f model, Quaternionfc orientation, double progress) {
        // Plane height in unit-sphere coordinates along world up: +1 (nothing cut) -> -1 (all gone).
        float cut = (float) (1.0 - 2.0 * smooth(progress));
        if (cut <= -0.999f) return;
        Vector3f up = new Quaternionf(orientation).conjugate().transform(impactDirection(id));
        // 1. Surface below the plane.
        BufferBuilder tri = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        int emitted = 0;
        float[] p = MESH.positions(), uv = MESH.uv();
        float[] px = new float[8], py = new float[8], pz = new float[8], pu = new float[8], pv = new float[8];
        float pulse = 0.75f + 0.25f * (float) Math.sin((Minecraft.getInstance().level.getGameTime()
                + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.6);
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
                tri.addVertex(px[0], py[0], pz[0]).setUv(pu[0], pv[0]).setColor(surfaceColor(px[0],py[0],pz[0],up,cut,pulse));
                tri.addVertex(px[i], py[i], pz[i]).setUv(pu[i], pv[i]).setColor(surfaceColor(px[i],py[i],pz[i],up,cut,pulse));
                tri.addVertex(px[i + 1], py[i + 1], pz[i + 1]).setUv(pu[i + 1], pv[i + 1])
                        .setColor(surfaceColor(px[i + 1],py[i + 1],pz[i + 1],up,cut,pulse));
                emitted++;
            }
        }
        if (emitted > 0) surface = draw(surface, tri, model, event, GameRenderer.getPositionTexColorShader());
        else tri.build();
        // 2. Cap and rim on the cut circle (centre up*cut, radius sqrt(1-cut^2)), in the plane perpendicular to up.
        float ring = (float) Math.sqrt(Math.max(0, 1 - cut * cut));
        drawImpactTargetGlow(event, model, up, progress);
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
        BufferBuilder rimBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float inner = ring * 0.96f, outer = ring * 1.06f + 0.02f;
        int core = argb((int) (230 * pulse), 236, 170, 255), edge = argb(0, 140, 40, 255);
        for (int i = 0; i < RIM_SEGMENTS; i++) {
            Vector3f a0 = point(c, e1, e2, inner, i).mul(1.012f), a1 = point(c, e1, e2, inner, i + 1).mul(1.012f);
            Vector3f b0 = point(c, e1, e2, outer, i).mul(1.012f), b1 = point(c, e1, e2, outer, i + 1).mul(1.012f);
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

    /** A planet-only target glow, anchored at the server's crosshair/projectile impact point. */
    private static void drawImpactTargetGlow(RenderLevelStageEvent event, Matrix4f model, Vector3f up, double progress) {
        if (!net.bullettrain.xenopixelsmod.config.XenoServerConfig.hakaiTargetGlow) return;
        var sample = PlanetHakaiTargetGlowPolicy.sample(progress, true);
        if (!sample.visible()) return;

        Vector3f e1 = new Vector3f(up).cross(Math.abs(up.y) < 0.9f
                ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
        Vector3f e2 = new Vector3f(up).cross(e1).normalize();
        float inner = Math.max(0.002f, sample.angleRadians() - sample.halfWidthRadians());
        float outer = sample.angleRadians() + sample.halfWidthRadians();
        Vector3f innerCenter = new Vector3f(up).mul((float) Math.cos(inner) * 1.012f);
        Vector3f outerCenter = new Vector3f(up).mul((float) Math.cos(outer) * 1.012f);
        float innerRadius = (float) Math.sin(inner) * 1.012f;
        float outerRadius = (float) Math.sin(outer) * 1.012f;
        int rgb = net.bullettrain.xenopixelsmod.config.XenoServerConfig.hakaiGlowColor & 0xFFFFFF;
        int alpha = Math.max(0, Math.min(255, Math.round(sample.alpha() * 220)));
        int hot = (alpha << 24) | rgb;
        int soft = (Math.max(0, alpha / 5) << 24) | rgb;

        var previousShader = RenderSystem.getShader();
        boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        boolean cull = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        boolean depthMask = org.lwjgl.opengl.GL11.glGetBoolean(org.lwjgl.opengl.GL11.GL_DEPTH_WRITEMASK);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferBuilder glow = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < TARGET_GLOW_SEGMENTS; i++) {
            Vector3f a = point(innerCenter, e1, e2, innerRadius, i);
            Vector3f b = point(innerCenter, e1, e2, innerRadius, i + 1);
            Vector3f c = point(outerCenter, e1, e2, outerRadius, i + 1);
            Vector3f d = point(outerCenter, e1, e2, outerRadius, i);
            glow.addVertex(a.x, a.y, a.z).setColor(hot);
            glow.addVertex(b.x, b.y, b.z).setColor(hot);
            glow.addVertex(c.x, c.y, c.z).setColor(soft);
            glow.addVertex(d.x, d.y, d.z).setColor(soft);
        }
        rim = draw(rim, glow, model, event, GameRenderer.getPositionColorShader());
        RenderSystem.depthMask(depthMask);
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        if (previousShader != null) RenderSystem.setShader(() -> previousShader);
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
    private static int surfaceColor(float x, float y, float z, Vector3f up, float cut, float pulse) {
        return PlanetHakaiSurfaceTint.color(x * up.x + y * up.y + z * up.z, cut, pulse);
    }
    private static float dot(float[] p, int v, Vector3f up) { return p[v * 3] * up.x + p[v * 3 + 1] * up.y + p[v * 3 + 2] * up.z; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static int argb(int a, int r, int g, int b) { return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b; }

    private static Vector3f point(Vector3f c, Vector3f e1, Vector3f e2, float r, int i) {
        double a = Math.PI * 2 * i / RIM_SEGMENTS;
        return new Vector3f(c).fma((float) (Math.cos(a) * r), e1).fma((float) (Math.sin(a) * r), e2);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { BURNS.clear(); SYNC.clear(); }
}
