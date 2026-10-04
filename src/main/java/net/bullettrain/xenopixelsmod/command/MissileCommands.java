package net.bullettrain.xenopixelsmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.entity.MissileTubeBlockEntity;
import net.bullettrain.xenopixelsmod.missile.BallisticMissileEntity;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.vs.ShipBallisticController;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleanup commands for stuck ordnance.
 *
 * <p>A missile that never detonated is not harmless: the entity keeps ticking, keeps its chunk
 * ticket alive through {@code MissileChunkLoadManager}, and a ship still registered as flying
 * keeps its physics controller resident. On a long-running server these accumulate, so an
 * operator needs a way to sweep them without hunting each one down.
 *
 * <p>Two kinds of "missile" exist here and both are cleared:
 * <ul>
 *   <li>{@link BallisticMissileEntity} — a real entity.</li>
 *   <li>A Sable hull flown as a missile by {@link ShipBallisticController} — a sub-level, not an
 *       entity, so it is aborted rather than discarded. The hull itself is left intact; only the
 *       flight is cancelled.</li>
 * </ul>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class MissileCommands {

    /**
     * Cap on the chunk radius an unloaded sweep may force-load.
     *
     * <p>Each chunk has to be generated-or-loaded to see its entities, so this is genuinely
     * expensive: 32 is already 65×65 = 4,225 chunks per dimension.
     */
    private static final int MAX_SWEEP_RADIUS = 32;

    private MissileCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command("xenomissile"));
        dispatcher.register(tubeCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tubeCommand() {
        return Commands.literal("xenotube")
                .executes(ctx -> tubeStatus(ctx.getSource()))
                .then(Commands.literal("speed")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 20))
                                .executes(ctx -> tubeSet(ctx.getSource(), "speed",
                                        IntegerArgumentType.getInteger(ctx, "level")))))
                .then(Commands.literal("clearance")
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 128))
                                .executes(ctx -> tubeSet(ctx.getSource(), "clearance",
                                        IntegerArgumentType.getInteger(ctx, "blocks")))))
                .then(Commands.literal("height")
                        .then(Commands.argument("y", IntegerArgumentType.integer(0, 16000))
                                .executes(ctx -> tubeSet(ctx.getSource(), "height",
                                        IntegerArgumentType.getInteger(ctx, "y")))));
    }

    private static int tubeStatus(CommandSourceStack source) {
        MissileTubeBlockEntity tube = lookedAtTube(source);
        if (tube == null) {
            source.sendFailure(Component.literal("Look at a missile tube."));
            return 0;
        }
        source.sendSuccess(() -> tube.statusLine(tube.getBlockState()
                .getValue(net.bullettrain.xenopixelsmod.block.custom.MissileTubeBlock.FACING)), false);
        return 1;
    }

    private static int tubeSet(CommandSourceStack source, String key, int value) {
        MissileTubeBlockEntity tube = lookedAtTube(source);
        if (tube == null) {
            source.sendFailure(Component.literal("Look at a missile tube."));
            return 0;
        }
        switch (key) {
            case "speed" -> tube.setSpeedLevel(value);
            case "clearance" -> tube.setSiloClearance(value);
            case "height" -> tube.setLaunchWorldY(value);
            default -> {
                return 0;
            }
        }
        source.sendSuccess(() -> tube.statusLine(tube.getBlockState()
                .getValue(net.bullettrain.xenopixelsmod.block.custom.MissileTubeBlock.FACING)), true);
        return 1;
    }

    private static MissileTubeBlockEntity lookedAtTube(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.world.entity.player.Player player)) return null;
        var hit = player.pick(8.0, 0.0f, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)) return null;
        var be = source.getLevel().getBlockEntity(blockHit.getBlockPos());
        return be instanceof MissileTubeBlockEntity tube ? tube : null;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> list(ctx.getSource()))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                // Loaded chunks only — cheap, and what you want almost always.
                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource(), 0))
                        // Also force-loads a radius around the caller and sweeps it, for
                        // ordnance that drifted into chunks nobody has visited since.
                        .then(Commands.argument("radiusChunks", IntegerArgumentType.integer(0, MAX_SWEEP_RADIUS))
                                .executes(ctx -> clear(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radiusChunks")))));
    }

    private static int list(CommandSourceStack source) {
        int entities = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            entities += loadedMissiles(level).size();
        }
        int flights = ShipBallisticController.activeFlightShipIds().size();
        int totalEntities = entities;
        source.sendSuccess(() -> Component.literal(String.format(
                "§bMissiles: §f%d §7entity(s) in loaded chunks, §f%d §7ship flight(s) active",
                totalEntities, flights)), false);
        return totalEntities + flights;
    }

    private static int clear(CommandSourceStack source, int radiusChunks) {
        int removed = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (BallisticMissileEntity missile : loadedMissiles(level)) {
                missile.discard();
                removed++;
            }
        }

        // Ship flights are aborted, not discarded — the hull is someone's build.
        int flights = ShipBallisticController.abortAll();

        int swept = radiusChunks > 0 ? sweepUnloaded(source, radiusChunks) : 0;
        removed += swept;

        int finalRemoved = removed;
        int finalSwept = swept;
        source.sendSuccess(() -> Component.literal(String.format(
                "§aCleared §f%d §amissile entity(s) and aborted §f%d §aship flight(s)%s",
                finalRemoved, flights,
                radiusChunks > 0 ? String.format(" (%d from a %d-chunk sweep)", finalSwept, radiusChunks) : "")),
                true);
        return finalRemoved + flights;
    }

    /**
     * Force-load a square of chunks around the caller and discard any missiles found.
     *
     * <p>Entities in unloaded chunks cannot be inspected — their data sits in region files and is
     * only deserialised on load. So the only honest way to reach them is to load the chunk, which
     * is why this is opt-in with an explicit radius rather than part of the default clear.
     */
    private static int sweepUnloaded(CommandSourceStack source, int radiusChunks) {
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();
        ChunkPos center = new ChunkPos((int) Math.floor(origin.x) >> 4, (int) Math.floor(origin.z) >> 4);
        int removed = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                try {
                    // getChunk(..., true) loads or generates; without it an absent chunk is skipped
                    // and the sweep would silently do nothing for exactly the chunks we care about.
                    level.getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, true);
                } catch (Throwable t) {
                    // One bad chunk must not abort the sweep.
                    XenoPixelsMod.LOGGER.warn("Missile sweep could not load chunk {},{}",
                            center.x + dx, center.z + dz, t);
                }
            }
        }
        for (BallisticMissileEntity missile : loadedMissiles(level)) {
            missile.discard();
            removed++;
        }
        return removed;
    }

    private static List<BallisticMissileEntity> loadedMissiles(ServerLevel level) {
        List<BallisticMissileEntity> found = new ArrayList<>();
        level.getEntities(ModEntities.BALLISTIC_MISSILE.get(), missile -> true, found);
        return found;
    }
}
