package net.bullettrain.xenopixelsmod.compat.sable;

import com.dragonminez.common.compat.SableCompat;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Keeps Create {@code ContraptionCollider.collideEntities} from scanning a continent.
 *
 * <p>Sable remaps a contraption's plot-local AABB through the ship pose. A large or
 * tilted hull, or a double-transformed box, becomes a world query hundreds of blocks
 * on a side. {@code Level#getEntities} then walks every item, orb and mob in that
 * volume every tick — that is the lag on Sable contraptions, big and small. A deck
 * of small gantries does the same scan once per gadget.
 *
 * <p>Any Sable-mounted Create collider, or any query that has exploded / drifted,
 * is rebuilt around the (world-projected) entity. Item and XP collision is dropped
 * for those queries; living entities still collide. The ship itself still handles
 * them via Sable's sub-level collider.
 */
public final class SableContraptionCull {

    /** Modest pose errors count — a 4-block bearing can still fan out after a bad transform. */
    private static final double EXPLODE_RATIO = 2.0;
    private static final double MIN_EXPLODE_VOLUME = 4.0 * 4.0 * 4.0;
    /** Plot/world mix-up, not a 10-block assembly offset from its entity. */
    private static final double CENTER_SNAP_SQ = 32.0 * 32.0;
    private static final double MIN_HALF = 2.0;
    private static final double PAD = 2.0;

    public static volatile boolean debugHud = false;
    private static int createScans;
    private static int fanOutSkips;
    private static double maxQuerySide;
    private static long debugWindowStartMs;

    private SableContraptionCull() {
    }

    public static boolean shouldSkipSableFanOut(Level level, AABB box) {
        if (box == null || !cullEnabled(level)) return false;
        boolean skip = querySide(box) > queryCap();
        if (skip) noteFanOutSkip(box);
        return skip;
    }

    public static void noteCreateScan(AABB box) {
        if (!debugHud || box == null) return;
        createScans++;
        maxQuerySide = Math.max(maxQuerySide, querySide(box));
        maybeFlushDebug();
    }

    public static void noteFanOutSkip(AABB box) {
        if (!debugHud) return;
        fanOutSkips++;
        if (box != null) maxQuerySide = Math.max(maxQuerySide, querySide(box));
        maybeFlushDebug();
    }

    public static String debugSnapshot() {
        return "createScans=" + createScans
                + " fanOutSkip=" + fanOutSkips
                + " maxSide=" + (int) maxQuerySide
                + " cap=" + (int) queryCap()
                + " " + SableSectionRenderCull.debugCounts();
    }

    /** Replaces Sable's 500-block SAT abort so {@code cap³} is the walk limit. */
    public static double collisionBoundCap() {
        if (!XenoPerfConfig.perfEnabled) return 500.0;
        if (FMLEnvironment.dist.isClient() && !XenoClientConfig.sableContraptionCullClient) {
            return 500.0;
        }
        if (!FMLEnvironment.dist.isClient() && !XenoPerfConfig.sableContraptionCullEnabled) {
            return 500.0;
        }
        return Math.max(16.0, XenoPerfConfig.sableContraptionMaxQueryExtent);
    }

    public static AABB clampQuery(Entity contraption, AABB query) {
        if (query == null || contraption == null) return query;
        if (!cullEnabled(contraption)) {
            return query;
        }

        boolean onShip = onSableShip(contraption);
        AABB local = contraption.getBoundingBox();
        double queryVol = volume(query);
        double localVol = volume(local);
        boolean exploded = queryVol > localVol * EXPLODE_RATIO && queryVol > MIN_EXPLODE_VOLUME;
        Vec3 origin = worldOrigin(contraption);
        Vec3 queryCenter = center(query);
        boolean misplaced = origin.distanceToSqr(queryCenter) > CENTER_SNAP_SQ;

        // Ground Create keeps its own box unless it has clearly blown up.
        // Everything on a Sable ship is rebuilt so small gadgets do not inherit
        // a ship-sized or pose-exploded query.
        if (!onShip && !exploded && !misplaced) return query;

        double cap = Math.max(16.0, XenoPerfConfig.sableContraptionMaxQueryExtent);
        double hx = halfExtent(local.getXsize(), cap);
        double hy = halfExtent(local.getYsize(), cap * 0.75);
        double hz = halfExtent(local.getZsize(), cap);
        AABB tight = new AABB(origin.x - hx, origin.y - hy, origin.z - hz,
                origin.x + hx, origin.y + hy, origin.z + hz);
        // Never grow a query that was already smaller and correctly centred.
        if (!misplaced && queryVol <= volume(tight)) return query;
        return tight;
    }

    public static boolean skipLooseDebris(Entity contraption, Entity candidate, AABB query) {
        if (candidate == null || query == null) return false;
        if (!cullEnabled(contraption)) {
            return false;
        }
        if (!(candidate instanceof ItemEntity) && !(candidate instanceof ExperienceOrb)) {
            return false;
        }
        // Any Sable-mounted collider, any size: items/orbs never need this scan.
        // Off-ship, only skip when the query itself has blown up so a ground
        // piston still pushes nearby drops.
        return onSableShip(contraption) || volume(query) > MIN_EXPLODE_VOLUME;
    }

    /** Back-compat for callers that only have the query. */
    public static boolean skipLooseDebris(Entity candidate, AABB query) {
        return skipLooseDebris(null, candidate, query);
    }

    /**
     * Client FPS is this scan on the render/tick thread. {@code /xenoperf set sablecull}
     * only writes the logical server, so leaving that off must not reopen the exploded
     * query on the client. Opt out locally with {@code /xenoclient set sablecull false}.
     */
    private static boolean cullEnabled(Entity contraption) {
        return cullEnabled(contraption == null ? null : contraption.level());
    }

    private static boolean cullEnabled(Level level) {
        if (!XenoPerfConfig.perfEnabled) return false;
        if (level != null && level.isClientSide) {
            return XenoClientConfig.sableContraptionCullClient;
        }
        return XenoPerfConfig.sableContraptionCullEnabled;
    }

    private static double queryCap() {
        return Math.max(16.0, XenoPerfConfig.sableContraptionMaxQueryExtent);
    }

    private static double querySide(AABB box) {
        return Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
    }

    private static void maybeFlushDebug() {
        if (!FMLEnvironment.dist.isClient()) return;
        long now = System.currentTimeMillis();
        if (debugWindowStartMs == 0L) debugWindowStartMs = now;
        if (now - debugWindowStartMs < 1000L) return;
        SableCullClientDebug.flush(debugSnapshot());
        createScans = 0;
        fanOutSkips = 0;
        maxQuerySide = 0.0;
        SableSectionRenderCull.resetDebugCounts();
        debugWindowStartMs = now;
    }

    private static boolean onSableShip(Entity entity) {
        if (entity == null) return false;
        try {
            return SableCompat.isEntityInSubLevel(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double halfExtent(double fullSize, double cap) {
        double hx = Math.max(MIN_HALF, fullSize * 0.75 + PAD);
        return Math.min(hx, cap);
    }

    private static Vec3 worldOrigin(Entity contraption) {
        Vec3 pos = contraption.position();
        try {
            if (SableCompat.isEntityInSubLevel(contraption)) {
                return SableCompat.projectToWorld(contraption.level(), pos);
            }
        } catch (Throwable ignored) {
        }
        return pos;
    }

    private static Vec3 center(AABB box) {
        return new Vec3(
                (box.minX + box.maxX) * 0.5,
                (box.minY + box.maxY) * 0.5,
                (box.minZ + box.maxZ) * 0.5);
    }

    private static double volume(AABB box) {
        if (box == null) return 0.0;
        double x = Math.max(0.0, box.getXsize());
        double y = Math.max(0.0, box.getYsize());
        double z = Math.max(0.0, box.getZsize());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return Double.MAX_VALUE;
        return x * y * z;
    }
}
