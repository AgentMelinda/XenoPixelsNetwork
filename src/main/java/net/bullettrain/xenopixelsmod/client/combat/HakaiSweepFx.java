package net.bullettrain.xenopixelsmod.client.combat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.space.IrisShaderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;

/**
 * The cut edge of the Hakai sweep on entities: a pulsing violet rim and a dark violet cross-section disc at the height
 * where {@link HakaiClipMultiBufferSource} is cutting the body, so the opening reads as erased, not hollow. Same look
 * as the planet erasure (PlanetHakaiClient). Only in {@code hakaiEraseStyle = sweep}.
 */
@EventBusSubscriber(modid = "xenopixelsmod", value = Dist.CLIENT)
public final class HakaiSweepFx {
    private static final int SEGMENTS = 32;
    /** Keep the additive rim on the erased side of the clipping plane to avoid coplanar depth flicker. */
    private static final float RING_CUT_BIAS = 0.12f;
    /** Separate frame-reused native buffers prevent Hakai meshes and other AFTER_ENTITIES handlers sharing vertex bytes. */
    private static ByteBufferBuilder rimAllocator, capAllocator;
    private static final int MESH_BUFFER_BYTES = 786432;

    private HakaiSweepFx() {}

    public static boolean enabled() { return "sweep".equals(XenoClientConfig.hakaiEraseStyle)
            || "impact".equals(XenoClientConfig.hakaiEraseStyle); }
    public static boolean impactMode() { return "impact".equals(XenoClientConfig.hakaiEraseStyle); }

    private static BufferBuilder beginRim() {
        if (rimAllocator == null) rimAllocator = new ByteBufferBuilder(MESH_BUFFER_BYTES);
        return new BufferBuilder(rimAllocator, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    private static BufferBuilder beginCaps() {
        if (capAllocator == null) capAllocator = new ByteBufferBuilder(MESH_BUFFER_BYTES);
        return new BufferBuilder(capAllocator, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    private static void releaseAllocators() {
        RenderSystem.assertOnRenderThread();
        if (rimAllocator != null) { rimAllocator.close(); rimAllocator = null; }
        if (capAllocator != null) { capAllocator.close(); capAllocator = null; }
    }

    /**
     * Per-entity sweep clock (2026-10-16 video: the cut jumped up and down every frame). The server sends a linear,
     * monotonic progress once per tick, but packets arrive bunched; extrapolating from them overshot and then "reset".
     * Now each packet starts a straight-line segment from the value on screen to the packet's value, spread over the
     * measured packet interval - the same idea as vanilla entity interpolation: constant speed, never ahead of the
     * server, never jumping back. Only a sustained decrease (a real cancel ramping the body back) moves it up, and after
     * the final clear packet (sent just before the server removes the entity) it stays fully cut for a short grace.
     */
    private static final class Clock {
        double segStart, interval = 1.0, lastPacket = Double.NaN, frame = Double.NaN, endedAt = Double.NaN;
        float from, to, shown, lastRaw = -1f;
        int drops;
    }

    private static final java.util.Map<Integer, Clock> CLOCKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final double END_GRACE_TICKS = 15;

    /** Server timing lines (HakaiSweepTiming), anchored at this client's game time on receipt. */
    private record Timing(HakaiSweepTimeline timeline, boolean completed, float impactX, float impactY, float impactZ) {}
    private static final java.util.Map<Integer, Timing> TIMINGS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, net.bullettrain.xenopixelsmod.combat.HakaiSweepSync> IDENTITIES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final HakaiSweepSyncHistory SYNC_HISTORY = new HakaiSweepSyncHistory();
    private static Object identityLevel;

    private static void checkWorld() {
        Object level = Minecraft.getInstance().level;
        if (identityLevel != level) {
            IDENTITIES.clear(); TIMINGS.clear(); CLOCKS.clear(); END_RINGS.clear(); FITS.clear(); RING_RADII.clear();
            HakaiFade.clear();
            identityLevel = level;
        }
    }

    public static void apply(net.bullettrain.xenopixelsmod.combat.HakaiSweepSync packet) {
        checkWorld();
        var level = Minecraft.getInstance().level;
        if (level == null || !packet.dimension().equals(level.dimension().location())) return;
        var previous = IDENTITIES.get(packet.timing().entityId());
        if (!packet.newerThan(previous)) return;
        Entity target = level.getEntity(packet.timing().entityId());
        if (target != null && !packet.matches(level.dimension().location(), target.getUUID())) return;
        if (!SYNC_HISTORY.accept(packet)) return;
        if (packet.resetsSweep(previous)) {
            TIMINGS.remove(packet.timing().entityId()); CLOCKS.remove(packet.timing().entityId());
            END_RINGS.remove(packet.timing().entityId()); FITS.remove(packet.timing().entityId());
            RING_RADII.remove(packet.timing().entityId());
        }
        IDENTITIES.put(packet.timing().entityId(), packet);
        var timing = packet.timing();
        double now = level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        // Client game time is the vanilla server clock; late samples advance along the server line.
        float progress = packet.terminal() ? 0f : packet.progressAt(now);
        apply(new net.bullettrain.xenopixelsmod.combat.HakaiSweepTiming(timing.entityId(), progress,
                timing.ratePerTick(), timing.impactX(), timing.impactY(), timing.impactZ()));
    }

    /** Shared by alpha, target outlines and the clipped mesh; null permits the legacy fallback. */
    public static Float synchronizedProgress(Entity entity, float partialTick) {
        checkWorld();
        var packet = IDENTITIES.get(entity.getId());
        if (packet == null) return null;
        if (!packet.matches(entity.level().dimension().location(), entity.getUUID())) return 0f;
        Timing timing = TIMINGS.get(entity.getId());
        if (timing != null) return timing.timeline().at(entity.level().getGameTime() + partialTick);
        EndRing ring = END_RINGS.get(entity.getId());
        return ring != null && entity.level().getGameTime() + partialTick < ring.expiresAt() ? 1f : 0f;
    }
    /** A short-lived world-space endpoint ring, independent of the mob that the server removes at sweep completion. */
    private record EndRing(double x, double y, double z, float width, double expiresAt) {}
    private static final java.util.Map<Integer, EndRing> END_RINGS = new java.util.concurrent.ConcurrentHashMap<>();

    public static void apply(net.bullettrain.xenopixelsmod.combat.HakaiSweepTiming packet) {
        checkWorld();
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (packet.progress() <= 0f && packet.ratePerTick() <= 0f) {
            Timing previous = TIMINGS.remove(packet.entityId());
            // A timing-driven sweep can replace a legacy fade clock for the same entity. Do not let that
            // stale clock override the endpoint ring after the authoritative terminal packet arrives.
            CLOCKS.remove(packet.entityId());
            double now = level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            if (previous != null) {
                float projected = previous.timeline().at(now);
                if (previous.completed() || HakaiSweepRenderPolicy.shouldHoldEndRing(projected)) {
                    EndRing endRing = captureEndRing(packet.entityId(), now,
                            packet.impactX(), packet.impactY(), packet.impactZ());
                    if (endRing != null) END_RINGS.put(packet.entityId(), endRing);
                } else {
                    END_RINGS.remove(packet.entityId());
                }
            } else {
                EndRing existing = END_RINGS.get(packet.entityId());
                if (!HakaiSweepRenderPolicy.shouldKeepExistingEndRing(
                        existing != null, now, existing == null ? Double.NaN : existing.expiresAt())) {
                    END_RINGS.remove(packet.entityId());
                }
            }
            return;
        }
        // Once the server timing stream owns this sweep, the legacy fade interpolation must not linger as
        // an alternate source of progress when the timing stream terminates.
        CLOCKS.remove(packet.entityId());
        END_RINGS.remove(packet.entityId());
        double now = level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        Timing line = TIMINGS.get(packet.entityId());
        HakaiSweepTimeline timeline = line == null
                ? HakaiSweepTimeline.start(now, packet.progress(), packet.ratePerTick())
                : line.timeline().correct(now, packet.progress(), packet.ratePerTick());
        TIMINGS.put(packet.entityId(), new Timing(timeline, packet.progress() >= 1f,
                packet.impactX(), packet.impactY(), packet.impactZ()));
        if (XenoClientConfig.hakaiSweepDebug)
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("HAKAI_SWEEP timing id={} progress={} rate={} at={}", packet.entityId(),
                    packet.progress(), packet.ratePerTick(), level.getGameTime());
    }

    /** Smooth sweep progress 0..1 for this frame, or -1 when the entity is not being erased. */
    public static float sweepProgress(Entity entity, float partialTick) {
        Float synchronizedValue = synchronizedProgress(entity, partialTick);
        if (synchronizedValue != null) return synchronizedValue > 0f ? synchronizedValue : -1f;
        var level = Minecraft.getInstance().level;
        if (level == null) return -1f;
        Timing timing = TIMINGS.get(entity.getId());
        if (timing != null) {
            double now = level.getGameTime() + partialTick;
            // The cut mesh and rim sample the same continuous clock, as the planet renderer does.
            return timing.timeline().at(now);
        }
        EndRing completed = END_RINGS.get(entity.getId());
        if (completed != null && level.getGameTime() + partialTick < completed.expiresAt()) return 1f;
        return legacyProgress(entity, partialTick);
    }

    /** Fallback for servers without sweep timing: interpolate the per-tick fade packets. */
    private static float legacyProgress(Entity entity, float partialTick) {
        var level = Minecraft.getInstance().level;
        if (level == null) return -1f;
        double now = level.getGameTime() + partialTick;
        int id = entity.getId();
        Clock clock = CLOCKS.get(id);
        if (clock != null && clock.frame == now) return clock.shown;
        Integer amplifier = HakaiFade.amplifierOf(id);
        float raw = amplifier == null ? (HakaiFade.dissolving(entity) ? HakaiFade.progress(entity, partialTick) : 0f) : amplifier / 255f;
        if (raw <= 0f) {
            if (clock == null) return -1f;
            if (clock.shown >= 0.8f) {
                if (Double.isNaN(clock.endedAt)) clock.endedAt = now;
                if (now - clock.endedAt <= END_GRACE_TICKS) { clock.shown = 1f; clock.frame = now; return 1f; }
            }
            CLOCKS.remove(id);
            return -1f;
        }
        if (clock == null) {
            clock = new Clock();
            clock.from = clock.to = clock.shown = raw;
            clock.segStart = clock.lastPacket = now;
            clock.lastRaw = raw;
            CLOCKS.put(id, clock);
        } else if (raw != clock.lastRaw) {
            // A new packet: measure the interval, then glide from what is on screen now to the new value.
            double gap = now - clock.lastPacket;
            if (gap > 0.05) clock.interval = Math.max(0.5, Math.min(4.0, clock.interval * 0.7 + gap * 0.3));
            clock.lastPacket = now;
            if (raw < clock.shown) {
                // Bunched or late packets can repeat an older value; only a sustained fall is a real cancel.
                clock.drops++;
                if (clock.drops >= 3) { clock.from = clock.shown; clock.to = raw; clock.segStart = now; }
            } else {
                clock.drops = 0;
                clock.from = clock.shown;
                clock.to = raw;
                clock.segStart = now;
            }
            clock.lastRaw = raw;
        }
        clock.endedAt = Double.NaN;
        double t = Math.max(0, Math.min(1, (now - clock.segStart) / clock.interval));
        float value = (float) (clock.from + (clock.to - clock.from) * t);
        clock.shown = clock.drops >= 3 ? value : Math.max(clock.shown, value);
        clock.shown = Math.max(0f, Math.min(1f, clock.shown));
        clock.frame = now;
        if (XenoClientConfig.hakaiSweepDebug)
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("HAKAI_SWEEP calc id={} class={} now={} partial={} raw={} amp={} from={} to={} t={} shown={} drops={} interval={} feet={} h={}",
                    id, entity.getClass().getSimpleName(), String.format("%.3f", now), String.format("%.3f", partialTick), raw, amplifier,
                    clock.from, clock.to, String.format("%.3f", t), clock.shown, clock.drops, String.format("%.2f", clock.interval),
                    String.format("%.3f", entity.getPosition(partialTick).y), entity.getBbHeight());
        return clock.shown;
    }

    public static void forget() { IDENTITIES.clear(); CLOCKS.clear(); FITS.clear(); TIMINGS.clear(); END_RINGS.clear(); RING_RADII.clear(); releaseAllocators(); }

    private static EndRing captureEndRing(int entityId, double now, float impactX, float impactY, float impactZ) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        Entity entity = level.getEntity(entityId);
        if (entity != null) {
            float partial = (float) (now - level.getGameTime());
            float[] fitted = bounds(entity, partial);
            if (impactMode()) {
                var p = entity.getPosition(partial);
                double x = p.x + (impactX - 0.5) * entity.getBbWidth();
                double y = p.y + impactY * entity.getBbHeight() + RING_CUT_BIAS;
                double z = p.z + (impactZ - 0.5) * entity.getBbWidth();
                float terminalRadius = impactRadius(entity, 1.0f, partial);
                return new EndRing(x, y, z, Math.max(0.3f, terminalRadius * 2.0f),
                        now + END_GRACE_TICKS);
            }
            double y = cutY(entity, 1.0f, partial) + RING_CUT_BIAS;
            return new EndRing(fitted[2], y, fitted[3], Math.max(0.3f, fitted[4] * 2.0f), now + END_GRACE_TICKS);
        }
        Fit fit = FITS.get(entityId);
        Extent extent = fit == null ? null : fit.last;
        if (extent == null || !extent.valid()) return null;
        return new EndRing((extent.minX + extent.maxX) * 0.5,
                extent.minY + 0.01,
                (extent.minZ + extent.maxZ) * 0.5,
                Math.max(0.3f, Math.max(extent.maxX - extent.minX, extent.maxZ - extent.minZ)),
                now + END_GRACE_TICKS);
    }

    @SubscribeEvent public static void prune(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        checkWorld();
        var level = Minecraft.getInstance().level;
        if (level == null) { CLOCKS.clear(); FITS.clear(); TIMINGS.clear(); END_RINGS.clear(); RING_RADII.clear(); releaseAllocators(); return; }
        if (level.getGameTime() % 100 != 0 || (IDENTITIES.isEmpty() && CLOCKS.isEmpty() && FITS.isEmpty() && TIMINGS.isEmpty()
                && END_RINGS.isEmpty() && RING_RADII.isEmpty())) return;
        CLOCKS.keySet().removeIf(id -> level.getEntity(id) == null);
        IDENTITIES.entrySet().removeIf(entry -> level.getEntity(entry.getKey()) == null
                && level.getGameTime() - entry.getValue().sampledTick() > 1200);
        FITS.keySet().removeIf(id -> level.getEntity(id) == null);
        TIMINGS.keySet().removeIf(id -> level.getEntity(id) == null);
        RING_RADII.keySet().removeIf(id -> level.getEntity(id) == null);
        END_RINGS.entrySet().removeIf(entry -> level.getGameTime() >= entry.getValue().expiresAt());
    }

    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        SYNC_HISTORY.clear();
        IDENTITIES.clear(); identityLevel = null;
        CLOCKS.clear(); FITS.clear(); TIMINGS.clear(); END_RINGS.clear(); RING_RADII.clear(); releaseAllocators();
    }

    /** True while the sweep is erasing this entity (including the grace after the final clear packet). */
    public static boolean active(Entity entity) {
        if (entity == null) return false;
        Float synchronizedValue = synchronizedProgress(entity, 0f);
        if (synchronizedValue != null) return enabled() && synchronizedValue > 0f;
        return enabled() && entity != null && (TIMINGS.containsKey(entity.getId()) || CLOCKS.containsKey(entity.getId())
                || END_RINGS.containsKey(entity.getId())
                || HakaiFade.dissolving(entity));
    }

    /** World-space bounds of an entity's rendered model, accumulated vertex by vertex for one frame. */
    public static final class Extent {
        final double entityRenderX, entityRenderY, entityRenderZ;
        Extent(double entityRenderX, double entityRenderY, double entityRenderZ) {
            this.entityRenderX = entityRenderX; this.entityRenderY = entityRenderY; this.entityRenderZ = entityRenderZ;
        }
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        void add(float x, float y, float z) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
            if (x < minX) minX = x; if (x > maxX) maxX = x;
            if (y < minY) minY = y; if (y > maxY) maxY = y;
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
        }
        boolean valid() { return maxY > minY; }

        /** Cross-section edges of the model at the cut, this frame (x0,y0,z0,x1,y1,z1 per segment). */
        float[] segments = new float[96];
        int segmentCount;
        void segment(float x0, float y0, float z0, float x1, float y1, float z1) {
            if (!Float.isFinite(x0) || !Float.isFinite(y0) || !Float.isFinite(z0)
                    || !Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(z1)) return;
            if (segmentCount >= 4096) return;
            if ((segmentCount + 1) * 6 > segments.length) segments = java.util.Arrays.copyOf(segments, segments.length * 2);
            int o = segmentCount++ * 6;
            segments[o] = x0; segments[o + 1] = y0; segments[o + 2] = z0;
            segments[o + 3] = x1; segments[o + 4] = y1; segments[o + 5] = z1;
        }
    }

    private static final class Fit { double frame = Double.NaN; Extent building = new Extent(Double.NaN, Double.NaN, Double.NaN), last; }
    record SegmentKey(long first, long second) {
        static SegmentKey of(long a, long b) { return a <= b ? new SegmentKey(a, b) : new SegmentKey(b, a); }
    }
    private static final java.util.Map<Integer, Fit> FITS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Keep the presentation ring stable when the largest closed cut contour changes between animated frames. */
    private static final java.util.Map<Integer, Float> RING_RADII = new java.util.concurrent.ConcurrentHashMap<>();

    /** The extent this frame's model draw should record into (the previous frame's becomes the fitted one). */
    static Extent measure(Entity entity, float partialTick) {
        var level = Minecraft.getInstance().level;
        double now = level == null ? 0 : level.getGameTime() + partialTick;
        var renderPosition = entity.getPosition(partialTick);
        Fit fit = FITS.computeIfAbsent(entity.getId(), id -> new Fit());
        if (fit.frame != now) {
            if (fit.building.valid()) {
                fit.last = fit.building;
                if (XenoClientConfig.hakaiSweepDebug)
                    net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("HAKAI_SWEEP measured id={} worldY=[{},{}] entityRenderY={} worldX=[{},{}] worldZ=[{},{}] now={} nextEntityRenderY={} partial={}",
                            entity.getId(), fit.last.minY, fit.last.maxY, fit.last.entityRenderY,
                            fit.last.minX, fit.last.maxX, fit.last.minZ, fit.last.maxZ,
                            now, renderPosition.y, partialTick);
            }
            fit.building = new Extent(renderPosition.x, renderPosition.y, renderPosition.z);
            fit.frame = now;
        }
        return fit.building;
    }

    /**
     * The model's fitted vertical range and footprint (owner 2026-10-16: the ring "not under the mob... make
     * adjustments to fit different mobs"): measured from the real model of the previous frame. The cut keeps the
     * measured world bounds are translated by the entity's movement since that frame, while the ring's horizontal centre follows the current interpolated position. Model dimensions
     * are bounded by entity size so aura/effect layers cannot make the sweep enormous; the hitbox is used until a
     * model measurement exists.
     * Returns {bottom, top, centreX, centreZ, halfWidth}.
     */
    static float[] bounds(Entity entity, float partialTick) {
        // getX/Y/Z(double) interpret the argument as a hitbox fraction; getPosition(float) is the
        // tick interpolation also used by LevelRenderer when placing the rendered entity model.
        return bounds(entity, entity.getPosition(partialTick));
    }

    private static float[] bounds(Entity entity, net.minecraft.world.phys.Vec3 renderPosition) {
        float height = Math.max(0.1f, entity.getBbHeight());
        Fit fit = FITS.get(entity.getId());
        Extent e = fit == null ? null : fit.last;
        return HakaiSweepRenderPolicy.fittedWorldBounds(
                e == null ? Float.POSITIVE_INFINITY : e.minX,
                e == null ? Float.POSITIVE_INFINITY : e.minY,
                e == null ? Float.POSITIVE_INFINITY : e.minZ,
                e == null ? Float.NEGATIVE_INFINITY : e.maxX,
                e == null ? Float.NEGATIVE_INFINITY : e.maxY,
                e == null ? Float.NEGATIVE_INFINITY : e.maxZ,
                e == null ? Double.NaN : e.entityRenderY,
                renderPosition, entity.getBbWidth(), height);
    }

    /** World Y of the cut: from just above the model's real top down to just below its real feet, eased. */
    public static float cutY(Entity entity, float progress, float partialTick) {
        float[] b = bounds(entity, partialTick);
        return cutY(b, progress);
    }

    /** World-space crosshair point, stored as a normalized point in the target's bounding box. */
    static net.minecraft.world.phys.Vec3 impactPoint(Entity entity, float partialTick) {
        Timing timing = TIMINGS.get(entity.getId());
        if (timing == null) return entity.getPosition(partialTick).add(0, entity.getBbHeight() * 0.5, 0);
        var p = entity.getPosition(partialTick);
        return new net.minecraft.world.phys.Vec3(p.x + (timing.impactX - 0.5) * entity.getBbWidth(),
                p.y + timing.impactY * entity.getBbHeight(),
                p.z + (timing.impactZ - 0.5) * entity.getBbWidth());
    }

    /** Maximum model-space distance from the impact point, used to finish exactly at the model bounds. */
    static float impactRadius(Entity entity, float progress, float partialTick) {
        float[] b = bounds(entity, partialTick);
        var c = impactPoint(entity, partialTick);
        double far = 0;
        for (double x : new double[]{b[2] - b[4], b[2] + b[4]})
            for (double y : new double[]{b[0], b[1]})
                for (double z : new double[]{b[3] - b[4], b[3] + b[4]})
                    far = Math.max(far, c.distanceToSqr(x, y, z));
        return net.bullettrain.xenopixelsmod.combat.HakaiImpactGeometry.radius((float) Math.sqrt(far), progress);
    }

    private static float cutY(float[] b, float progress) {
        double p = Math.max(0, Math.min(1, progress));
        double eased = p * p * (3 - 2 * p);
        float top = b[1] + 0.03f, bottom = b[0] - 0.03f;
        return (float) (top - (top - bottom) * eased);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !enabled()) return;
        // Iris re-renders the level for its shadow map. This rim is unlit additive glow that mutates blend and
        // depth state directly, so it belongs to the main pass only - the same guard the space renderers use.
        if (IrisShaderState.packInUse() && IrisShaderState.renderingShadows()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { releaseAllocators(); return; }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().getPosition();
        BufferBuilder builder = null, caps = null;
        try {
        for (Entity entity : mc.level.entitiesForRendering()) {
            float progress = sweepProgress(entity, partial);
            if (!HakaiSweepRenderPolicy.shouldRenderRing(progress)) continue;
            if (builder == null) builder = beginRim();
            var renderPosition = entity.getPosition(partial);
            float[] fittedBounds = bounds(entity, renderPosition);
            Vec3 hitPoint = impactMode() ? impactPoint(entity, partial) : null;
            float cutY = impactMode() ? (float) hitPoint.y : cutY(fittedBounds, progress);
            // The clipped model ends exactly at cutY. Drawing the rim on that same depth plane lets
            // the body's depth buffer intermittently hide sections of it; lift it a few centimetres
            // into the already-erased side while keeping the measured sweep height.
            float y = (float) (cutY + RING_CUT_BIAS - camera.y);
            if (XenoClientConfig.hakaiSweepDebug)
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("HAKAI_SWEEP ring id={} progress={} cutY={}", entity.getId(), progress, y + camera.y);
            float pulse = 0.75f + 0.25f * (float) Math.sin((mc.level.getGameTime() + partial) * 0.7);
            Fit fit = FITS.get(entity.getId());
            if (!impactMode() && fit != null) {
                boolean currentContour = fit.building.segmentCount > 0;
                Extent contour = currentContour ? fit.building : fit.last;
                if (contour != null && contour.segmentCount > 0) {
                    var position = entity.getPosition(partial);
                    float dx = (float) (position.x - contour.entityRenderX);
                    float dy = (float) (position.y - contour.entityRenderY);
                    float dz = (float) (position.z - contour.entityRenderZ);
                    if (!currentContour) {
                        double sumY = 0;
                        for (int i = 0; i < contour.segmentCount; i++) {
                            int offset = i * 6;
                            sumY += contour.segments[offset + 1] + contour.segments[offset + 4];
                        }
                        float previousCutY = (float) (sumY / (contour.segmentCount * 2.0));
                        dy = HakaiSweepRenderPolicy.contourYOffset(cutY, previousCutY);
                    }
                    if (caps == null) caps = beginCaps();
                    capAndEdges(caps, builder, contour, camera, dx, dy, dz,
                            argb((int) (255 * pulse), 236, 170, 255));
                }
            }
            float fallbackRadius = impactMode() ? impactRadius(entity, progress, partial)
                    : Math.max(0.15f, fittedBounds[4]);
            float minimumRadius = Math.max(0.15f, entity.getBbWidth() * 0.55f);
            // Impact mode shares the exact expanding sphere radius with the model clip. Smoothing it against a
            // mob-width minimum made the visual wave lag the dissolve and start away from the crosshair point.
            float radius = impactMode() ? fallbackRadius : HakaiSweepRenderPolicy.stableRingRadius(
                    RING_RADII.getOrDefault(entity.getId(), Float.NaN), fallbackRadius, minimumRadius);
            if (!impactMode()) RING_RADII.put(entity.getId(), radius);
            float[] band = impactMode() ? HakaiSweepRenderPolicy.impactRingBand(radius)
                    : HakaiSweepRenderPolicy.visibleRingBand(radius);
            // The cap follows the model's cut contours; the outer ring follows the entity anchor. Choosing a
            // different largest contour each animated frame used to move/shrink the ring independently of the mob.
            if (impactMode()) {
                Vec3 center = hitPoint.subtract(camera);
                Vec3 right = HakaiSweepRenderPolicy.verticalImpactRingRight(hitPoint, camera);
                verticalRing(builder, center, right, band[0], band[1], pulse);
            } else {
                float cx = (float) (renderPosition.x - camera.x);
                float cz = (float) (renderPosition.z - camera.z);
                ring(builder, cx, y, cz, band[0], band[1], pulse);
            }
        }
        double now = mc.level.getGameTime() + partial;
        for (var entry : END_RINGS.entrySet()) {
            EndRing endRing = entry.getValue();
            if (now >= endRing.expiresAt()) {
                END_RINGS.remove(entry.getKey(), endRing);
                continue;
            }
            Entity entity = mc.level.getEntity(entry.getKey());
            // While the entity is still being rendered, its normal moving cut already owns this ring.
            if (entity != null && sweepProgress(entity, partial) > 0.0f) continue;
            if (builder == null) builder = beginRim();
            float width = Math.max(0.3f, endRing.width());
            float pulse = 0.75f + 0.25f * (float) Math.sin(now * 0.7);
            if (impactMode()) {
                Vec3 center = new Vec3(endRing.x(), endRing.y(), endRing.z());
                float[] band = HakaiSweepRenderPolicy.impactRingBand(width * 0.5f);
                verticalRing(builder, center.subtract(camera),
                        HakaiSweepRenderPolicy.verticalImpactRingRight(center, camera), band[0], band[1], pulse);
            } else {
                float[] band = HakaiSweepRenderPolicy.visibleRingBand(width * 0.5f);
                ring(builder, (float) (endRing.x() - camera.x), (float) (endRing.y() - camera.y),
                        (float) (endRing.z() - camera.z), band[0], band[1], pulse);
            }
        }
        // Caps first, with normal blending: a solid dark violet cross-section, so a cut horizontal body (cows, pigs)
        // never shows a hollow box or pops its whole back away (owner 2026-10-16: not smooth on 4-foot walkers).
        if (caps != null) {
            var capMesh = caps.build();
            if (capMesh != null) {
                boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
                RenderSystem.getModelViewStack().pushMatrix();
                RenderSystem.getModelViewStack().set(event.getModelViewMatrix());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                // The filled cap is translucent and must not add a new depth obstacle before the rim.
                GL11.glDepthMask(false);
                try {
                    drawWithNoFog(capMesh);
                } finally {
                    GL11.glDepthMask(depthWrite);
                    RenderSystem.enableCull();
                    RenderSystem.disableBlend();
                    RenderSystem.getModelViewStack().popMatrix();
                    RenderSystem.applyModelViewMatrix();
                }
            }
        }
        if (builder == null) return;
        var mesh = builder.build();
        if (mesh == null) return;
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        GL11.glDepthMask(false);
        try {
            drawWithNoFog(mesh);
        } finally {
            GL11.glDepthMask(depthWrite);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
        } catch (RuntimeException | Error failure) {
            // A thrown frame can leave unbuilt vertex ranges in either buffer. Close both native allocations so the
            // next frame starts from fresh, isolated streams instead of inheriting stale offsets.
            releaseAllocators();
            throw failure;
        }
    }

    /** The End applies short-range dimension fog to position-color shaders, which can erase this local glow. */
    private static void drawWithNoFog(com.mojang.blaze3d.vertex.MeshData mesh) {
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        try {
            FogRenderer.setupNoFog();
            BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    /**
     * Joins this frame cross-section segments into closed loops (one per cut model part: body, each leg, head) and
     * emits a filled cap per loop (fan from its centroid, dark centre to violet edge) plus a glowing edge strip.
     */
    private static void capAndEdges(BufferBuilder caps, BufferBuilder glow, Extent e, net.minecraft.world.phys.Vec3 camera,
                                       float dx, float dy, float dz, int edgeColour) {
        int n = e.segmentCount;
        float[] s = e.segments;
        // Deduplicate (outline and main pass draw the model twice) by quantised endpoints.
        java.util.Map<Long, java.util.List<Integer>> byPoint = new java.util.HashMap<>();
        java.util.Set<SegmentKey> seen = new java.util.HashSet<>();
        java.util.List<Integer> kept = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            long a = key(s[i * 6], s[i * 6 + 2]), b = key(s[i * 6 + 3], s[i * 6 + 5]);
            if (a == b) continue;
            if (!seen.add(SegmentKey.of(a, b))) continue;
            kept.add(i);
            byPoint.computeIfAbsent(a, k -> new java.util.ArrayList<>()).add(i);
            byPoint.computeIfAbsent(b, k -> new java.util.ArrayList<>()).add(i);
        }
        java.util.Set<Integer> used = new java.util.HashSet<>();
        float ox = (float) camera.x, oy = (float) camera.y, oz = (float) camera.z;
        int fade = edgeColour & 0x00FFFFFF;
        for (int start : kept) {
            if (used.contains(start)) continue;
            java.util.List<float[]> loop = new java.util.ArrayList<>();
            int seg = start;
            long at = key(s[seg * 6], s[seg * 6 + 2]);
            loop.add(new float[]{s[seg * 6], s[seg * 6 + 1], s[seg * 6 + 2]});
            while (seg >= 0 && used.add(seg)) {
                long a = key(s[seg * 6], s[seg * 6 + 2]);
                int o = a == at ? 3 : 0;
                float[] next = {s[seg * 6 + o], s[seg * 6 + o + 1], s[seg * 6 + o + 2]};
                loop.add(next);
                at = key(next[0], next[2]);
                int following = -1;
                for (int candidate : byPoint.getOrDefault(at, java.util.List.of())) if (!used.contains(candidate)) { following = candidate; break; }
                seg = following;
            }
            if (loop.size() < 4 || key(loop.get(0)[0], loop.get(0)[2])
                    != key(loop.get(loop.size() - 1)[0], loop.get(loop.size() - 1)[2])) continue;
            float cx = 0, cy = 0, cz = 0;
            for (float[] p : loop) { cx += p[0] + dx; cy += p[1] + dy; cz += p[2] + dz; }
            cx /= loop.size(); cy /= loop.size(); cz /= loop.size();
            for (int i = 0; i + 1 < loop.size(); i++) {
                float[] p = loop.get(i), q = loop.get(i + 1);
                caps.addVertex(cx - ox, cy - oy, cz - oz).setColor(0xF2080010);
                caps.addVertex(p[0] + dx - ox, p[1] + dy - oy, p[2] + dz - oz).setColor(0xF0360A5C);
                caps.addVertex(q[0] + dx - ox, q[1] + dy - oy, q[2] + dz - oz).setColor(0xF0360A5C);
                // Glow strip along the edge, pushed slightly outward from the centre of the part.
                float px = p[0] + dx + (p[0] - cx + dx) * 0.08f, pz = p[2] + dz + (p[2] - cz + dz) * 0.08f;
                float qx = q[0] + dx + (q[0] - cx + dx) * 0.08f, qz = q[2] + dz + (q[2] - cz + dz) * 0.08f;
                glow.addVertex(p[0] + dx - ox, p[1] + dy - oy + 0.002f, p[2] + dz - oz).setColor(edgeColour);
                glow.addVertex(px - ox, p[1] + dy - oy + 0.002f, pz - oz).setColor(fade);
                glow.addVertex(qx - ox, q[1] + dy - oy + 0.002f, qz - oz).setColor(fade);
                glow.addVertex(p[0] + dx - ox, p[1] + dy - oy + 0.002f, p[2] + dz - oz).setColor(edgeColour);
                glow.addVertex(qx - ox, q[1] + dy - oy + 0.002f, qz - oz).setColor(fade);
                glow.addVertex(q[0] + dx - ox, q[1] + dy - oy + 0.002f, q[2] + dz - oz).setColor(edgeColour);
            }
        }
    }

    private static long key(float x, float z) {
        return (Math.round(x * 512.0) & 0xFFFFFFFFL) << 32 | (Math.round(z * 512.0) & 0xFFFFFFFFL);
    }

    private static void disc(BufferBuilder b, float cx, float y, float cz, float r, int centre, int edge) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            b.addVertex(cx, y, cz).setColor(centre);
            b.addVertex(cx + (float) Math.cos(a0) * r, y, cz + (float) Math.sin(a0) * r).setColor(edge);
            b.addVertex(cx + (float) Math.cos(a1) * r, y, cz + (float) Math.sin(a1) * r).setColor(edge);
        }
    }

    /**
     * Texture-free purple energy rim. Three nested gradients give the moving and retained endpoint rings a crisp
     * violet-white core, a denser purple band, and a soft additive halo without changing their geometry or lifetime.
     */
    private static void ring(BufferBuilder b, float cx, float y, float cz, float inner, float outer, float pulse) {
        float span = Math.max(0.001f, outer - inner);
        float coreEdge = inner + span * 0.20f;
        float bandEdge = inner + span * 0.62f;
        ringBand(b, cx, y, cz, inner, coreEdge,
                argb((int) (220 * pulse), 244, 222, 255), argb((int) (178 * pulse), 190, 112, 255));
        ringBand(b, cx, y, cz, coreEdge, bandEdge,
                argb((int) (142 * pulse), 152, 60, 255), argb((int) (84 * pulse), 98, 28, 220));
        ringBand(b, cx, y, cz, bandEdge, outer,
                argb((int) (54 * pulse), 112, 46, 255), argb(0, 80, 24, 200));
    }

    /** Upright, camera-facing purple wave centred on the authoritative crosshair impact point. */
    private static void verticalRing(BufferBuilder b, Vec3 center, Vec3 right, float inner, float outer, float pulse) {
        float span = Math.max(0.001f, outer - inner);
        float coreEdge = inner + span * 0.20f;
        float bandEdge = inner + span * 0.62f;
        verticalRingBand(b, center, right, inner, coreEdge,
                argb((int) (220 * pulse), 244, 222, 255), argb((int) (178 * pulse), 190, 112, 255));
        verticalRingBand(b, center, right, coreEdge, bandEdge,
                argb((int) (142 * pulse), 152, 60, 255), argb((int) (84 * pulse), 98, 28, 220));
        verticalRingBand(b, center, right, bandEdge, outer,
                argb((int) (54 * pulse), 112, 46, 255), argb(0, 80, 24, 200));
    }

    private static void verticalRingBand(BufferBuilder b, Vec3 c, Vec3 right, float inner, float outer,
                                         int innerColor, int outerColor) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            ringQuad(b, c, right, inner, outer, c0, s0, c1, s1, innerColor, outerColor);
        }
    }

    private static void ringQuad(BufferBuilder b, Vec3 c, Vec3 right, float inner, float outer,
                                 float c0, float s0, float c1, float s1, int innerColor, int outerColor) {
        vertex(b, c, right, inner, c0, s0, innerColor);
        vertex(b, c, right, outer, c0, s0, outerColor);
        vertex(b, c, right, outer, c1, s1, outerColor);
        vertex(b, c, right, inner, c0, s0, innerColor);
        vertex(b, c, right, outer, c1, s1, outerColor);
        vertex(b, c, right, inner, c1, s1, innerColor);
    }

    private static void vertex(BufferBuilder b, Vec3 c, Vec3 right, float radius, float horizontal, float vertical, int color) {
        b.addVertex((float) (c.x + right.x * horizontal * radius),
                (float) (c.y + vertical * radius),
                (float) (c.z + right.z * horizontal * radius)).setColor(color);
    }

    private static void ringBand(BufferBuilder b, float cx, float y, float cz, float inner, float outer,
                                 int innerColor, int outerColor) {
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            b.addVertex(cx + c0 * inner, y, cz + s0 * inner).setColor(innerColor);
            b.addVertex(cx + c0 * outer, y, cz + s0 * outer).setColor(outerColor);
            b.addVertex(cx + c1 * outer, y, cz + s1 * outer).setColor(outerColor);
            b.addVertex(cx + c0 * inner, y, cz + s0 * inner).setColor(innerColor);
            b.addVertex(cx + c1 * outer, y, cz + s1 * outer).setColor(outerColor);
            b.addVertex(cx + c1 * inner, y, cz + s1 * inner).setColor(innerColor);
        }
    }

    private static int argb(int a, int r, int g, int b) { return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b; }
}
