package net.bullettrain.xenopixelsmod.space.physics;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceTickScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.joml.Vector3d;

import java.util.ArrayList;

/** Explicit admin-only experiment in the disposable orbit dimension, not production planet terrain. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PhysicalPlanetSpike implements SpaceTickScheduler.Pass {
    private static final String TAG = "xenopixelsmod_physical_planet_spike_v1";
    private static final ResourceLocation ORBIT = ResourceLocation.fromNamespaceAndPath("xenospace_spike", "orbit");
    private static final PhysicalPlanetSpike PASS = new PhysicalPlanetSpike();
    private final Vector3d impulse = new Vector3d();
    private final Vector3d gravity = new Vector3d();
    private final Vector3d pinPosition = new Vector3d();
    private final Vector3d pinLinear = new Vector3d();
    private final Vector3d pinAngular = new Vector3d();
    private final org.joml.Quaterniond pinOrientation = new org.joml.Quaterniond();
    private PhysicalPlanetSpike() {}
    private static boolean orbit(ServerLevel level) {
        return ORBIT.equals(level.dimension().location());
    }
    /**
     * Registered only when space is enabled. This pass drives an admin-only experiment, and its cost
     * was measured: at 50 charted bodies it is 5.27 us of the 9.54 us/tick the whole subsystem spends,
     * and it was spending all of it even with {@code enabled=false} (ai/evidence/space-perf-s5-2026-10-14).
     * {@code create}, {@code status} and {@code loadedBodyId} read the Sable body directly, so the spike
     * stays fully usable with the pass unregistered — only the scripted orbital impulse pauses.
     */
    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        if (XenoSpaceConfig.enabled()) SpaceTickScheduler.register(PASS);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SpaceTickScheduler.unregister(PASS); }

    private static ServerSubLevel find(ServerLevel level) {
        for (var body : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (body instanceof ServerSubLevel serverBody && serverBody.getUserDataTag() != null
                    && serverBody.getUserDataTag().getBoolean(TAG)) return serverBody;
        }
        return null;
    }
    public static java.util.UUID loadedBodyId(ServerLevel level) {
        if (!orbit(level)) return null;
        var body = find(level);
        return body == null || body.isRemoved() ? null : body.getUniqueId();
    }

    /**
     * Assemble a voxel sphere of the requested radius.
     *
     * <p>The size is the plan's §11 knob and it is deliberately <b>not</b> the chart's
     * {@code radius_blocks}: a charted Earth is 100 blocks of gravity math, and a dev body anyone can
     * stand on is a handful of voxels. When the operator omits the radius, a body already bound to this
     * dimension supplies it — clamped, so a 100-block chart row does not mean 17k blocks placed on the
     * server thread.
     *
     * @param requestedRadius voxels, or null to take the bound chart body's radius and then the ceiling
     */
    public static String create(ServerLevel level, Integer requestedRadius) {
        if (!orbit(level)) throw new IllegalStateException("Physics spike requires xenospace_spike:orbit.");
        if (find(level) != null) throw new IllegalStateException("One physics spike body already exists; inspect its status.");
        if (!SubLevelContainer.getContainer(level).getAllSubLevels().isEmpty())
            throw new IllegalStateException("Use an empty disposable orbit world; existing Sable bodies are present.");
        String source = requestedRadius != null ? "requested" : "defaulted";
        int radius = XenoSpaceConfig.spikeRadius(requestedRadius != null ? requestedRadius : chartRadius(level));
        BlockPos center = new BlockPos(0, 128, 0);
        // One voxel of slack on every side: the scan must see the air the hull will not occupy, and the
        // entity check must cover the same volume the assembly is about to place blocks in.
        int pad = radius + 1;
        AABB area = new AABB(center.getX() - pad, center.getY() - pad, center.getZ() - pad,
                center.getX() + pad + 1, center.getY() + pad + 1, center.getZ() + pad + 1);
        if (!level.getEntities(null, area).isEmpty()) throw new IllegalStateException("Move entities away from the assembly area at 0 128 0.");
        var blocks = new ArrayList<BlockPos>((2 * radius + 1) * (2 * radius + 1) * (2 * radius + 1));
        // Check the complete bounding volume before adding any blocks; never assemble owner terrain.
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++) {
            BlockPos pos = center.offset(x, y, z);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir())
                throw new IllegalStateException("Assembly area must be loaded and entirely air.");
            if (PrototypeOrbit.containsVoxel(x, y, z, radius)) blocks.add(pos);
        }
        for (var pos : blocks) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        // Exact Sable 2.0.3 API moves only the just-created, bounded set into its own plot.
        var body = SubLevelAssemblyHelper.assembleBlocks(level, center, blocks,
                new BoundingBox3i(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius)));
        if (body.getUserDataTag() == null) body.setUserDataTag(new net.minecraft.nbt.CompoundTag());
        body.getUserDataTag().putBoolean(TAG, true);
        body.getUserDataTag().putInt(TAG + "_radius", radius);
        // Remember the pose Sable assembled the plot at. Without this the body has nowhere to be pinned
        // back to, and the scripted orbital impulse walks it off the standing test before the test runs
        // (measured 2026-10-14: an r=8 body ended ~95 blocks below its creation point).
        var pose = body.logicalPose();
        var anchor = pose.position();
        var spin = pose.orientation();
        body.getUserDataTag().putDouble(TAG + "_ax", anchor.x());
        body.getUserDataTag().putDouble(TAG + "_ay", anchor.y());
        body.getUserDataTag().putDouble(TAG + "_az", anchor.z());
        body.getUserDataTag().putDouble(TAG + "_qw", spin.w());
        body.getUserDataTag().putDouble(TAG + "_qx", spin.x());
        body.getUserDataTag().putDouble(TAG + "_qy", spin.y());
        body.getUserDataTag().putDouble(TAG + "_qz", spin.z());
        body.setName("Xeno physical planet spike (" + blocks.size() + " voxels, r=" + radius + ")");
        return "Created real Sable spherical voxel body " + body.getUniqueId() + " of radius " + radius
                + " (" + blocks.size() + " voxels, " + source + "); its HD surface follows Sable replication.";
    }

    /**
     * Pin the spike body to its assembly pose, or release it to the scripted orbit again. The collider and
     * surface tests below need a body that stays where it was built; this is an admin-only experiment
     * fixture, not a production behaviour, and it is off unless someone asks for it.
     */
    public static String setHold(ServerLevel level) {
        if (!orbit(level)) return "Physics spike requires xenospace_spike:orbit.";
        var body = find(level);
        if (body == null || body.isRemoved()) return "No physical planet spike body loaded.";
        var tag = body.getUserDataTag();
        boolean held = !tag.getBoolean(TAG + "_hold");
        tag.putBoolean(TAG + "_hold", held);
        return held ? "Spike body " + body.getUniqueId() + " pinned at "
                + tag.getDouble(TAG + "_ax") + " " + tag.getDouble(TAG + "_ay") + " " + tag.getDouble(TAG + "_az")
                + " (top surface ~" + (tag.getDouble(TAG + "_ay") + tag.getInt(TAG + "_radius") + 1) + "); the orbital impulse is paused."
                : "Spike body " + body.getUniqueId() + " released; the scripted orbit resumes from its current velocity.";
    }

    /**
     * The chart radius of the body bound to this dimension, or the config ceiling when nothing is bound.
     * Reads the published chart through {@code SpaceChartService.state}, so it is server-thread only —
     * which is exactly where a command runs.
     */
    private static int chartRadius(ServerLevel level) {
        var chart = net.bullettrain.xenopixelsmod.space.SpaceChartService
                .state(level.getServer()).runtime.chart;
        int slot = chart.slotForDimension(level.dimension().location().toString());
        if (slot < 0) return XenoSpaceConfig.maxSpikeRadius();
        double radius = chart.body(slot).radiusBlocks();
        return Double.isFinite(radius) ? (int) Math.round(radius) : XenoSpaceConfig.maxSpikeRadius();
    }

    public static String status(ServerLevel level) {
        if (!orbit(level)) return "Physics spike requires xenospace_spike:orbit.";
        var body = find(level);
        if (body == null) return "No physical planet spike body loaded.";
        return "Sable body=" + body.getUniqueId() + " mass=" + body.getMassTracker().getMass()
                + " radius=" + body.getUserDataTag().getInt(TAG + "_radius") + " (voxels, chart radius is separate)"
                + " position=" + body.logicalPose().position() + " rotation=" + body.logicalPose().orientation()
                + " velocity=" + body.latestLinearVelocity + " seeded=" + body.getUserDataTag().getBoolean(TAG + "_seeded")
                + " held=" + body.getUserDataTag().getBoolean(TAG + "_hold");
    }

    @Override public String name() { return "physical-planet-spike"; }

    /**
     * The spike body exists only in the disposable orbit dimension, so this is the one system the pass
     * wants out of the nine the scheduler fans out to. Declining here rather than in {@code run} keeps
     * the pass due for the orbit system on every other system's invocation.
     */
    @Override public boolean appliesTo(SubLevelPhysicsSystem system) {
        ServerLevel level = system.getLevel();
        return level != null && orbit(level);
    }

    @Override public int run(SubLevelPhysicsSystem system, double seconds) {
        if (!XenoSpaceConfig.enabled()) return 0;
        var body = find(system.getLevel());
        if (body == null || body.isRemoved()) return 0;
        var handle = system.getPhysicsHandle(body);
        if (handle == null || !handle.isValid()) return 0;
        var tag = body.getUserDataTag();
        if (tag.getBoolean(TAG + "_hold")) {
            // The pinned fixture an operator asked for: back to the assembly pose, and this tick's motion
            // cancelled. Only the pose matters for the collider test, but leaving the velocity running would
            // fight the teleport every tick and jitter the client's interpolated pose.
            handle.teleport(pinPosition.set(tag.getDouble(TAG + "_ax"), tag.getDouble(TAG + "_ay"),
                    tag.getDouble(TAG + "_az")), pinOrientation.set(tag.getDouble(TAG + "_qw"),
                    tag.getDouble(TAG + "_qx"), tag.getDouble(TAG + "_qy"), tag.getDouble(TAG + "_qz")));
            handle.addLinearAndAngularVelocity(handle.getLinearVelocity(pinLinear).negate(),
                    handle.getAngularVelocity(pinAngular).negate());
            return 1;
        }
        double mass = body.getMassTracker().getMass(), step = PrototypeOrbit.step(seconds);
        if (!Double.isFinite(mass) || mass <= 0 || step == 0) return 0;
        if (!tag.getBoolean(TAG + "_seeded")) {
            // One initial tangential velocity and spin. Subsequent motion belongs entirely to Sable.
            handle.addLinearAndAngularVelocity(new Vector3d(0, 0, 2), new Vector3d(0, 0.15, 0));
            tag.putBoolean(TAG + "_seeded", true);
        }
        var pos = body.logicalPose().position();
        double dx = 32.5 - pos.x(), dy = 128.5 - pos.y(), dz = 0.5 - pos.z();
        double factor = PrototypeOrbit.accelerationFactor(dx, dy, dz);
        // The actual dimension gravity is data-driven; the legacy 10 m/s² constant is insufficient.
        DimensionPhysicsData.getGravity(system.getLevel(), pos, gravity);
        impulse.set(dx * factor - gravity.x, dy * factor - gravity.y, dz * factor - gravity.z).mul(mass * step);
        body.logicalPose().orientation().transformInverse(impulse);
        handle.applyLinearImpulse(impulse);
        return 1;
    }
}
