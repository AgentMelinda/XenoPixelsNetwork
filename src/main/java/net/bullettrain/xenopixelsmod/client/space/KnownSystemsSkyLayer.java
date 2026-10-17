package net.bullettrain.xenopixelsmod.client.space;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Galaxy Atlas slice 2: the known star systems drawn as coloured points in the Voidsea sky, each in its true direction
 * from the camera, so a system you can see in the Atlas is also a star you can fly towards. Systems are 100,000+ blocks
 * away, so only direction matters; at most 8,192 known systems enter the sky buffer. It is rebuilt only when the known
 * set changes or the camera has moved (in 3D) more than {@link #REBUILD_DISTANCE} blocks, never per frame. Sol's cell
 * is skipped (its Sun is a real body). Render thread only.
 */
final class KnownSystemsSkyLayer {
    private static final double REBUILD_DISTANCE = 2_000;
    /** A little inside the sky cube so the points draw over the background stars. */
    private static final float RADIUS = 60f;
    private static final float SIZE = 0.22f;

    private static VertexBuffer buffer;
    private static long builtSkyRevision = Long.MIN_VALUE;
    private static boolean emptyBuilt;
    private static double builtX = Double.NaN, builtY = Double.NaN, builtZ = Double.NaN;

    private KnownSystemsSkyLayer() {}

    static void draw(Matrix4f view, Matrix4f projection, Vec3 camera) {
        if (!UniverseClient.hasSnapshot()) { close(); return; }
        long skyRevision = UniverseClient.skyRevision();
        double moved = Math.sqrt((camera.x - builtX) * (camera.x - builtX) + (camera.y - builtY) * (camera.y - builtY)
                + (camera.z - builtZ) * (camera.z - builtZ));
        // NaN (first draw) compares false, so the null-buffer test covers it. 3D: directions use x, y and z (Codex review).
        if (builtSkyRevision != skyRevision || (buffer == null && !emptyBuilt) || moved > REBUILD_DISTANCE)
            rebuild(skyRevision, camera);
        if (buffer == null) return;
        buffer.bind();
        buffer.drawWithShader(view, projection, GameRenderer.getPositionColorShader());
    }

    private static void rebuild(long skyRevision, Vec3 camera) {
        close();
        builtSkyRevision = skyRevision; builtX = camera.x; builtY = camera.y; builtZ = camera.z;
        emptyBuilt = false;
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int quads = 0;
        for (var known : UniverseClient.skySystems()) {
            if (known.cell().home()) continue;
            var s = known.system();
            double dx = s.x() - camera.x, dy = s.y() - camera.y, dz = s.z() - camera.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1) continue;
            float x = (float) (dx / length), y = (float) (dy / length), z = (float) (dz / length);
            // Two axes perpendicular to the view direction (use world X when looking straight up/down).
            float ux = -z, uy = 0, uz = x;
            float ul = (float) Math.sqrt(ux * ux + uz * uz);
            if (ul < 1e-4f) { ux = 1; uy = 0; uz = 0; ul = 1; }
            ux /= ul; uz /= ul;
            float vx = y * uz - z * uy, vy = z * ux - x * uz, vz = x * uy - y * ux;
            float cx = x * RADIUS, cy = y * RADIUS, cz = z * RADIUS;
            int colour = s.starClass().colour;
            for (int corner = 0; corner < 4; corner++) {
                float a = (corner == 0 || corner == 3) ? -SIZE : SIZE, b = corner < 2 ? -SIZE : SIZE;
                builder.addVertex(cx + ux * a + vx * b, cy + uy * a + vy * b, cz + uz * a + vz * b).setColor(colour);
            }
            quads++;
        }
        if (quads == 0) { var empty = builder.build(); if (empty != null) empty.close(); emptyBuilt = true; return; }
        VertexBuffer uploaded = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try { uploaded.bind(); uploaded.upload(builder.buildOrThrow()); buffer = uploaded; }
        catch (RuntimeException | Error failure) { uploaded.close(); throw failure; }
        finally { VertexBuffer.unbind(); }
    }

    static void close() {
        if (buffer != null) { buffer.close(); buffer = null; }
        builtSkyRevision = Long.MIN_VALUE;
        emptyBuilt = false;
    }
}
