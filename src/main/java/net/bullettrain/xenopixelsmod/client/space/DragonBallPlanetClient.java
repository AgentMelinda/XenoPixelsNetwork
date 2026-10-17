package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetShape;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Poly form of the planet-sized Super Dragon Balls: one shared UV sphere mesh (shading baked into vertex colour) and one
 * generated equirectangular texture per star count, both built from {@link DragonBallPlanetShape} so they match the
 * voxel form. Opaque and depth-tested, drawn after entities in the Voidsea; not drawn under shader packs in this bench.
 */
@EventBusSubscriber(modid = "xenopixelsmod", value = Dist.CLIENT)
public final class DragonBallPlanetClient {
    private static final ResourceLocation VOIDSEA = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "voidsea");
    private static final int LON = 96, LAT = 48, TEX_W = 1024, TEX_H = 512;
    private static final Matrix4f MODEL = new Matrix4f();
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[7];
    private static volatile DragonBallPlanetSnapshot snapshot;
    private static VertexBuffer sphere;

    private DragonBallPlanetClient() {}

    public static void apply(DragonBallPlanetSnapshot packet) { snapshot = packet; }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || IrisShaderState.packInUse()) return;
        var mc = Minecraft.getInstance();
        DragonBallPlanetSnapshot snap = snapshot;
        if (mc.level == null || snap == null || snap.balls().isEmpty() || !VOIDSEA.equals(mc.level.dimension().location())) return;
        var camera = event.getCamera().getPosition();
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        var shader = RenderSystem.getShader();
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        try {
            ensureResources();
            FogRenderer.setupNoFog();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            sphere.bind();
            for (var ball : snap.balls()) {
                RenderSystem.setShaderTexture(0, TEXTURES[ball.stars() - 1]);
                MODEL.set(event.getModelViewMatrix()).translate((float) (ball.x() - camera.x), (float) (ball.y() - camera.y), (float) (ball.z() - camera.z))
                        .scale(ball.radius());
                sphere.drawWithShader(MODEL, event.getProjectionMatrix(), GameRenderer.getPositionTexColorShader());
            }
        } finally {
            VertexBuffer.unbind();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (shader != null) RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    private static void ensureResources() {
        if (sphere != null) return;
        var textures = Minecraft.getInstance().getTextureManager();
        for (int stars = 1; stars <= 7; stars++) {
            var image = new NativeImage(TEX_W, TEX_H, false);
            for (int j = 0; j < TEX_H; j++) {
                double lat = Math.PI / 2 - (j + 0.5) / TEX_H * Math.PI;
                for (int i = 0; i < TEX_W; i++) {
                    double lon = (i + 0.5) / TEX_W * Math.PI * 2 - Math.PI;
                    double x = Math.cos(lat) * Math.sin(lon), y = Math.sin(lat), z = Math.cos(lat) * Math.cos(lon);
                    image.setPixelRGBA(i, j, DragonBallPlanetShape.inStar(stars, x, y, z) ? abgr(200, 20, 40) : abgr(245, 145, 32));
                }
            }
            TEXTURES[stars - 1] = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "dynamic/dragon_ball_planet_" + stars);
            textures.register(TEXTURES[stars - 1], new DynamicTexture(image));
        }
        // Light from upper front-left in ball space; ambient keeps the night side readable.
        double lx = -0.45, ly = 0.6, lz = 0.66, ln = Math.sqrt(lx * lx + ly * ly + lz * lz);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < LAT; j++)
            for (int i = 0; i < LON; i++) {
                int[][] corners = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
                for (int[] c : corners) {
                    double lat = Math.PI / 2 - (double) c[1] / LAT * Math.PI, lon = (double) c[0] / LON * Math.PI * 2 - Math.PI;
                    float x = (float) (Math.cos(lat) * Math.sin(lon)), y = (float) Math.sin(lat), z = (float) (Math.cos(lat) * Math.cos(lon));
                    double light = 0.45 + 0.55 * Math.max(0, (x * lx + y * ly + z * lz) / ln);
                    int g = (int) Math.round(255 * Math.min(1, light));
                    builder.addVertex(x, y, z).setUv((float) c[0] / LON, (float) c[1] / LAT).setColor(g, g, g, 255);
                }
            }
        var buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try { buffer.bind(); buffer.upload(builder.buildOrThrow()); sphere = buffer; }
        catch (RuntimeException | Error failure) { buffer.close(); throw failure; }
        finally { VertexBuffer.unbind(); }
    }

    private static int abgr(int r, int g, int b) { return 0xFF000000 | (b << 16) | (g << 8) | r; }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { snapshot = null; }
}
