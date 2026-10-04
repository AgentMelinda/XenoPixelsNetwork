package net.bullettrain.xenopixelsmod.features.tournament;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.features.playerrole.PlayerRoleId;
import net.bullettrain.xenopixelsmod.features.playerrole.PlayerRoleService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Server authority for queue + KotH tournament lifecycle.
 *
 * <p>v1 results are admin-only via {@link #reportResult}. Angel awards run only after a valid
 * server-side commit when {@link XenoServerConfig#tournamentEnabled} is true. Does not subscribe
 * to death events (auto-KO is a follow-on PR).
 */
public final class TournamentService {
    public static final int MAX_QUEUED = 12;

    private static final AtomicLong MATCH_SEQ = new AtomicLong();

    /**
     * Package-visible test hook. When non-null, receives {@code (winnerId, "tournament:"+matchId)}
     * instead of calling {@link PlayerRoleService#grant}. Production leaves this null.
     */
    static BiConsumer<UUID, String> awardHook;

    private TournamentService() {
    }

    /** @return false if disabled, full, or already queued */
    public static boolean enqueue(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        if (!XenoServerConfig.tournamentEnabled) {
            player.sendSystemMessage(Component.literal(
                    "Tournament is disabled on this server (tournamentEnabled=false)."));
            return false;
        }
        TournamentSavedData data = TournamentSavedData.get(player.getServer());
        boolean ok = enqueueInto(data, player.getUUID(), true);
        if (ok) {
            TournamentNetwork.syncQueueTo(player);
            XenoPixelsMod.LOGGER.info("Tournament enqueue {}", player.getGameProfile().getName());
        }
        return ok;
    }

    public static boolean leaveQueue(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        TournamentSavedData data = TournamentSavedData.get(player.getServer());
        boolean ok = data.leaveQueue(player.getUUID());
        if (ok) {
            TournamentNetwork.syncQueueTo(player);
        }
        return ok;
    }

    /**
     * King challenges next queued challenger when idle.
     * Prefers SavedData {@link TournamentArenaRegion}s when present; else config positions.
     *
     * @return match id when a challenge starts
     */
    public static Optional<String> tryStartChallenge(MinecraftServer server) {
        if (server == null || !XenoServerConfig.tournamentEnabled) return Optional.empty();
        TournamentSavedData data = TournamentSavedData.get(server);
        if (data.hasActiveMatch()) return Optional.empty();

        UUID king = data.king();
        if (king == null) {
            king = data.pollQueue();
            if (king == null) return Optional.empty();
            data.setKing(king);
        }

        UUID challenger = data.pollQueue();
        if (challenger == null) return Optional.empty();
        if (challenger.equals(king)) {
            // Should not happen; put back and bail.
            data.enqueue(challenger);
            return Optional.empty();
        }

        List<TournamentArenaRegion> regions = data.arenasSnapshot();
        int arenaCount = regions.isEmpty()
                ? Math.max(1, parseArenaPositions(XenoServerConfig.tournamentArenaPositions).size())
                : regions.size();
        int arenaIndex = data.nextArenaIndex() % arenaCount;
        data.setNextArenaIndex(arenaIndex + 1);

        String matchId = startMatchForTest(data, king, challenger, arenaIndex);
        teleportEntrants(server, king, challenger, arenaIndex, regions);
        TournamentNetwork.broadcastQueue(server);
        XenoPixelsMod.LOGGER.info("Tournament match {} start king={} challenger={} arena={}",
                matchId, king, challenger, arenaIndex);
        return Optional.of(matchId);
    }

    /**
     * Admin-only v1 result commit. After validity checks succeed, awards {@link PlayerRoleId#ANGEL}
     * with source {@code tournament:&lt;matchId&gt;} when tournament config is enabled.
     */
    public static void reportResult(MinecraftServer server, String matchId, UUID winner,
                                    ServerPlayer reporter) {
        if (server == null || matchId == null || winner == null) return;
        TournamentSavedData data = TournamentSavedData.get(server);
        String source = reporter == null
                ? "admin:unknown"
                : "admin:" + reporter.getGameProfile().getName();
        boolean committed = reportResultInto(data, matchId, winner, source);
        if (committed) {
            ServerPlayer winnerPlayer = server.getPlayerList().getPlayer(winner);
            tryAwardAngel(winner, matchId, winnerPlayer, XenoServerConfig.tournamentEnabled);
        }
        TournamentNetwork.broadcastQueue(server);
        XenoPixelsMod.LOGGER.info("Tournament match {} winner={} source={} awarded={}",
                matchId, winner, source, committed && XenoServerConfig.tournamentEnabled);
    }

    public static List<UUID> queuedSnapshot(MinecraftServer server) {
        if (server == null) return List.of();
        return TournamentSavedData.get(server).queuedSnapshot();
    }

    // --- package-visible helpers for unit tests (no live MinecraftServer) ---

    static boolean enqueueInto(TournamentSavedData data, UUID id) {
        return enqueueInto(data, id, true);
    }

    static boolean enqueueInto(TournamentSavedData data, UUID id, boolean enabled) {
        if (data == null || id == null || !enabled) return false;
        if (data.isQueued(id)) return false;
        if (id.equals(data.king()) && data.hasActiveMatch()) return false;
        if (data.queuedCount() >= MAX_QUEUED) return false;
        MatchResult active = data.activeMatchId() == null ? null : data.match(data.activeMatchId());
        if (active != null && !active.isComplete()
                && (id.equals(active.entrantA()) || id.equals(active.entrantB()))) {
            return false;
        }
        return data.enqueue(id);
    }

    static String startMatchForTest(TournamentSavedData data, UUID entrantA, UUID entrantB,
                                    int arenaIndex) {
        String matchId = "m-" + Long.toString(MATCH_SEQ.incrementAndGet(), 36);
        MatchResult match = new MatchResult(matchId, entrantA, entrantB, Math.max(0, arenaIndex),
                null, "");
        data.leaveQueue(entrantA);
        data.leaveQueue(entrantB);
        data.putActiveMatch(match);
        return matchId;
    }

    /**
     * Commits a match result when the match exists, is open, and {@code winner} is an entrant.
     *
     * @return true when the result was committed (award callers must check this first)
     */
    static boolean reportResultInto(TournamentSavedData data, String matchId, UUID winner,
                                    String reportedBy) {
        if (data == null || matchId == null || winner == null) return false;
        MatchResult existing = data.match(matchId);
        if (existing == null) return false;
        if (existing.isComplete()) return false;
        if (!winner.equals(existing.entrantA()) && !winner.equals(existing.entrantB())) return false;

        MatchResult completed = existing.withWinner(winner, reportedBy);
        data.replaceMatch(completed);
        data.setKing(winner);
        // Loser leaves the hill; they may re-queue via /xenotourney join.
        UUID loser = winner.equals(existing.entrantA()) ? existing.entrantB() : existing.entrantA();
        data.leaveQueue(loser);
        return true;
    }

    /**
     * Grants angel when awards are enabled. Uses {@link #awardHook} in unit tests; otherwise
     * {@link PlayerRoleService#grant} when the winner {@link ServerPlayer} is online.
     */
    static void tryAwardAngel(UUID winner, String matchId, ServerPlayer winnerPlayer,
                              boolean awardsEnabled) {
        if (!awardsEnabled || winner == null || matchId == null || matchId.isBlank()) return;
        String source = "tournament:" + matchId;
        if (awardHook != null) {
            awardHook.accept(winner, source);
            return;
        }
        if (winnerPlayer != null) {
            PlayerRoleService.grant(winnerPlayer, PlayerRoleId.ANGEL, source);
        }
    }

    public static List<BlockPos> parseArenaPositions(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of(new BlockPos(0, 64, 0));
        }
        List<BlockPos> out = new ArrayList<>();
        for (String part : raw.split(";")) {
            BlockPos pos = parseBlockPos(part.trim());
            if (pos != null) out.add(pos);
        }
        if (out.isEmpty()) {
            out.add(new BlockPos(0, 64, 0));
        }
        return Collections.unmodifiableList(out);
    }

    private static BlockPos parseBlockPos(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] bits = raw.split(",");
        if (bits.length != 3) return null;
        try {
            int x = Integer.parseInt(bits[0].trim());
            int y = Integer.parseInt(bits[1].trim());
            int z = Integer.parseInt(bits[2].trim());
            return new BlockPos(x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void teleportEntrants(MinecraftServer server, UUID king, UUID challenger,
                                         int arenaIndex, List<TournamentArenaRegion> regions) {
        BlockPos base;
        ServerLevel level;
        if (regions != null && !regions.isEmpty()) {
            TournamentArenaRegion region = regions.get(Math.floorMod(arenaIndex, regions.size()));
            base = region.spawn();
            level = resolveArenaLevel(server, region.dimension());
        } else {
            List<BlockPos> legacy = parseArenaPositions(XenoServerConfig.tournamentArenaPositions);
            if (legacy.isEmpty()) return;
            base = legacy.get(Math.floorMod(arenaIndex, legacy.size()));
            level = resolveArenaLevel(server, XenoServerConfig.tournamentArenaDimension);
        }
        if (level == null || base == null) return;

        ServerPlayer kingPlayer = server.getPlayerList().getPlayer(king);
        ServerPlayer challengerPlayer = server.getPlayerList().getPlayer(challenger);
        if (kingPlayer != null) {
            kingPlayer.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5,
                    kingPlayer.getYRot(), kingPlayer.getXRot());
        }
        if (challengerPlayer != null) {
            // Offset challenger so they are not stacked on the king.
            challengerPlayer.teleportTo(level, base.getX() + 3.5, base.getY(), base.getZ() + 0.5,
                    challengerPlayer.getYRot(), challengerPlayer.getXRot());
        }
    }

    private static ServerLevel resolveArenaLevel(MinecraftServer server, String dim) {
        if (dim == null || dim.isBlank()) {
            return server.getLevel(Level.OVERWORLD);
        }
        ResourceLocation id = ResourceLocation.tryParse(dim.trim().toLowerCase(Locale.ROOT));
        if (id == null) {
            return server.getLevel(Level.OVERWORLD);
        }
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
        ServerLevel level = server.getLevel(key);
        return level != null ? level : server.getLevel(Level.OVERWORLD);
    }

    /**
     * True when the player is outside the active match arena bounds.
     * Used for future out-of-bounds lose; v1 only exposes the check.
     */
    public static boolean isOutsideActiveArena(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        TournamentSavedData data = TournamentSavedData.get(player.getServer());
        if (!data.hasActiveMatch()) return false;
        MatchResult match = data.match(data.activeMatchId());
        if (match == null) return false;
        UUID id = player.getUUID();
        if (!id.equals(match.entrantA()) && !id.equals(match.entrantB())) return false;
        TournamentArenaRegion region = data.arena(match.arenaIndex());
        if (region == null) return false;
        if (!TournamentArenaRegion.sameDimension(region.dimension(), player.level().dimension().location())) {
            return true;
        }
        return !region.contains(player.position());
    }
}
