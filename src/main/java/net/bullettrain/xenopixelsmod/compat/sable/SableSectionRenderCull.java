package net.bullettrain.xenopixelsmod.compat.sable;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Client frustum/distance cull for Sable's vanilla/Sodium ship renderer.
 *
 * <p>Sable 2.0.3 {@code VanillaSubLevelRenderDispatcher.updateCulling} is a TODO.
 * {@code renderChunkedSubLevel} then draws {@code allRenderSections} of
 * {@code getAllSubLevels()} every layer, every frame. Fancy already sphere-tests
 * each section ({@code FancySubLevelOcclusionData}, radius 14). This is that
 * test for the Sodium reach-around / vanilla path.
 */
public final class SableSectionRenderCull {

    /** Same radius Fancy uses for {@code CullFrustum.testSphere}. */
    private static final float SECTION_RADIUS = 14.0F;
    private static final double ALWAYS_DRAW = 24.0;

    private static ClientSubLevel current;
    private static double camX;
    private static double camY;
    private static double camZ;
    private static double lookX;
    private static double lookY;
    private static double lookZ;
    private static double maxDistSq;
    private static double coneCos;
    private static boolean active;

    static int drawn;
    static int skipped;

    private SableSectionRenderCull() {
    }

    public static void begin(ClientSubLevel subLevel) {
        current = subLevel;
        if (subLevel == null || !enabled()) {
            active = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        camX = camera.getPosition().x;
        camY = camera.getPosition().y;
        camZ = camera.getPosition().z;
        Vector3f look = camera.getLookVector();
        lookX = look.x();
        lookY = look.y();
        lookZ = look.z();
        int rd = Math.max(2, mc.options.getEffectiveRenderDistance());
        double maxDist = rd * 16.0 + 32.0;
        maxDistSq = maxDist * maxDist;
        double fov = mc.options.fov().get();
        coneCos = Math.cos(Math.toRadians(fov * 0.5 + 25.0));
        active = true;
    }

    public static void end() {
        current = null;
        active = false;
    }

    public static boolean skip(SectionRenderDispatcher.RenderSection section) {
        if (!active || current == null || section == null) {
            return false;
        }
        BlockPos origin = section.getOrigin();
        Pose3dc pose = current.renderPose();
        Vector3d world = pose.transformPosition(
                new Vector3d(origin.getX() + 8.0, origin.getY() + 8.0, origin.getZ() + 8.0),
                new Vector3d());
        double dx = world.x - camX;
        double dy = world.y - camY;
        double dz = world.z - camZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > maxDistSq) {
            skipped++;
            return true;
        }
        if (distSq > ALWAYS_DRAW * ALWAYS_DRAW) {
            double dist = Math.sqrt(distSq);
            double dot = (dx * lookX + dy * lookY + dz * lookZ) / dist;
            // Extra slack so a 16³ section at the cone edge is not popped.
            double slack = SECTION_RADIUS / dist;
            if (dot < coneCos - slack) {
                skipped++;
                return true;
            }
        }
        drawn++;
        return false;
    }

    public static String debugCounts() {
        return "secDraw=" + drawn + " secSkip=" + skipped;
    }

    public static void resetDebugCounts() {
        drawn = 0;
        skipped = 0;
    }

    private static boolean enabled() {
        return XenoPerfConfig.perfEnabled && XenoClientConfig.sableContraptionCullClient;
    }
}
