package net.bullettrain.xenopixelsmod.features.tournament;

import com.dragonminez.server.world.structure.helper.DMZStructures;
import com.dragonminez.server.world.structure.helper.StructureLocator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.command.XenoPermissions;
import net.bullettrain.xenopixelsmod.compat.worldedit.WorldEditBridge;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Tournament commands.
 *
 * <pre>
 * /xenotourney join | leave | status | result | arenas | start
 * /xenotourney arena locate [cell_arena]
 * /xenotourney arena bindcell [index]
 * /xenotourney arena setfromwe [index]
 * /xenotourney arena setspawn [index]
 * /xenotourney arena clear &lt;index&gt; | clearall
 * </pre>
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class TournamentCommands {
    private TournamentCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("xenotourney")
                .then(Commands.literal("join")
                        .requires(XenoPermissions.require(XenoPermissions.TOURNEY_JOIN))
                        .executes(ctx -> join(ctx.getSource())))
                .then(Commands.literal("leave")
                        .requires(XenoPermissions.require(XenoPermissions.TOURNEY_LEAVE))
                        .executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("status")
                        .requires(XenoPermissions.require(XenoPermissions.TOURNEY_STATUS))
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("result")
                        .requires(XenoPermissions.require(XenoPermissions.TOURNEY_RESULT))
                        .then(Commands.argument("matchId", StringArgumentType.word())
                                .then(Commands.argument("winner", EntityArgument.player())
                                        .executes(ctx -> result(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "matchId"),
                                                EntityArgument.getPlayer(ctx, "winner"))))))
                .then(Commands.literal("arenas")
                        .requires(XenoPermissions.require(XenoPermissions.TOURNEY_ARENAS))
                        .executes(ctx -> arenas(ctx.getSource())))
                .then(Commands.literal("arena")
                        .requires(XenoPermissions.require(XenoPermissions.TOURNEY_RESULT))
                        .then(Commands.literal("locate")
                                .executes(ctx -> arenaLocate(ctx.getSource(), "cell_arena"))
                                .then(Commands.argument("structureId", StringArgumentType.word())
                                        .executes(ctx -> arenaLocate(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "structureId")))))
                        .then(Commands.literal("bindcell")
                                .executes(ctx -> arenaBindCell(ctx.getSource(), -1))
                                .then(Commands.argument("index",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(ctx -> arenaBindCell(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .getInteger(ctx, "index")))))
                        .then(Commands.literal("setfromwe")
                                .executes(ctx -> arenaSetFromWe(ctx.getSource(), -1))
                                .then(Commands.argument("index",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(ctx -> arenaSetFromWe(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .getInteger(ctx, "index")))))
                        .then(Commands.literal("setspawn")
                                .executes(ctx -> arenaSetSpawn(ctx.getSource(), -1))
                                .then(Commands.argument("index",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(ctx -> arenaSetSpawn(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .getInteger(ctx, "index")))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("index",
                                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(ctx -> arenaClear(ctx.getSource(),
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .getInteger(ctx, "index")))))
                        .then(Commands.literal("clearall")
                                .executes(ctx -> arenaClearAll(ctx.getSource()))))
                .then(Commands.literal("start")
                        .requires(XenoPermissions.require(XenoPermissions.TOURNEY_RESULT))
                        .executes(ctx -> start(ctx.getSource())))
                .executes(ctx -> help(ctx.getSource())));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Usage: /xenotourney join|leave|status|result|arenas|start\n"
                        + "Arena: /xenotourney arena locate|bindcell|setfromwe|setspawn|clear|clearall\n"
                        + "Queue+KotH (max " + TournamentService.MAX_QUEUED
                        + "). Out-of-bounds auto-lose is off until tournamentOutOfBoundsLose."),
                false);
        return 1;
    }

    private static int join(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (TournamentService.enqueue(player)) {
            source.sendSuccess(() -> Component.literal(
                    "Joined the tournament queue ("
                            + TournamentSavedData.get(player.getServer()).queuedCount()
                            + "/" + TournamentService.MAX_QUEUED + ")."), false);
            return 1;
        }
        source.sendFailure(Component.literal(
                "Could not join (disabled, full, already queued, or already in a match)."));
        return 0;
    }

    private static int leave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (TournamentService.leaveQueue(player)) {
            source.sendSuccess(() -> Component.literal("Left the tournament queue."), false);
            return 1;
        }
        source.sendFailure(Component.literal("You are not in the tournament queue."));
        return 0;
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        TournamentSavedData data = TournamentSavedData.get(server);
        // Refresh snapshot then open the green queue UI for the player who asked.
        if (source.getEntity() instanceof ServerPlayer player) {
            TournamentNetwork.syncQueueTo(player);
            TournamentNetwork.openQueueScreen(player);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Tournament enabled=").append(XenoServerConfig.tournamentEnabled)
                .append(" mode=").append(data.mode().id()).append('\n');
        sb.append("King: ").append(nameOf(server, data.king())).append('\n');
        sb.append("Active match: ")
                .append(data.activeMatchId() == null ? "(none)" : data.activeMatchId())
                .append('\n');
        sb.append("Queue (").append(data.queuedCount()).append('/')
                .append(TournamentService.MAX_QUEUED).append("): ");
        List<UUID> queued = data.queuedSnapshot();
        if (queued.isEmpty()) {
            sb.append("(empty)");
        } else {
            for (int i = 0; i < queued.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(nameOf(server, queued.get(i)));
            }
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int result(CommandSourceStack source, String matchId, ServerPlayer winner)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MinecraftServer server = source.getServer();
        TournamentSavedData data = TournamentSavedData.get(server);
        MatchResult match = data.match(matchId);
        if (match == null) {
            source.sendFailure(Component.literal("Unknown match id '" + matchId + "'."));
            return 0;
        }
        if (match.isComplete()) {
            source.sendFailure(Component.literal("Match " + matchId + " already has a winner."));
            return 0;
        }
        UUID winnerId = winner.getUUID();
        if (!winnerId.equals(match.entrantA()) && !winnerId.equals(match.entrantB())) {
            source.sendFailure(Component.literal(
                    winner.getGameProfile().getName() + " is not an entrant of " + matchId + "."));
            return 0;
        }
        ServerPlayer reporter = source.getEntity() instanceof ServerPlayer sp ? sp : null;
        TournamentService.reportResult(server, matchId, winnerId, reporter);
        String awardNote = XenoServerConfig.tournamentEnabled
                ? " (angel awarded if winner online; source tournament:" + matchId + ")"
                : " (angel award skipped — tournamentEnabled=false)";
        source.sendSuccess(() -> Component.literal(
                "Recorded winner of " + matchId + ": " + winner.getGameProfile().getName()
                        + awardNote), true);
        return 1;
    }

    private static int arenas(CommandSourceStack source) {
        TournamentSavedData data = TournamentSavedData.get(source.getServer());
        List<TournamentArenaRegion> regions = data.arenasSnapshot();
        StringBuilder sb = new StringBuilder();
        sb.append("Out-of-bounds auto-lose: ").append(XenoServerConfig.tournamentOutOfBoundsLose)
                .append(" (follow-on; geometry only for now)\n");
        if (!regions.isEmpty()) {
            sb.append("Saved fight cells (").append(regions.size()).append("):\n");
            for (int i = 0; i < regions.size(); i++) {
                sb.append("  ").append(regions.get(i).describe(i)).append('\n');
            }
        } else {
            sb.append("No SavedData fight cells — falling back to config positions.\n");
            List<BlockPos> positions = TournamentService.parseArenaPositions(
                    XenoServerConfig.tournamentArenaPositions);
            sb.append("Config dimension: ").append(XenoServerConfig.tournamentArenaDimension).append('\n');
            sb.append("Config positions (").append(positions.size()).append("):\n");
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                sb.append("  [").append(i).append("] ")
                        .append(pos.getX()).append(',').append(pos.getY()).append(',')
                        .append(pos.getZ()).append('\n');
            }
            sb.append("Tip: /xenotourney arena locate cell_arena  then  arena bindcell\n");
            sb.append("Or WorldEdit //wand select then /xenotourney arena setfromwe");
        }
        source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return 1;
    }

    private static int arenaLocate(CommandSourceStack source, String structureId) {
        ResourceKey<Structure> key = resolveDmzStructure(structureId);
        if (key == null) {
            source.sendFailure(Component.literal(
                    "Unknown DMZ structure '" + structureId + "'. Try cell_arena. /xenostructure list"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());
        BlockPos found = StructureLocator.locateStructure(level, key, origin);
        if (found == null) {
            source.sendFailure(Component.literal(
                    "Not found nearby: " + key.location()
                            + ". Place with /xenostructure place cell_arena"));
            return 0;
        }
        TournamentArenaRegion preview = TournamentArenaRegion.aroundCellArena(
                level.dimension().location().toString(), found);
        source.sendSuccess(() -> Component.literal(
                "Found " + key.location() + " at "
                        + found.getX() + " " + found.getY() + " " + found.getZ() + "\n"
                        + "Suggested fight cell (±" + TournamentArenaRegion.CELL_PAD_XZ
                        + " xz): " + preview.describe(0) + "\n"
                        + "Bind with /xenotourney arena bindcell"), false);
        return 1;
    }

    private static int arenaBindCell(CommandSourceStack source, int index) {
        ResourceKey<Structure> key = resolveDmzStructure("cell_arena");
        if (key == null) {
            source.sendFailure(Component.literal("DMZ cell_arena structure key missing."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos found = StructureLocator.locateStructure(
                level, key, BlockPos.containing(source.getPosition()));
        if (found == null) {
            source.sendFailure(Component.literal(
                    "cell_arena not found. /xenostructure place cell_arena then retry."));
            return 0;
        }
        TournamentArenaRegion region = TournamentArenaRegion.aroundCellArena(
                level.dimension().location().toString(), found);
        TournamentSavedData data = TournamentSavedData.get(source.getServer());
        data.setArena(index, region);
        List<TournamentArenaRegion> snap = data.arenasSnapshot();
        int shown = Math.min(Math.max(0, index < 0 ? snap.size() - 1 : index), snap.size() - 1);
        source.sendSuccess(() -> Component.literal(
                "Bound cell_arena fight cell: " + snap.get(shown).describe(shown)
                        + "\nRefine bounds with WorldEdit + /xenotourney arena setfromwe "
                        + shown), true);
        return 1;
    }

    private static int arenaSetFromWe(CommandSourceStack source, int index)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!WorldEditBridge.available()) {
            source.sendFailure(Component.literal(
                    "WorldEdit is not installed — use //wand or /xenotourney arena bindcell."));
            return 0;
        }
        WorldEditBridge.Bounds3d bounds = WorldEditBridge.getSelectionBounds3d(
                player, player.serverLevel());
        if (bounds == null) {
            source.sendFailure(Component.literal(
                    "Make a complete WorldEdit selection first (//pos1 //pos2 or wand)."));
            return 0;
        }
        BlockPos spawn = player.blockPosition();
        String dim = player.level().dimension().location().toString();
        TournamentArenaRegion region = TournamentArenaRegion.fromWorldEdit(
                dim, "we_selection", spawn, bounds.min(), bounds.max());
        TournamentSavedData data = TournamentSavedData.get(source.getServer());
        data.setArena(index, region);
        List<TournamentArenaRegion> snap = data.arenasSnapshot();
        int shown = Math.min(Math.max(0, index < 0 ? snap.size() - 1 : index), snap.size() - 1);
        source.sendSuccess(() -> Component.literal(
                "Arena from WorldEdit: " + snap.get(shown).describe(shown)
                        + "\nSpawn = your feet (adjust with /xenotourney arena setspawn "
                        + shown + ")"), true);
        return 1;
    }

    private static int arenaSetSpawn(CommandSourceStack source, int index)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TournamentSavedData data = TournamentSavedData.get(source.getServer());
        if (data.arenaCount() == 0) {
            source.sendFailure(Component.literal(
                    "No arenas saved. /xenotourney arena bindcell or setfromwe first."));
            return 0;
        }
        int i = index < 0 ? data.arenaCount() - 1 : index;
        TournamentArenaRegion current = data.arena(i);
        if (current == null) {
            source.sendFailure(Component.literal("No arena at index " + i + "."));
            return 0;
        }
        TournamentArenaRegion next = current.withSpawn(player.blockPosition());
        data.setArena(i, next);
        source.sendSuccess(() -> Component.literal(
                "Spawn updated: " + next.describe(i)), true);
        return 1;
    }

    private static int arenaClear(CommandSourceStack source, int index) {
        TournamentSavedData data = TournamentSavedData.get(source.getServer());
        if (!data.clearArena(index)) {
            source.sendFailure(Component.literal("No arena at index " + index + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Cleared arena [" + index + "]."), true);
        return 1;
    }

    private static int arenaClearAll(CommandSourceStack source) {
        TournamentSavedData data = TournamentSavedData.get(source.getServer());
        data.clearAllArenas();
        source.sendSuccess(() -> Component.literal(
                "Cleared all SavedData fight cells (config positions remain as fallback)."), true);
        return 1;
    }

    private static ResourceKey<Structure> resolveDmzStructure(String id) {
        if (id == null || id.isBlank()) return null;
        String want = id.trim().toLowerCase(Locale.ROOT);
        if (want.contains(":")) {
            want = want.substring(want.indexOf(':') + 1);
        }
        try {
            for (Field field : DMZStructures.class.getFields()) {
                if (!ResourceKey.class.isAssignableFrom(field.getType())) continue;
                @SuppressWarnings("unchecked")
                ResourceKey<Structure> key = (ResourceKey<Structure>) field.get(null);
                if (key == null || key.location() == null) continue;
                if (want.equals(key.location().getPath().toLowerCase(Locale.ROOT))) {
                    return key;
                }
            }
        } catch (Throwable t) {
            XenoPixelsMod.LOGGER.warn("resolveDmzStructure failed: {}", t.toString());
        }
        return null;
    }

    private static int start(CommandSourceStack source) {
        Optional<String> matchId = TournamentService.tryStartChallenge(source.getServer());
        if (matchId.isPresent()) {
            source.sendSuccess(() -> Component.literal(
                    "Started challenge " + matchId.get() + "."), true);
            return 1;
        }
        source.sendFailure(Component.literal(
                "Could not start a challenge (disabled, active match, or missing king/challenger)."));
        return 0;
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        if (id == null) return "(none)";
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        return id.toString();
    }
}
