package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Far LOD for S11 small planets (owner 2026-10-16: "no support lod for small planets?"). The client recomputes the
 * same deterministic {@link SmallPlanetField} from bounded server-synced inputs (derived field seed, nearby chart
 * exclusions and asteroid belts; never the raw world seed) and draws every small planet within 8,192 blocks as a textured sphere: a texture generated from the
 * planet's own seed and profile colours, shading baked into the mesh, and a minimum apparent size so far ones read as
 * coloured dots. A planet whose physical Sable body is present (built by SmallPlanetPhysicalizer near players) is
 * skipped, so the real voxel body takes over seamlessly. LOD is drawn only while the server reports small-planet
 * field enabled, so client-visible candidates use the same chart and belt exclusions as server activation.
 */
@EventBusSubscriber(modid = "xenopixelsmod", value = Dist.CLIENT)
public final class SmallPlanetFarRenderer {
    private static final ResourceLocation VOIDSEA = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "voidsea");
    /** Display reach, not the physicalization range: a whole system must be visible from near its star. */
    private static final double RANGE = SmallPlanetField.MAX_VISUAL_RANGE;
    // Distant candidates are point-scale; 24x12 geometry keeps far-field GPU work low while textures carry profile detail.
    private static final int LON = 24, LAT = 12, TEX_W = 256, TEX_H = 128;
    private static final int MAX_TEXTURES = 9 * SmallPlanetField.MAX_PLANETS_PER_SYSTEM;
    private static final Matrix4f MODEL = new Matrix4f();
    private static final Map<SmallPlanetField.ObjectId, ResourceLocation> TEXTURES = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<SmallPlanetField.ObjectId, SmallPlanetField.OrbitingCandidate> ORBITS = new java.util.HashMap<>();
    private static final double[] FRAME_POSITION = new double[3];
    /** Texture each visible planet last drew with, for the on-demand identity check; written only on change. */
    private static final Map<SmallPlanetField.ObjectId, ResourceLocation> BOUND = new java.util.HashMap<>();
    private static List<SmallPlanetField.Candidate> visible = List.of();
    private static double lastX = Double.NaN, lastZ = Double.NaN, lastY;
    private static long lastSeed;
    private static long lastUniverseRevision = -1;
    private static int lastRevision = -1;
    private static boolean lastOrbitEnabled;
    private static boolean orbitWarningLogged;
    private static VertexBuffer sphere;
    private static int nextTextureId;

    private SmallPlanetFarRenderer() {}

    public static List<SmallPlanetField.Candidate> visible() { return visible; }

    /** Recompute the nearby field when the player moved 256 blocks or the seed changed (at most 9 systems x 8 slots). */
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        var snapshot = SmallPlanetFieldClient.current();
        if (mc.player == null || mc.level == null || snapshot == null || !snapshot.enabled() || !XenoClientConfig.smallPlanetLod
                || !VOIDSEA.equals(mc.level.dimension().location())) {
            visible = List.of();
            ORBITS.clear();
            lastX = Double.NaN;
            lastRevision = -1;
            lastUniverseRevision = -1;
            return;
        }
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        long universeRevision = UniverseClient.revision();
        if (SpaceOrbitClockClient.enabled()) {
            double seconds = SpaceOrbitClockClient.seconds(0.0f);
            if (!Double.isFinite(seconds)) {
                visible = List.of(); ORBITS.clear(); lastX = Double.NaN; lastOrbitEnabled = false; return;
            }
            try {
                boolean refresh = !lastOrbitEnabled || needsRescan(lastX, lastY, lastZ, lastSeed,
                        lastRevision, lastUniverseRevision, x, y, z, snapshot.fieldSeed(),
                        snapshot.revision(), universeRevision);
                if (refresh) {
                    var orbiting = SmallPlanetField.orbitingPotentiallyNearby(snapshot.fieldSeed(), x, y, z, RANGE,
                            SmallPlanetField.MAX_PLANETS_PER_SYSTEM, snapshot.chartedBodies(), snapshot.asteroidBelts(),
                            cell -> UniverseClient.systemAt(snapshot.fieldSeed(), cell),
                            snapshot.homeStarX(), snapshot.homeStarY(), snapshot.homeStarZ(), seconds);
                    ORBITS.clear();
                    for (var value : orbiting) ORBITS.put(value.anchor().id(), value);
                    lastX = x; lastY = y; lastZ = z; lastSeed = snapshot.fieldSeed();
                    lastRevision = snapshot.revision(); lastUniverseRevision = universeRevision;
                }
                var current = new java.util.ArrayList<SmallPlanetField.Candidate>(ORBITS.size());
                var ids = new java.util.HashSet<SmallPlanetField.ObjectId>();
                for (var value : ORBITS.values()) {
                    value.orbit().positionAt(seconds, FRAME_POSITION);
                    double dx = FRAME_POSITION[0] - x, dy = FRAME_POSITION[1] - y, dz = FRAME_POSITION[2] - z;
                    double reach = RANGE + value.anchor().radius();
                    if (dx * dx + dy * dy + dz * dz > reach * reach) continue;
                    current.add(value.anchor().atPosition(FRAME_POSITION[0], FRAME_POSITION[1], FRAME_POSITION[2]));
                    ids.add(value.anchor().id());
                }
                visible = List.copyOf(current);
                BOUND.keySet().retainAll(ids);
                lastOrbitEnabled = true;
                orbitWarningLogged = false;
            } catch (RuntimeException invalidOrbit) {
                visible = List.of(); ORBITS.clear(); lastX = Double.NaN; lastOrbitEnabled = false;
                if (!orbitWarningLogged) {
                    orbitWarningLogged = true;
                    XenoPixelsMod.LOGGER.warn("Could not evaluate generated small-planet orbit positions; far LOD hidden", invalidOrbit);
                }
            }
            return;
        }
        // A server toggle from analytical motion to stationary display invalidates the last sampled positions.
        if (!lastOrbitEnabled && !needsRescan(lastX, lastY, lastZ, lastSeed, lastRevision, lastUniverseRevision,
                x, y, z, snapshot.fieldSeed(), snapshot.revision(), universeRevision)) return;
        lastOrbitEnabled = false;
        orbitWarningLogged = false;
        lastX = x; lastY = y; lastZ = z; lastSeed = snapshot.fieldSeed(); lastRevision = snapshot.revision();
        lastUniverseRevision = universeRevision;
        try {
            visible = SmallPlanetField.visibleNearby(snapshot.fieldSeed(), x, y, z, RANGE, SmallPlanetField.MAX_PLANETS_PER_SYSTEM,
                    snapshot.chartedBodies(), snapshot.asteroidBelts(),
                    cell -> UniverseClient.systemAt(snapshot.fieldSeed(), cell));
            var ids = new java.util.HashSet<SmallPlanetField.ObjectId>();
            ORBITS.clear();
            for (var planet : visible) ids.add(planet.id());
            BOUND.keySet().retainAll(ids);
        } catch (RuntimeException outside) {
            visible = List.of();
        }
    }

    static boolean needsRescan(double lastX, double lastY, double lastZ, long lastSeed, int lastFieldRevision,
                               long lastUniverseRevision, double x, double y, double z, long seed,
                               int fieldRevision, long universeRevision) {
        if (Double.isNaN(lastX) || lastSeed != seed || lastFieldRevision != fieldRevision
                || lastUniverseRevision != universeRevision) return true;
        double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
        return dx * dx + dy * dy + dz * dz >= 256 * 256;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || IrisShaderState.packInUse()) return;
        var mc = Minecraft.getInstance();
        List<SmallPlanetField.Candidate> list = visible;
        if (mc.level == null || list.isEmpty() || !VOIDSEA.equals(mc.level.dimension().location())) return;
        var camera = event.getCamera().getPosition();
        boolean orbitMode = SpaceOrbitClockClient.enabled();
        double orbitSeconds = orbitMode ? SpaceOrbitClockClient.seconds(event.getPartialTick().getGameTimeDeltaPartialTick(false)) : Double.NaN;
        if (orbitMode && !Double.isFinite(orbitSeconds)) return;
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean clamp = GL11.glIsEnabled(GL32.GL_DEPTH_CLAMP);
        var shader = RenderSystem.getShader();
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        var container = SubLevelContainer.getContainer(mc.level);
        try {
            ensureMesh();
            FogRenderer.setupNoFog();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            GL11.glEnable(GL32.GL_DEPTH_CLAMP);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            sphere.bind();
            for (var planet : list) {
                var orbiting = ORBITS.get(planet.id());
                if (orbiting != null && Double.isFinite(orbitSeconds)) orbiting.orbit().positionAt(orbitSeconds, FRAME_POSITION);
                else { FRAME_POSITION[0] = planet.x(); FRAME_POSITION[1] = planet.y(); FRAME_POSITION[2] = planet.z(); }
                if (container != null && physicalPresent(container, planet.id().bodyId(), FRAME_POSITION[0], FRAME_POSITION[1], FRAME_POSITION[2])) continue;
                double dx = FRAME_POSITION[0] - camera.x, dy = FRAME_POSITION[1] - camera.y, dz = FRAME_POSITION[2] - camera.z;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                // Never smaller than a few pixels: far planets read as coloured dots, near ones at true size.
                float radius = (float) Math.max(planet.radius(), distance * 0.004);
                var texture = texture(planet);
                RenderSystem.setShaderTexture(0, texture);
                if (!texture.equals(BOUND.get(planet.id()))) BOUND.put(planet.id(), texture);
                MODEL.set(event.getModelViewMatrix()).translate((float) dx, (float) dy, (float) dz).scale(radius);
                sphere.drawWithShader(MODEL, event.getProjectionMatrix(), GameRenderer.getPositionTexColorShader());
            }
        } finally {
            com.mojang.blaze3d.vertex.VertexBuffer.unbind();
            if (!clamp) GL11.glDisable(GL32.GL_DEPTH_CLAMP);
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (shader != null) RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderFogStart(fogStart); RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    /** Seeded centre of a candidate in the last scan, or null. */
    static double[] position(String bodyId) {
        for (var planet : visible) if (planet.id().bodyId().equals(bodyId)) return new double[]{planet.x(), planet.y(), planet.z()};
        return null;
    }

    /** Visible candidates by body id with their last bound texture (null if not drawn), for the identity check. */
    static List<PlanetTextureChecks.Observed> observed() {
        var list = new java.util.ArrayList<PlanetTextureChecks.Observed>(visible.size());
        for (var planet : visible) list.add(new PlanetTextureChecks.Observed(planet.id().bodyId(), BOUND.get(planet.id())));
        return list;
    }

    /**
     * Each visible candidate's own generated texture by body id, or absent if none was generated. Copies the
     * cache rather than calling get(), which would reorder the access-ordered LRU.
     */
    static Map<String, ResourceLocation> generated() {
        var copy = new java.util.HashMap<>(TEXTURES);
        var byBody = new java.util.HashMap<String, ResourceLocation>();
        for (var planet : visible) {
            var texture = copy.get(planet.id());
            if (texture != null) byBody.put(planet.id().bodyId(), texture);
        }
        return byBody;
    }

    /**
     * Whether render() would draw this planet's far globe on the next frame, or why not, from the same gates
     * render() applies. The candidate list itself only refreshes on the client tick, so a planet missing from
     * the last scan is reported as such rather than as drawn or hidden.
     */
    static String drawState(String bodyId) {
        var mc = Minecraft.getInstance();
        if (!XenoClientConfig.smallPlanetLod) return "suppressed: small-planet LOD off";
        if (mc.level == null || !VOIDSEA.equals(mc.level.dimension().location())) return "suppressed: not in the Voidsea";
        if (IrisShaderState.packInUse()) return "suppressed: shader pack in use";
        var container = SubLevelContainer.getContainer(mc.level);
        for (var planet : visible) {
            if (!planet.id().bodyId().equals(bodyId)) continue;
            if (container != null && physicalPresent(container, planet.id().bodyId(), planet.x(), planet.y(), planet.z()))
                return "suppressed: physical body drawn instead";
            return "far globe drawable next frame";
        }
        return "not in the last candidate scan";
    }

    /** True when some client Sable body's bounds contain the planet centre (the voxel body is drawn instead). */
    private static boolean physicalPresent(SubLevelContainer container, String bodyId, double x, double y, double z) {
        // A physical small planet can move off its seeded position after it is spawned. Its exact body-id/UUID
        // binding remains authoritative, while the proximity check below remains a fallback before that binding
        // has arrived (or for an untagged body), so the stale seeded globe is not drawn beside its moved incarnation.
        if (PhysicalPlanetRenderer.hasLiveBodyBinding(bodyId, container)) return true;
        for (var body : container.getAllSubLevels()) {
            var box = body.boundingBox();
            if (box == null || body.isRemoved()) continue;
            if (x >= box.minX() && x <= box.maxX() && y >= box.minY() && y <= box.maxY()
                    && z >= box.minZ() && z <= box.maxZ()) return true;
        }
        return false;
    }

    /** Per-planet texture from its seed: profile surface/shadow/accent colours mixed by smooth value noise. */
    private static ResourceLocation texture(SmallPlanetField.Candidate planet) {
        var cached = TEXTURES.get(planet.id());
        if (cached != null) return cached;
        var profile = planet.profile();
        var image = new NativeImage(TEX_W, TEX_H, false);
        var random = new SplittableRandom(planet.seed() ^ 0x5DEECE66DL);
        float[][] grid = new float[17][9];
        for (float[] row : grid) for (int j = 0; j < row.length; j++) row[j] = (float) random.nextDouble();
        float[][] fine = new float[33][17];
        for (float[] row : fine) for (int j = 0; j < row.length; j++) row[j] = (float) random.nextDouble();
        for (int j = 0; j < TEX_H; j++)
            for (int i = 0; i < TEX_W; i++) {
                float u = (float) i / TEX_W, v = (float) j / TEX_H;
                float n = 0.65f * noise(grid, u * 16, v * 8, 16) + 0.35f * noise(fine, u * 32, v * 16, 32);
                int colour = n < 0.42f ? mix(profile.shadowArgb, profile.surfaceArgb, n / 0.42f)
                        : n < 0.78f ? profile.surfaceArgb : mix(profile.surfaceArgb, profile.accentArgb, (n - 0.78f) / 0.22f);
                image.setPixelRGBA(i, j, toAbgr(colour));
            }
        var location = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "dynamic/small_planet_" + nextTextureId++);
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        TEXTURES.put(planet.id(), location);
        if (TEXTURES.size() > MAX_TEXTURES) {
            var eldest = TEXTURES.entrySet().iterator().next();
            Minecraft.getInstance().getTextureManager().release(eldest.getValue());
            TEXTURES.remove(eldest.getKey());
            BOUND.remove(eldest.getKey());
        }
        return location;
    }

    /** Bilinear value noise on a grid that wraps in u (longitude). */
    private static float noise(float[][] grid, float x, float y, int wrap) {
        int x0 = (int) Math.floor(x), y0 = Math.min((int) Math.floor(y), grid[0].length - 2);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        int xa = Math.floorMod(x0, wrap), xb = Math.floorMod(x0 + 1, wrap);
        float a = grid[xa][y0] + (grid[xb][y0] - grid[xa][y0]) * fx;
        float b = grid[xa][y0 + 1] + (grid[xb][y0 + 1] - grid[xa][y0 + 1]) * fx;
        return a + (b - a) * fy;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int toAbgr(int argb) {
        return 0xFF000000 | ((argb & 255) << 16) | (argb & 0xFF00) | ((argb >> 16) & 255);
    }

    private static void ensureMesh() {
        if (sphere != null) return;
        double lx = -0.45, ly = 0.6, lz = 0.66, ln = Math.sqrt(lx * lx + ly * ly + lz * lz);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int j = 0; j < LAT; j++)
            for (int i = 0; i < LON; i++) {
                int[][] corners = {{i, j}, {i, j + 1}, {i + 1, j + 1}, {i + 1, j}};
                for (int[] c : corners) {
                    double lat = Math.PI / 2 - (double) c[1] / LAT * Math.PI, lon = (double) c[0] / LON * Math.PI * 2 - Math.PI;
                    float x = (float) (Math.cos(lat) * Math.sin(lon)), y = (float) Math.sin(lat), z = (float) (Math.cos(lat) * Math.cos(lon));
                    double light = 0.35 + 0.65 * Math.max(0, (x * lx + y * ly + z * lz) / ln);
                    int g = (int) Math.round(255 * Math.min(1, light));
                    builder.addVertex(x, y, z).setUv((float) c[0] / LON, (float) c[1] / LAT).setColor(g, g, g, 255);
                }
            }
        var buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try { buffer.bind(); buffer.upload(builder.buildOrThrow()); sphere = buffer; }
        catch (RuntimeException | Error failure) { buffer.close(); throw failure; }
        finally { VertexBuffer.unbind(); }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        visible = List.of();
        ORBITS.clear();
        lastX = Double.NaN;
        lastOrbitEnabled = false;
        orbitWarningLogged = false;
        lastUniverseRevision = -1;
        var textures = Minecraft.getInstance().getTextureManager();
        TEXTURES.values().forEach(textures::release);
        TEXTURES.clear();
        BOUND.clear();
    }
}
