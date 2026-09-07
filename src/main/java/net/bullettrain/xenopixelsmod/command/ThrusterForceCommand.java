package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.entity.ShipThrusterBlockEntity;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Command to inspect and modify XenoPixels thruster force values. */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public class ThrusterForceCommand {
    private static final double LOOK_DISTANCE = 5.5;
    private static final double LOOK_RADIUS = 0.9;
    /** How far from the world position named by {@code at <x y z>} a ship thruster's transformed
     * world centre may sit and still be the one the player meant. A ship block never lines up
     * exactly with a parent-world block position once the hull is translated or rotated. */
    static final double AT_MATCH_RADIUS = 1.25;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("xenothruster")
            .then(Commands.literal("force")
                .executes(ctx -> getForce(ctx.getSource()))
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                    .executes(ctx -> setForce(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "value")))
                    .then(Commands.literal("radius")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                            .executes(ctx -> setForceInRadius(ctx.getSource(),
                                DoubleArgumentType.getDouble(ctx, "value"),
                                IntegerArgumentType.getInteger(ctx, "radius")))))
                    .then(Commands.literal("at")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                            .executes(ctx -> setForceAtPosition(ctx.getSource(),
                                DoubleArgumentType.getDouble(ctx, "value"),
                                BlockPosArgument.getBlockPos(ctx, "pos")))))
                    .then(Commands.literal("all")
                        .executes(ctx -> setForceAllOnShip(ctx.getSource(),
                            DoubleArgumentType.getDouble(ctx, "value"))))
                    .then(Commands.literal("nearest")
                        .executes(ctx -> setForceNearest(ctx.getSource(),
                            DoubleArgumentType.getDouble(ctx, "value"), 32))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                            .executes(ctx -> setForceNearest(ctx.getSource(),
                                DoubleArgumentType.getDouble(ctx, "value"),
                                IntegerArgumentType.getInteger(ctx, "radius"))))))));
    }

    private static int getForce(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ThrusterTarget target = resolveLookedAt(source.getLevel(), player);
        if (target == null) {
            target = findNearestThruster(source.getLevel(), player.position(), 32);
        }
        if (target == null) {
            source.sendFailure(Component.literal("No loaded thruster is targeted or within 32 world blocks"));
            return 0;
        }

        ShipThrusterBlockEntity thruster = target.thruster();
        source.sendSuccess(() -> Component.literal("Thruster force: " + thruster.getMaxForce()), false);
        return 1;
    }

    private static int setForce(CommandSourceStack source, double force) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ThrusterTarget target = resolveLookedAt(source.getLevel(), player);
        if (target == null) {
            target = findNearestThruster(source.getLevel(), player.position(), 32);
        }
        if (target == null) {
            source.sendFailure(Component.literal("No loaded thruster is targeted or within 32 world blocks"));
            return 0;
        }

        target.thruster().setMaxForce(force);
        source.sendSuccess(() -> Component.literal("Set thruster force to: " + force), false);
        return 1;
    }

    private static int setForceInRadius(CommandSourceStack source, double force, int radius)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ThrusterTarget target = resolveLookedAt(source.getLevel(), player);
        if (target == null) {
            source.sendFailure(Component.literal("No loaded thruster is targeted"));
            return 0;
        }

        List<ShipThrusterBlockEntity> thrusters;
        if (target.ship() != null) {
            thrusters = findThrustersOnShip(target.ship(), target.thruster().getBlockPos(), radius);
        } else {
            thrusters = findGroundThrusters(source.getLevel(), target.worldCenter(), radius);
        }
        for (ShipThrusterBlockEntity thruster : thrusters) {
            thruster.setMaxForce(force);
        }

        int count = thrusters.size();
        source.sendSuccess(() -> Component.literal("Set " + count + " thrusters to force: " + force), false);
        return count;
    }

    private static int setForceAtPosition(CommandSourceStack source, double force, BlockPos pos) {
        ThrusterTarget target = resolveAtPosition(source.getLevel(), pos);
        if (target == null) {
            source.sendFailure(Component.literal("No loaded thruster found at or intersecting world block "
                    + pos.toShortString()));
            return 0;
        }

        target.thruster().setMaxForce(force);
        source.sendSuccess(() -> Component.literal("Set thruster at " + pos.toShortString()
                + " to force: " + force), false);
        return 1;
    }

    private static int setForceAllOnShip(CommandSourceStack source, double force)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerSubLevel ship = findPlayerShip(source.getLevel(), player);
        if (ship == null) {
            source.sendFailure(Component.literal("You are not tracking or standing on a loaded ship"));
            return 0;
        }

        List<ShipThrusterBlockEntity> thrusters = findAllThrustersOnShip(ship);
        if (thrusters.isEmpty()) {
            source.sendFailure(Component.literal("The selected ship has no loaded thrusters"));
            return 0;
        }

        for (ShipThrusterBlockEntity thruster : thrusters) {
            thruster.setMaxForce(force);
        }
        int count = thrusters.size();
        source.sendSuccess(() -> Component.literal("Set " + count
                + " loaded thrusters on ship to force: " + force), false);
        return count;
    }

    private static int setForceNearest(CommandSourceStack source, double force, int radius)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ThrusterTarget target = findNearestThruster(source.getLevel(), player.position(), radius);
        if (target == null) {
            source.sendFailure(Component.literal("No loaded thruster found within " + radius
                    + " world blocks"));
            return 0;
        }

        target.thruster().setMaxForce(force);
        source.sendSuccess(() -> Component.literal("Set nearest thruster to force: " + force), false);
        return 1;
    }

    private static ThrusterTarget resolveLookedAt(ServerLevel level, ServerPlayer player) {
        HitResult hit = player.pick(LOOK_DISTANCE, 0.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            ThrusterTarget direct = resolveDirect(level, ((BlockHitResult) hit).getBlockPos(), true);
            if (direct != null) return direct;
        }

        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0F).scale(LOOK_DISTANCE));
        RaySelection selection = new RaySelection();
        forEachNearbyThruster(level, start, LOOK_DISTANCE + 1.5, target -> {
            double along = projectionParameter(target.worldCenter(), start, end);
            if (along < 0.0 || along > 1.0) return;
            double distanceSquared = distanceToSegmentSquared(target.worldCenter(), start, end);
            if (distanceSquared > LOOK_RADIUS * LOOK_RADIUS) return;
            if (along < selection.along || along == selection.along && distanceSquared < selection.distanceSquared) {
                selection.target = target;
                selection.along = along;
                selection.distanceSquared = distanceSquared;
            }
        });
        return selection.target;
    }

    /**
     * Resolve the thruster meant by {@code at <x y z>}. The argument is a <em>parent-world</em>
     * position the player typed, so ship thrusters are matched only on their transformed world
     * centre — never on their raw plot coordinates, which are stored far from the hull's visible
     * position and could collide with the requested numbers by coincidence.
     */
    private static ThrusterTarget resolveAtPosition(ServerLevel level, BlockPos pos) {
        ThrusterTarget direct = resolveDirect(level, pos, false);
        if (direct != null) return direct;

        Vec3 requestedCenter = Vec3.atCenterOf(pos);
        NearestSelection selection = new NearestSelection(AT_MATCH_RADIUS);
        forEachShipThruster(level, target -> selection.consider(target, requestedCenter));
        return selection.target;
    }

    /**
     * Look up a thruster at an exact block position.
     *
     * <p>{@code matchPlotPositions} decides whether ship thrusters may be matched on their raw
     * plot {@link BlockPos}. That is right for the look path, where the position comes from a
     * {@link BlockHitResult} that Sable has already resolved <em>into</em> the ship's plot, and
     * wrong for {@code at}, where the position is a parent-world coordinate typed by a player and
     * a plot position that happens to carry the same numbers belongs to an unrelated hull.
     */
    private static ThrusterTarget resolveDirect(ServerLevel level, BlockPos pos,
                                                boolean matchPlotPositions) {
        BlockEntity direct = level.hasChunkAt(pos) ? level.getBlockEntity(pos) : null;
        if (direct instanceof ShipThrusterBlockEntity thruster) {
            ServerSubLevel ship = VsShipHelper.getLoadedShipAt(level, pos);
            return new ThrusterTarget(thruster, ship, worldCenter(thruster, ship));
        }
        if (!matchPlotPositions) return null;

        for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel ship)) continue;
            for (ShipThrusterBlockEntity thruster : findAllThrustersOnShip(ship)) {
                if (thruster.getBlockPos().equals(pos)) {
                    return new ThrusterTarget(thruster, ship, worldCenter(thruster, ship));
                }
            }
        }
        return null;
    }

    private static ServerSubLevel findPlayerShip(ServerLevel level, ServerPlayer player) {
        try {
            SubLevelAccess tracked = SableCompanion.INSTANCE.getTrackingOrVehicleSubLevel(player);
            if (tracked instanceof ServerSubLevel ship) return ship;
        } catch (Throwable ignored) {
        }

        try {
            ServerSubLevel containing = VsShipHelper.getLoadedShipAt(level, player.blockPosition());
            if (containing != null) return containing;
        } catch (Throwable ignored) {
        }

        Vec3 playerPos = player.position();
        for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (candidate instanceof ServerSubLevel ship
                    && ship.boundingBox().contains(playerPos.x, playerPos.y, playerPos.z)) {
                return ship;
            }
        }
        return null;
    }

    private static List<ShipThrusterBlockEntity> findThrustersOnShip(
            ServerSubLevel ship, BlockPos center, int radius) {
        double radiusSquared = (double) radius * radius;
        List<ShipThrusterBlockEntity> result = new ArrayList<>();
        forEachThrusterOnShip(ship, thruster -> {
            if (thruster.getBlockPos().distSqr(center) <= radiusSquared) {
                result.add(thruster);
            }
        });
        return result;
    }

    private static List<ShipThrusterBlockEntity> findAllThrustersOnShip(ServerSubLevel ship) {
        List<ShipThrusterBlockEntity> result = new ArrayList<>();
        forEachThrusterOnShip(ship, result::add);
        return result;
    }

    private static List<ShipThrusterBlockEntity> findGroundThrusters(
            ServerLevel level, Vec3 center, double radius) {
        List<ShipThrusterBlockEntity> result = new ArrayList<>();
        forEachGroundThruster(level, center, radius, target -> result.add(target.thruster()));
        return result;
    }

    private static ThrusterTarget findNearestThruster(ServerLevel level, Vec3 worldPos, double radius) {
        NearestSelection selection = new NearestSelection(radius);
        forEachNearbyThruster(level, worldPos, radius, target -> selection.consider(target, worldPos));
        return selection.target;
    }

    private static void forEachNearbyThruster(
            ServerLevel level, Vec3 worldCenter, double radius, Consumer<ThrusterTarget> consumer) {
        Set<ShipThrusterBlockEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Consumer<ThrusterTarget> unique = target -> {
            if (seen.add(target.thruster())) consumer.accept(target);
        };
        forEachGroundThruster(level, worldCenter, radius, unique);
        forEachShipThruster(level, target -> {
            if (target.worldCenter().distanceToSqr(worldCenter) <= radius * radius) {
                unique.accept(target);
            }
        });
    }

    private static void forEachGroundThruster(
            ServerLevel level, Vec3 center, double radius, Consumer<ThrusterTarget> consumer) {
        int minChunkX = Mth.floor(center.x - radius) >> 4;
        int maxChunkX = Mth.floor(center.x + radius) >> 4;
        int minChunkZ = Mth.floor(center.z - radius) >> 4;
        int maxChunkZ = Mth.floor(center.z + radius) >> 4;
        double radiusSquared = radius * radius;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ShipThrusterBlockEntity thruster)) continue;
                    Vec3 targetCenter = Vec3.atCenterOf(thruster.getBlockPos());
                    if (targetCenter.distanceToSqr(center) <= radiusSquared) {
                        consumer.accept(new ThrusterTarget(thruster, null, targetCenter));
                    }
                }
            }
        }
    }

    private static void forEachShipThruster(ServerLevel level, Consumer<ThrusterTarget> consumer) {
        for (var candidate : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(candidate instanceof ServerSubLevel ship)) continue;
            forEachThrusterOnShip(ship,
                    thruster -> consumer.accept(new ThrusterTarget(thruster, ship, worldCenter(thruster, ship))));
        }
    }

    private static void forEachThrusterOnShip(
            ServerSubLevel ship, Consumer<ShipThrusterBlockEntity> consumer) {
        Set<ShipThrusterBlockEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var holder : ship.getPlot().getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk == null) continue;
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                if (blockEntity instanceof ShipThrusterBlockEntity thruster && seen.add(thruster)) {
                    consumer.accept(thruster);
                }
            }
        }
    }

    static Vec3 transformedBlockCenter(ServerSubLevel ship, BlockPos localPos) {
        return transformedBlockCenter(ship.logicalPose(), localPos);
    }

    static Vec3 transformedBlockCenter(Pose3dc pose, BlockPos localPos) {
        Vector3d transformed = pose.transformPosition(new Vector3d(
                localPos.getX() + 0.5, localPos.getY() + 0.5, localPos.getZ() + 0.5));
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }

    private static Vec3 worldCenter(ShipThrusterBlockEntity thruster, ServerSubLevel ship) {
        return ship == null ? Vec3.atCenterOf(thruster.getBlockPos())
                : transformedBlockCenter(ship, thruster.getBlockPos());
    }

    static double projectionParameter(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared <= 1.0e-12) return 0.0;
        return point.subtract(start).dot(segment) / lengthSquared;
    }

    static double distanceToSegmentSquared(Vec3 point, Vec3 start, Vec3 end) {
        double parameter = Mth.clamp(projectionParameter(point, start, end), 0.0, 1.0);
        Vec3 closest = start.add(end.subtract(start).scale(parameter));
        return point.distanceToSqr(closest);
    }

    private record ThrusterTarget(
            ShipThrusterBlockEntity thruster, ServerSubLevel ship, Vec3 worldCenter) {
    }

    private static final class NearestSelection {
        private final double radiusSquared;
        private ThrusterTarget target;
        private double distanceSquared = Double.POSITIVE_INFINITY;

        private NearestSelection(double radius) {
            this.radiusSquared = radius * radius;
        }

        private void consider(ThrusterTarget candidate, Vec3 center) {
            double candidateDistanceSquared = candidate.worldCenter().distanceToSqr(center);
            if (candidateDistanceSquared <= radiusSquared && candidateDistanceSquared < distanceSquared) {
                target = candidate;
                distanceSquared = candidateDistanceSquared;
            }
        }
    }

    private static final class RaySelection {
        private ThrusterTarget target;
        private double along = Double.POSITIVE_INFINITY;
        private double distanceSquared = Double.POSITIVE_INFINITY;
    }
}
