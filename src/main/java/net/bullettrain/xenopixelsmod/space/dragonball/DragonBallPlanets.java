package net.bullettrain.xenopixelsmod.space.dragonball;

import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Planet-form Super Dragon Balls - render/performance bench (design-doc 16, owner request 2026-10-16: "big planets in
 * space", compare 1:1 voxel spheres against smooth poly spheres). Two forms, both admin-command driven for now:
 * <ul>
 *     <li>VOXEL: seven Sable bodies built 1:1 from {@link DragonBallPlanetShape} (hollow amber shell, crimson star
 *     inlay) through the shared batched {@link PlotBodyBuilder}, one ball at a time, timing every build.</li>
 *     <li>POLY: no server bodies; the client draws seven textured spheres from a {@link DragonBallPlanetSnapshot}.</li>
 * </ul>
 * The seven balls are laid on a heptagon in the Voidsea facing +Z (where the stars are) relative to the command's
 * position, so a player looking north sees all seven star faces. Nothing here is persisted; server stop removes the
 * bodies. Sable's pose scale was checked and rejected for a "big pixel" form: it is client-render only, not synced and
 * ignored by physics (decompiled Sable 2.0.3, 2026-10-16).
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DragonBallPlanets {
    static final SubLevelLoadingTicketType<String> TICKET = SubLevelLoadingTicketType.create(
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "dragon_ball_planet"), Codec.STRING);
    public static final String TAG = "xenopixelsmod_dragon_ball_planet_v1";

    public enum Form { VOXEL, POLY }

    private record Spot(int stars, Vector3d centre) {}

    private static final List<Spot> pending = new ArrayList<>();
    private static final List<UUID> bodies = new ArrayList<>();
    private static final List<String> report = new ArrayList<>();
    private static DragonBallPlanetSnapshot poly = new DragonBallPlanetSnapshot(List.of());
    private static PlotBodyBuilder.Job job;
    private static Spot building;
    private static int radius, thickness, budget, buildTicks;
    private static long benchStartNanos;

    private DragonBallPlanets() {}

    /** Starts a bench: clears the previous one, then lays out seven balls of {@code radius} in front of {@code origin}. */
    public static String bench(MinecraftServer server, Form form, int radius, int thickness, int budget, Vec3 origin) {
        clear(server);
        DragonBallPlanets.radius = radius;
        DragonBallPlanets.thickness = thickness;
        DragonBallPlanets.budget = budget;
        // Heptagon of ring radius 3R in the XY plane, 7R north (-Z) of the origin, star faces (+Z) toward the origin.
        double ring = radius * 3.0, cx = origin.x, cy = origin.y, cz = origin.z - radius * 7.0;
        var spots = new ArrayList<Spot>();
        for (int i = 0; i < CosmicDragonBalls.COUNT; i++) {
            double angle = Math.PI / 2 + i * Math.PI * 2 / CosmicDragonBalls.COUNT;
            spots.add(new Spot(i + 1, new Vector3d(cx - Math.cos(angle) * ring, cy + Math.sin(angle) * ring, cz)));
        }
        report.clear();
        benchStartNanos = System.nanoTime();
        if (form == Form.POLY) {
            var list = new ArrayList<DragonBallPlanetSnapshot.Ball>();
            for (Spot s : spots) list.add(new DragonBallPlanetSnapshot.Ball(s.stars, s.centre.x, s.centre.y, s.centre.z, radius));
            poly = new DragonBallPlanetSnapshot(list);
            syncAll(server);
            report.add("poly: 7 spheres r=" + radius + " sent to clients");
        } else {
            pending.addAll(spots);
        }
        XenoPixelsMod.LOGGER.info("SDB_BENCH start form={} r={} thickness={} budget={} centre=({}, {}, {})", form, radius, thickness, budget,
                Math.round(cx), Math.round(cy), Math.round(cz));
        return "bench " + form.name().toLowerCase() + " r=" + radius + " started";
    }

    public static void clear(MinecraftServer server) {
        ServerLevel level = voidsea(server);
        if (job != null) { job.cancel(); job = null; building = null; }
        pending.clear();
        if (level != null) {
            var container = SubLevelContainer.getContainer(level);
            for (UUID id : bodies)
                if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved()) {
                    container.removeForceLoadTicket(body, TICKET, id.toString());
                    container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                }
        }
        bodies.clear();
        if (!poly.balls().isEmpty()) { poly = new DragonBallPlanetSnapshot(List.of()); syncAll(server); }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (job == null && pending.isEmpty()) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = voidsea(server);
        if (level == null) { pending.clear(); return; }
        if (job == null) {
            building = pending.remove(0);
            int stars = building.stars;
            BlockState shell = ModBlocks.COSMIC_BALL_SHELL.get().defaultBlockState(), star = ModBlocks.COSMIC_BALL_STAR.get().defaultBlockState();
            int r = radius, t = thickness;
            PlotBodyBuilder.Voxels voxels = (x, y, z) -> switch (DragonBallPlanetShape.voxel(stars, r, t, x, y, z)) {
                case 1 -> shell;
                case 2 -> star;
                default -> null;
            };
            buildTicks = 0;
            // Ticket at allocation, as AsteroidPhysicalizer does: a multi-batch build must stay loaded between batches
            // (Codex review of CL-139).
            job = PlotBodyBuilder.start(level, building.centre, DragonBallPlanetShape.extent(r), voxels).withoutEviction().withoutFrameFirst()
                    .onBodyAllocated(body -> SubLevelContainer.getContainer(level).addForceLoadTicket(body, TICKET, body.getUniqueId().toString()));
        }
        long start = System.nanoTime();
        boolean complete;
        try {
            long tick = server.overworld().getGameTime();
            var owner = net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL;
            if (job.body() == null && !net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget
                    .mayPrepare(server, tick, owner, budget)) return;
            if (!net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.prepare(server, tick, owner, job)) {
                if (job.failed()) {
                    XenoPixelsMod.LOGGER.warn("SDB_BENCH ball {} failed (no free plot)", building.stars);
                    job = null;
                }
                return;
            }
            int stepBudget = net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.allowance(
                    server, tick, owner, job, budget);
            if (stepBudget <= 0) return;
            double millisBudget = net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.remainingMillis(
                    server, tick, owner);
            if (XenoSpaceConfig.generatedBodyBuildMillisPerTick() > 0.0
                    && millisBudget <= 0.0 && job.installed() > 0) return;
            complete = net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.step(
                    server, tick, owner, job, stepBudget, millisBudget);
        } catch (RuntimeException error) {
            XenoPixelsMod.LOGGER.error("SDB_BENCH ball {} build failed", building.stars, error);
            // The ticket is added at allocation (:131), so a throw after that point would otherwise keep an
            // empty or half-built plot force-loaded forever: cancel() drops the body but not the ticket.
            try {
                ServerSubLevel orphan = job.body();
                if (orphan != null && !orphan.isRemoved()) SubLevelContainer.getContainer(level)
                        .removeForceLoadTicket(orphan, TICKET, orphan.getUniqueId().toString());
            } catch (RuntimeException cleanupFailure) {
                XenoPixelsMod.LOGGER.error("SDB_BENCH ball {} could not release its force-load ticket",
                        building.stars, cleanupFailure);
            }
            job.cancel();
            job = null; building = null; return;
        }
        buildTicks++;
        if (!complete) {
            if (job.failed()) { XenoPixelsMod.LOGGER.warn("SDB_BENCH ball {} failed (no free plot)", building.stars); job = null; }
            return;
        }
        ServerSubLevel body = job.body();
        var result = job.result();
        if (body.getUserDataTag() == null) body.setUserDataTag(new CompoundTag());
        CompoundTag tag = new CompoundTag();
        tag.putInt("stars", building.stars);
        body.getUserDataTag().put(TAG, tag);
        bodies.add(body.getUniqueId());
        String line = String.format("ball %d r=%d voxels=%d fill=%.1fms install=%.1fms maxSlice=%.1fms ticks=%d batches=%d lastStep=%.1fms",
                building.stars, radius, result.blocks(), result.fillMillis(), result.installMillis(), job.maxServerSliceMillis(), buildTicks,
                job.batches(), (System.nanoTime() - start) / 1e6);
        report.add(line);
        XenoPixelsMod.LOGGER.info("SDB_BENCH {}", line);
        job = null; building = null;
        if (pending.isEmpty())
            XenoPixelsMod.LOGGER.info("SDB_BENCH voxel complete: 7 balls in {} ms wall", Math.round((System.nanoTime() - benchStartNanos) / 1e6));
    }

    /** Planet-sized balls must stay visible from afar: replicate them to players within this many blocks of the centre. */
    public static final double TRACKING_RANGE = 2048;

    /** Called from the Sable tracking mixin with the body's own logicalPose.position vector (identity compare, like Earth). */
    public static boolean extendsNativeTracking(ServerLevel level, net.minecraft.world.entity.player.Player player, org.joml.Vector3dc centre) {
        if (player.level() != level || !PhysicalEarth.HOST.equals(level.dimension().location())) return false;
        var container = SubLevelContainer.getContainer(level);
        for (UUID id : bodies)
            if (container.getSubLevel(id) instanceof ServerSubLevel body && body.logicalPose().position() == centre)
                return centre.distanceSquared(player.getX(), player.getY(), player.getZ()) <= TRACKING_RANGE * TRACKING_RANGE;
        return DragonBallBodyManager.extendsNativeTracking(level, player, centre);
    }

    public static String status() {
        var out = new StringBuilder("dragon ball planets: bodies=" + bodies.size() + " pending=" + pending.size()
                + (building != null ? " building=" + building.stars : "") + " poly=" + poly.balls().size());
        for (String line : report) out.append('\n').append(line);
        return out.toString();
    }

    private static ServerLevel voidsea(MinecraftServer server) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
    }

    static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) AddonNetwork.sendToPlayer(player, poly);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !poly.balls().isEmpty()) AddonNetwork.sendToPlayer(player, poly);
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { clear(event.getServer()); }
}
