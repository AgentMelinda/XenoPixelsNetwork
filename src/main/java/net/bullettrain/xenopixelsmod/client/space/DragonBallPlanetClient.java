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
 * Poly preview of planet-sized Super Dragon Balls. The generated equirectangular texture for each star count is shared
 * with the production {@link PhysicalPlanetRenderer}; only this class's preview sphere mesh consumes the temporary
 * {@link DragonBallPlanetSnapshot}. The production renderer gets position and orientation from the replicated Sable body.
 */
@EventBusSubscriber(modid = "xenopixelsmod", value = Dist.CLIENT)
public final class DragonBallPlanetClient {
    private static final ResourceLocation VOIDSEA = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "voidsea");
    private static final int LON = 96, LAT = 48, TEX_W = 1024, TEX_H = 512;
    private static final int PROXY_TEX_W = 128, PROXY_TEX_H = 64;
    private static final Matrix4f MODEL = new Matrix4f();
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[7];
    private static final ResourceLocation[] PROXY_TEXTURES = new ResourceLocation[7];
    private static volatile DragonBallPlanetSnapshot snapshot;
    private static VertexBuffer sphere;

    private DragonBallPlanetClient() {}

    public static void apply(DragonBallPlanetSnapshot packet) { snapshot = packet; }

    /** Star counts of the benchmark spheres this client draws (bench poly only); empty when none. */
    static java.util.List<Integer> benchmarkStars() {
        DragonBallPlanetSnapshot snap = snapshot;
        if (snap == null) return java.util.List.of();
        var stars = new java.util.ArrayList<Integer>(snap.balls().size());
        for (var ball : snap.balls()) stars.add(ball.stars());
        return stars;
    }

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

    /** Gets the procedural star-count texture used by a production SDB body. Called on the render thread. */
    public static ResourceLocation textureForSlot(int slot) {
        if (slot < 1 || slot > TEXTURES.length) throw new IllegalArgumentException("SDB slot out of range: " + slot);
        return ensureTexture(slot, TEX_W, TEX_H, "dynamic/dragon_ball_planet_", TEXTURES);
    }

    /** Low-resolution texture for sub-pixel far proxies; generated before rendering when the snapshot arrives. */
    public static ResourceLocation projectionTextureForSlot(int slot) {
        if (slot < 1 || slot > PROXY_TEXTURES.length) throw new IllegalArgumentException("SDB slot out of range: " + slot);
        return ensureTexture(slot, PROXY_TEX_W, PROXY_TEX_H, "dynamic/dragon_ball_planet_proxy_", PROXY_TEXTURES);
    }

    /** Prepare the far texture while applying the packet, before a draw loop can request it. */
    public static void prewarmProjectionTextures(int[] slots) {
        for (int slot : slots) projectionTextureForSlot(slot);
    }

    /** These texture ids are registered as DynamicTextures here and must not be replaced by the generic surface loader. */
    public static boolean ownsTexture(ResourceLocation location) {
        return location != null && "xenopixelsmod".equals(location.getNamespace())
                && location.getPath().startsWith("dynamic/dragon_ball_planet_");
    }

    private static void ensureTextures() {
        for (int stars = 1; stars <= TEXTURES.length; stars++) textureForSlot(stars);
    }

    /** Generates one map and publishes its id only after TextureManager accepts the DynamicTexture. */
    private static ResourceLocation ensureTexture(int stars, int width, int height, String pathPrefix,
                                                  ResourceLocation[] cache) {
        if (cache[stars - 1] != null) return cache[stars - 1];
        var textures = Minecraft.getInstance().getTextureManager();
        var image = new NativeImage(width, height, false);
        for (int j = 0; j < height; j++) {
            double lat = Math.PI / 2 - (j + 0.5) / height * Math.PI;
            for (int i = 0; i < width; i++) {
                double lon = (i + 0.5) / width * Math.PI * 2 - Math.PI;
                double x = Math.cos(lat) * Math.sin(lon), y = Math.sin(lat), z = Math.cos(lat) * Math.cos(lon);
                image.setPixelRGBA(i, j, DragonBallPlanetShape.inStar(stars, x, y, z)
                        ? abgr(200, 20, 40) : abgr(245, 145, 32));
            }
        }
        var location = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", pathPrefix + stars);
        var texture = new DynamicTexture(image);
        try {
            textures.register(location, texture);
            cache[stars - 1] = location;
            return location;
        } catch (RuntimeException | Error failure) {
            texture.close();
            throw failure;
        }
    }

    private static void ensureResources() {
        ensureTextures();
        if (sphere != null) return;
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
