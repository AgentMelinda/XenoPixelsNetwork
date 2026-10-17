package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/** Far asteroid visualization. The field is computed from its compact snapshot; no per-rock packets or entities. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID, value = Dist.CLIENT)
public final class AsteroidFarRenderer {
    private static final ResourceLocation VOIDSEA = net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.HOST;
    private static final double DRAW_RANGE = 2_048.0;
    private static final int MAX_ROCKS = AsteroidRenderPlan.MAX_VISIBLE;
    private static final int CELL = AsteroidField.CELL_SIZE;
    private static final Matrix4f MODEL = new Matrix4f();

    private record Key(int revision, long seed, List<AsteroidField.Belt> belts,
                       Set<AsteroidField.ObjectId> present, long cellX, long cellY, long cellZ) {}
    private record Mesh(long token, Key key, float[] vertices, int vertexCount,
                        double originX, double originY, double originZ, int asteroids, Throwable error) {}

    private static Key requestedKey;
    private static Key activeKey;
    private static long generation;
    private static long inFlightToken;
    private static volatile Mesh completed;
    private static VertexBuffer buffer;
    private static int activeAsteroids;
    private static final java.util.Set<String> REPORTED = new java.util.HashSet<>();

    private AsteroidFarRenderer() {}

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        boolean pack = IrisShaderState.packInUse();
        RenderLevelStageEvent.Stage wanted = pack
                ? RenderLevelStageEvent.Stage.AFTER_LEVEL : RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (event.getStage() != wanted || (pack && IrisShaderState.renderingShadows())) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        if (!VOIDSEA.equals(mc.level.dimension().location())) { clear(); return; }
        AsteroidFieldSnapshot snapshot = AsteroidFieldClient.current();
        if (snapshot == null || snapshot.belts().isEmpty()
                || !SpacePlanetMode.byName(XenoClientConfig.spacePlanetMode).drawsBodies()) {
            clear();
            return;
        }

        var camera = event.getCamera().getPosition();
        long cellX = floorCell(camera.x), cellY = floorCell(camera.y), cellZ = floorCell(camera.z);
        Set<AsteroidField.ObjectId> present = AsteroidFieldClient.present();
        Key wantedKey = new Key(snapshot.revision(), snapshot.fieldSeed(), snapshot.belts(), present,
                cellX, cellY, cellZ);
        if (!wantedKey.equals(requestedKey)) {
            requestedKey = wantedKey;
            generation++;
        }

        adoptCompleted();
        if (activeKey == null || !activeKey.equals(requestedKey)) startBuild(snapshot, present, camera.x, camera.y, camera.z, requestedKey);
        if (buffer == null || activeKey == null || !activeKey.equals(requestedKey)) return;

        if (pack) mc.getMainRenderTarget().bindWrite(false);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        var shader = RenderSystem.getShader();
        var shaderColor = RenderSystem.getShaderColor();
        float r = shaderColor[0], g = shaderColor[1], b = shaderColor[2], a = shaderColor[3];
        try {
            FogRenderer.setupNoFog();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            MODEL.set(event.getModelViewMatrix()).translate((float) (activeOriginX - camera.x),
                    (float) (activeOriginY - camera.y), (float) (activeOriginZ - camera.z));
            buffer.bind();
            buffer.drawWithShader(MODEL, event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
            if (REPORTED.add("field:" + activeKey.revision()))
                XenoPixelsMod.LOGGER.info("Far asteroid field rendered: revision={}, rocks={}, range={}, cap={}",
                        activeKey.revision(), activeAsteroids, DRAW_RANGE, MAX_ROCKS);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.depthMask(write);
            RenderSystem.depthFunc(depthFunction);
            GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(r, g, b, a);
            if (shader != null) RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    private static double activeOriginX, activeOriginY, activeOriginZ;

    private static long floorCell(double coordinate) { return (long) Math.floor(coordinate / CELL); }

    private static void startBuild(AsteroidFieldSnapshot snapshot, Set<AsteroidField.ObjectId> present,
                                   double cameraX, double cameraY, double cameraZ, Key key) {
        if (inFlightToken != 0) return;
        long token = generation;
        inFlightToken = token;
        CompletableFuture.supplyAsync(() -> build(token, key, snapshot, present, cameraX, cameraY, cameraZ),
                        ForkJoinPool.commonPool())
                .whenComplete((mesh, error) -> completed = error == null ? mesh
                        : new Mesh(token, key, new float[0], 0, 0, 0, 0, 0, error));
    }

    private static Mesh build(long token, Key key, AsteroidFieldSnapshot snapshot,
                              Set<AsteroidField.ObjectId> present, double cameraX, double cameraY, double cameraZ) {
        var candidates = AsteroidRenderPlan.visible(snapshot, present, cameraX, cameraY, cameraZ, DRAW_RANGE, MAX_ROCKS);
        double originX = key.cellX * (double) CELL, originY = key.cellY * (double) CELL, originZ = key.cellZ * (double) CELL;
        float[] vertices = new float[candidates.size() * 20 * 3 * 7];
        int offset = 0;
        for (AsteroidField.Candidate candidate : candidates)
            offset = appendRock(vertices, offset, candidate, originX, originY, originZ, cameraX, cameraY, cameraZ);
        return new Mesh(token, key, java.util.Arrays.copyOf(vertices, offset), offset / 7,
                originX, originY, originZ, candidates.size(), null);
    }

    private static void adoptCompleted() {
        Mesh mesh = completed;
        if (mesh == null) return;
        completed = null;
        inFlightToken = 0;
        if (mesh.token != generation || !mesh.key.equals(requestedKey)) return;
        if (mesh.error != null) {
            activeKey = mesh.key;
            activeAsteroids = 0;
            closeBuffer();
            if (REPORTED.add("error:" + mesh.key.revision()))
                XenoPixelsMod.LOGGER.error("Far asteroid field build failed for revision {}", mesh.key.revision(), mesh.error);
            return;
        }

        if (mesh.vertexCount == 0) closeBuffer();
        else {
            var builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < mesh.vertices.length; i += 7)
                builder.addVertex(mesh.vertices[i], mesh.vertices[i + 1], mesh.vertices[i + 2])
                        .setColor((int) mesh.vertices[i + 3], (int) mesh.vertices[i + 4],
                                (int) mesh.vertices[i + 5], (int) mesh.vertices[i + 6]);
            var data = builder.buildOrThrow();
            closeBuffer();
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            try { buffer.bind(); buffer.upload(data); }
            catch (RuntimeException | Error failure) { closeBuffer(); throw failure; }
            finally { VertexBuffer.unbind(); }
        }
        activeKey = mesh.key;
        activeAsteroids = mesh.asteroids;
        activeOriginX = mesh.originX;
        activeOriginY = mesh.originY;
        activeOriginZ = mesh.originZ;
    }

    private static int appendRock(float[] out, int offset, AsteroidField.Candidate candidate,
                                  double originX, double originY, double originZ,
                                  double cameraX, double cameraY, double cameraZ) {
        double[][] base = ICO_VERTICES;
        double[] jitter = new double[base.length];
        var random = new java.util.SplittableRandom(candidate.seed());
        for (int i = 0; i < jitter.length; i++) jitter[i] = 0.78 + random.nextDouble() * 0.44;
        double scale = candidate.radius();
        double[] cx = new double[base.length], cy = new double[base.length], cz = new double[base.length];
        for (int i = 0; i < base.length; i++) {
            cx[i] = (candidate.x() - originX) + base[i][0] * jitter[i] * scale;
            cy[i] = (candidate.y() - originY) + base[i][1] * jitter[i] * scale;
            cz[i] = (candidate.z() - originZ) + base[i][2] * jitter[i] * scale;
        }
        double dx = candidate.x() - cameraX, dy = candidate.y() - cameraY, dz = candidate.z() - cameraZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int alpha = (int) Math.max(18, Math.min(255, 255 * (DRAW_RANGE - distance) / 512.0));
        for (int face = 0; face < ICO_FACES.length; face++) {
            int ia = ICO_FACES[face][0], ib = ICO_FACES[face][1], ic = ICO_FACES[face][2];
            double abx = cx[ib] - cx[ia], aby = cy[ib] - cy[ia], abz = cz[ib] - cz[ia];
            double acx = cx[ic] - cx[ia], acy = cy[ic] - cy[ia], acz = cz[ic] - cz[ia];
            double nx = aby * acz - abz * acy, ny = abz * acx - abx * acz, nz = abx * acy - aby * acx;
            double length = Math.max(1e-9, Math.sqrt(nx * nx + ny * ny + nz * nz));
            double light = Math.max(0.0, (nx * -0.35 + ny * 0.82 + nz * -0.45) / length);
            double variation = 0.86 + ((mix64(candidate.seed() + face) >>> 11) & 255) / 255.0 * 0.14;
            double shade = (0.38 + 0.62 * light) * variation;
            int red = color(candidate.icy() ? 148 : 111, shade);
            int green = color(candidate.icy() ? 183 : 105, shade);
            int blue = color(candidate.icy() ? 210 : 96, shade);
            int[] indices = {ia, ib, ic};
            for (int index : indices) {
                out[offset++] = (float) cx[index]; out[offset++] = (float) cy[index]; out[offset++] = (float) cz[index];
                out[offset++] = red; out[offset++] = green; out[offset++] = blue; out[offset++] = alpha;
            }
        }
        return offset;
    }

    private static int color(int base, double shade) { return (int) Math.max(0, Math.min(255, base * shade)); }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static final double[][] ICO_VERTICES = icosahedronVertices();
    private static double[][] icosahedronVertices() {
        double t = (1.0 + Math.sqrt(5.0)) / 2.0;
        double[][] values = {{-1,t,0},{1,t,0},{-1,-t,0},{1,-t,0},{0,-1,t},{0,1,t},{0,-1,-t},{0,1,-t},{t,0,-1},{t,0,1},{-t,0,-1},{-t,0,1}};
        for (double[] v : values) { double n = Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]); v[0]/=n; v[1]/=n; v[2]/=n; }
        return values;
    }
    private static final int[][] ICO_FACES = {
            {0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},{1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},
            {3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},{4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1}
    };

    private static void clear() {
        if (requestedKey == null && buffer == null) return;
        requestedKey = null;
        activeKey = null;
        generation++;
        activeAsteroids = 0;
        closeBuffer();
    }

    private static void closeBuffer() {
        if (buffer != null) { buffer.close(); buffer = null; }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
