package net.bullettrain.xenopixelsmod.space.universe;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Applies {@link UniverseGrowth} on the server (switch {@link XenoSpaceConfig#universeGrowth()}, default off): the timer
 * check runs every 100 ticks and reveals at most one system per game day; the discovery check runs every 40 ticks for
 * players in the Voidsea. Each reveal is announced to Voidsea players. Cost: a few cell hashes per check.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class UniverseService {
    private static final int TIMER_FRONTIER_REBUILD_CELLS_PER_TICK = 512;

    private UniverseService() {}

    public static ServerLevel voidsea(MinecraftServer server) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
    }

    public static long fieldSeed(MinecraftServer server) {
        return UniverseField.mix(server.overworld().getSeed() ^ 0x0B5E_55ED_C0FFEEL);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int t = server.getTickCount();
        var mode = UniverseGrowth.Mode.parse(XenoSpaceConfig.universeGrowth());
        if (mode == UniverseGrowth.Mode.OFF) return;
        if (t % 40 != 0 && !mode.timer()) return;
        ServerLevel level = voidsea(server);
        if (level == null) return;
        var data = UniverseSavedData.get(level);
        long seed = fieldSeed(server);
        if (mode.timer() && data.size() < XenoSpaceConfig.universeMaxRevealed())
            data.advanceTimerFrontier(seed, TIMER_FRONTIER_REBUILD_CELLS_PER_TICK);
        if (t % 40 != 0) return;

        long day = UniverseGrowth.day(server.overworld().getGameTime());
        if (mode.timer() && data.timerFrontierReady() && t % 100 == 0
                && day != data.lastTimerDay && data.size() < XenoSpaceConfig.universeMaxRevealed()) {
            boolean first = data.lastTimerDay == Long.MIN_VALUE;
            data.lastTimerDay = day; data.setDirty();
            if (!first) {
                var cell = data.nextTimerCell(seed);
                if (cell != null && data.reveal(cell, UniverseSavedData.Source.TIMER, day, seed)) announce(level, seed, cell, "detected");
            }
        }
        if (mode.discovery()) {
            if (data.discoveryDay != day) { data.discoveryDay = day; data.discoveredToday = 0; data.setDirty(); }
            for (ServerPlayer player : level.players()) {
                var cell = UniverseField.cellAt(player.getX(), player.getZ());
                if (cell == null || data.isRevealed(cell) || UniverseField.systemAt(seed, cell) == null) continue;
                if (!UniverseGrowth.mayDiscover(data.discoveredToday, XenoSpaceConfig.universeDiscoveriesPerDay(), data.size(),
                        XenoSpaceConfig.universeMaxRevealed())) break;
                if (data.reveal(cell, UniverseSavedData.Source.DISCOVERY, day, seed)) {
                    data.discoveredToday++;
                    announce(level, seed, cell, "discovered by " + player.getGameProfile().getName());
                }
            }
        }
    }

    private static void announce(ServerLevel level, long seed, UniverseField.Cell cell, String how) {
        var system = UniverseField.systemAt(seed, cell);
        if (system == null) return;
        String text = "New star system " + how + ": " + system.name() + " (" + system.starClass().name().toLowerCase(java.util.Locale.ROOT)
                + ", cell " + cell.id() + ")";
        XenoPixelsMod.LOGGER.info("Universe: {}", text);
        for (ServerPlayer player : level.players()) player.displayClientMessage(Component.literal(text), false);
        var reveal = new UniverseCellReveal(seed, XenoSpaceConfig.universeWarp(), cell);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers())
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, reveal);
        InfiniteUniversePageService.markRevealed(seed, cell.x(), cell.z());
        var logicalReveal = new InfiniteUniverseCellReveal(seed, cell.x(), cell.z());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers())
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, logicalReveal);
    }

    /** The Galaxy Atlas description for clients (field seed + revealed cells, capped). */
    public static UniverseSnapshot snapshot(MinecraftServer server) {
        ServerLevel level = voidsea(server);
        var cells = level == null ? java.util.Set.<UniverseField.Cell>of()
                : UniverseSavedData.get(level).revealedCells();
        return new UniverseSnapshot(fieldSeed(server), XenoSpaceConfig.universeWarp(), cells);
    }

    public static void syncAll(MinecraftServer server) {
        var packet = snapshot(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, packet);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, snapshot(player.getServer()));
    }

    /**
     * Warp to a revealed system (owner decision: travel by flight and by warp). Same rule for the command and the Atlas:
     * universeWarp must be on and the player needs permission level 2. Arrives 3,000 blocks from the star on the plane.
     */
    public static String warp(ServerPlayer player, UniverseField.Cell cell) {
        if (!XenoSpaceConfig.universeWarp()) return "Universe warp is disabled on this server.";
        if (!player.hasPermissions(2)) return "You are not allowed to warp.";
        ServerLevel level = voidsea(player.getServer());
        if (level == null) return "No Voidsea.";
        if (!UniverseSavedData.get(level).isRevealed(cell)) return "System " + cell.id() + " is not known yet.";
        var system = UniverseField.systemAt(fieldSeed(player.getServer()), cell);
        if (system == null) return "Cell " + cell.id() + " has no system.";
        player.teleportTo(level, system.x() + 3000, system.y(), system.z(), player.getYRot(), player.getXRot());
        return "Warped to " + system.name() + " (" + cell.id() + ").";
    }

    /** Admin reveal (commands/tests); bypasses the daily limits but not the cap. */
    public static boolean reveal(MinecraftServer server, UniverseField.Cell cell) {
        ServerLevel level = voidsea(server);
        if (level == null) return false;
        var data = UniverseSavedData.get(level);
        long seed = fieldSeed(server);
        if (UniverseField.systemAt(seed, cell) == null || data.size() >= XenoSpaceConfig.universeMaxRevealed()) return false;
        boolean added = data.reveal(cell, UniverseSavedData.Source.ADMIN,
                UniverseGrowth.day(server.overworld().getGameTime()), seed);
        if (added) announce(level, seed, cell, "revealed by an operator");
        return added;
    }

    /** Records an explicitly selected full-width logical address; it does not physically place or travel to that system. */
    public static String revealLogical(MinecraftServer server, long x, long z) {
        if (!XenoSpaceConfig.logicalUniverseEnabled()) return "Logical universe generation is disabled.";
        ServerLevel level = voidsea(server);
        if (level == null) return "No Voidsea.";
        long seed = fieldSeed(server);
        var system = InfiniteUniverseField.systemAt(seed, new InfiniteUniverseField.Cell(x, z));
        if (system == null) return "Logical cell " + x + ":" + z + " is empty.";

        // Preserve the existing compact snapshot/reveal path for all cells that are physically representable today.
        if (UniverseField.Cell.inRange(x) && UniverseField.Cell.inRange(z))
            return reveal(server, new UniverseField.Cell((int) x, (int) z))
                    ? "Revealed " + system.name() + " (" + x + ":" + z + ")."
                    : "System was not added (already known or the configured discovery cap was reached).";

        var data = InfiniteUniverseSavedData.get(level);
        var result = data.reveal(x, z, InfiniteUniverseSavedData.Source.ADMIN,
                UniverseGrowth.day(server.overworld().getGameTime()), XenoSpaceConfig.universeMaxRevealed());
        if (result == InfiniteUniverseSavedData.RevealResult.ADDED) {
            InfiniteUniversePageService.markRevealed(seed, x, z);
            var reveal = new InfiniteUniverseCellReveal(seed, x, z);
            for (ServerPlayer player : server.getPlayerList().getPlayers())
                net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, reveal);
        }
        return switch (result) {
            case ADDED -> "Recorded logical system " + system.name() + " (" + x + ":" + z
                    + "). Logical discovery is saved; distant physical region travel is not wired yet.";
            case ALREADY_REVEALED -> "Logical system " + system.name() + " (" + x + ":" + z + ") is already known.";
            case CAP_REACHED -> "Logical system was not recorded: configured discovery cap reached.";
        };
    }
}
