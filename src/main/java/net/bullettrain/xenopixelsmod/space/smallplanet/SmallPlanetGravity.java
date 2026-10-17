package net.bullettrain.xenopixelsmod.space.smallplanet;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.PhysicalPlanetBindings;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector4d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative and client-predicted radial gravity for nearby walkable small planets. */
public final class SmallPlanetGravity {
    private static volatile Map<UUID, Integer> clientBodies = Map.of();
    private static volatile boolean clientEnabled;
    private static volatile long clientRevision;
    private static final ThreadLocal<Samples> SAMPLES = ThreadLocal.withInitial(Samples::new);
    private static final ThreadLocal<Vector3d> CENTER = ThreadLocal.withInitial(Vector3d::new);

    private static final class Sample {
        boolean computed;
        boolean forceApplied;
        final Vector4d acceleration = new Vector4d();
    }

    private static final class Samples {
        Level level;
        long gameTime = Long.MIN_VALUE;
        boolean enabled;
        long clientRevision = Long.MIN_VALUE;
        final Map<Entity, Sample> byEntity = new java.util.IdentityHashMap<>();
    }

    private SmallPlanetGravity() {}

    /** Called by the client packet handler with a complete replacement snapshot. */
    public static void bindClient(PhysicalPlanetBindings packet) {
        Map<UUID, Integer> next = new HashMap<>();
        for (PhysicalPlanetBindings.Entry entry : packet.entries())
            if (entry.radialGravityRadius() >= SmallPlanetField.MIN_RADIUS)
                next.put(entry.uuid(), entry.radialGravityRadius());
        clientBodies = Map.copyOf(next);
        clientEnabled = packet.radialGravityEnabled();
        clientRevision++;
    }

    public static void clearClient() {
        clientBodies = Map.of();
        clientEnabled = false;
        clientRevision++;
    }

    static Map<UUID, Integer> clientBindings() { return clientBodies; }
    static boolean isClientEnabled() { return clientEnabled; }

    /** Adds the cached inward force at the start of player travel; vanilla Y gravity is suppressed separately. */
    public static boolean apply(Entity entity) {
        if (!eligible(entity)) return false;
        Sample sample = sample(entity);
        if (!(sample.acceleration.w > 0.0) || sample.forceApplied) return false;
        sample.forceApplied = true;
        var acceleration = sample.acceleration;
        var velocity = entity.getDeltaMovement();
        entity.setDeltaMovement(velocity.add(acceleration.x, acceleration.y, acceleration.z));
        return true;
    }

    /** Keeps total gravity continuous by blending world-down and radial acceleration with the same field weight. */
    public static double vanillaGravityScale(Entity entity) {
        return eligible(entity) ? SmallPlanetRadialGravity.vanillaComplement(sample(entity).acceleration.w) : 1.0;
    }

    private static boolean eligible(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        return !entity.isNoGravity() && !entity.isPassenger() && !player.isSpectator()
                && !player.getAbilities().flying && !player.isFallFlying()
                && !player.isInWater() && !player.isInLava()
                && PhysicalEarth.HOST.equals(entity.level().dimension().location());
    }

    private static Sample sample(Entity entity) {
        Samples samples = SAMPLES.get();
        Level level = entity.level();
        long now = level.getGameTime();
        boolean client = level.isClientSide();
        boolean enabled = client ? clientEnabled : XenoSpaceConfig.smallPlanetGravityEnabled();
        long revision = client ? clientRevision : 0L;
        if (samples.level != level || samples.gameTime != now || samples.enabled != enabled
                || samples.clientRevision != revision) {
            samples.byEntity.clear();
            samples.level = level;
            samples.gameTime = now;
            samples.enabled = enabled;
            samples.clientRevision = revision;
        }
        Sample cached = samples.byEntity.get(entity);
        if (cached != null && cached.computed) return cached;
        if (cached == null) {
            cached = new Sample();
            samples.byEntity.put(entity, cached);
        }
        // Publish before getGravity() is read below. The mixin re-enters sample() and must see weight 0,
        // preserving vanilla gravity as the unsuppressed magnitude used to construct this field.
        cached.computed = true;
        cached.forceApplied = false;
        cached.acceleration.zero();

        if (!enabled) return cached;
        if (client) clientAcceleration(entity, cached.acceleration);
        else if (entity.level() instanceof ServerLevel serverLevel)
            SmallPlanetPhysicalizer.radialAcceleration(serverLevel, (Player) entity, cached.acceleration);
        return cached;
    }

    private static void clientAcceleration(Entity player, Vector4d output) {
        var container = SubLevelContainer.getContainer(player.level());
        double px = player.getX(), py = player.getY() + player.getBbHeight() * 0.5, pz = player.getZ();
        double nearest = Double.POSITIVE_INFINITY;
        for (var entry : clientBodies.entrySet()) {
            SubLevel body = container.getSubLevel(entry.getKey());
            if (body == null || body.isRemoved()) continue;
            int radius = entry.getValue();
            var box = body.getPlot().getBoundingBox();
            if (box == null) continue;
            Vector3d center = body.logicalPose().transformPosition(CENTER.get().set(
                    (box.minX() + box.maxX() + 1) * 0.5,
                    (box.minY() + box.maxY() + 1) * 0.5,
                    (box.minZ() + box.maxZ() + 1) * 0.5));
            double dx = center.x - px, dy = center.y - py, dz = center.z - pz;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared >= nearest || distanceSquared >= 4.0 * radius * radius) continue;
            if (SmallPlanetRadialGravity.accelerationAt(px, py, pz, center.x, center.y, center.z,
                    radius, player.getGravity(), output)) {
                nearest = distanceSquared;
            }
        }
    }
}
