package net.bullettrain.xenopixelsmod.space.universe;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetField;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetActivationPolicy;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Applies {@link UniverseGrowth} on the server (switch {@link XenoSpaceConfig#universeGrowth()}, default off): the timer
 * check runs every 100 ticks and reveals at most one system per game day; the discovery check runs every 40 ticks for
 * players in the Voidsea. Each finite reveal is charted; ordinary discoveries can additionally unlock a deterministically
 * addressed, bounded logical system region. Physical planet bodies remain under the separate proximity activation caps.
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
            // Own cell always (fly-into-the-cell); with universeDiscoveryRange > 0, also a nearby star in the 3x3.
            // The daily and total caps are re-checked before every single reveal.
            discovery:
            for (ServerPlayer player : level.players()) {
                for (var cell : UniverseGrowth.discoveryCandidates(seed, player.getX(), player.getZ(),
                        XenoSpaceConfig.universeDiscoveryRange(), data::isRevealed)) {
                    if (!UniverseGrowth.mayDiscover(data.discoveredToday, XenoSpaceConfig.universeDiscoveriesPerDay(), data.size(),
                            XenoSpaceConfig.universeMaxRevealed())) break discovery;
                    if (data.reveal(cell, UniverseSavedData.Source.DISCOVERY, day, seed)) {
                        data.discoveredToday++;
                        announce(level, seed, cell, "discovered by " + player.getGameProfile().getName());
                        bridgeOrdinaryDiscovery(level, seed, cell, day, player.getGameProfile().getName());
                    }
                }
            }
        }
    }

    private static void announce(ServerLevel level, long seed, UniverseField.Cell cell, String how) {
        var system = UniverseField.systemAt(seed, cell);
        if (system == null) return;
        var slots = SmallPlanetField.summarizeSlots(system);
        // Chart success is green, but the wording makes no promise that the system is physically enterable.
        String text = DiscoveryAnnouncement.charted(system.name(), system.starClass().name(), slots.systemPlanetSlots(),
                slots.smallWorldSlots(), slots.unmappedSystemSlots(), cell.id(), how,
                SmallPlanetActivationPolicy.discoveryNote(SmallPlanetPhysicalizer.activationGate()));
        XenoPixelsMod.LOGGER.info("Universe: {}", text);
        for (ServerPlayer player : level.players())
            player.displayClientMessage(Component.literal(text).withStyle(DiscoveryAnnouncement.successColor()), false);
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
        var frontier = frontierSnapshot(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, frontier);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, snapshot(player.getServer()));
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(player, frontierSnapshot(player.getServer()));
            net.bullettrain.xenopixelsmod.space.SpaceVisualNetwork.sendFallenStarsSettings(player);
        }
    }

    public static UniverseFrontierSnapshot frontierSnapshot(MinecraftServer server) {
        ServerLevel level = voidsea(server);
        var frontierData = level == null ? null : FrontierBindings.get(level);
        var bindings = frontierData == null || frontierData.fieldSeedMismatch()
                ? java.util.Map.<InfiniteUniverseField.Cell, UniverseField.Cell>of() : frontierData.entries();
        return UniverseFrontierSnapshot.from(fieldSeed(server), XenoSpaceConfig.universeWarp(), bindings);
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
        if (!UniverseSavedData.get(level).isRevealed(cell)) {
            var logical = FrontierBindings.get(level).logicalCell(cell);
            if (logical != null) return warpLogical(player, logical.x(), logical.z());
            return "System " + cell.id() + " is not known yet.";
        }
        var system = UniverseField.systemAt(fieldSeed(player.getServer()), cell);
        if (system == null) return "Cell " + cell.id() + " has no system.";
        player.teleportTo(level, system.x() + 3000, system.y(), system.z(), player.getYRot(), player.getXRot());
        return "Warped to " + system.name() + " (" + cell.id() + ").";
    }

    /**
     * Materializes a revealed long-coordinate system in an append-only vacant finite-field cell, then warps to it.
     * The physical slot is a bounded backend; the Atlas/logical identity remains the original signed-long address.
     */
    public static String warpLogical(ServerPlayer player, long x, long z) {
        if (!XenoSpaceConfig.logicalUniverseEnabled()) return "Logical universe generation is disabled.";
        if (!XenoSpaceConfig.universeWarp()) return "Universe warp is disabled on this server.";
        if (!player.hasPermissions(2)) return "You are not allowed to warp.";
        if (UniverseField.Cell.inRange(x) && UniverseField.Cell.inRange(z))
            return warp(player, new UniverseField.Cell((int) x, (int) z));

        ServerLevel level = voidsea(player.getServer());
        if (level == null) return "No Voidsea.";
        var logical = new InfiniteUniverseField.Cell(x, z);
        long seed = fieldSeed(player.getServer());
        var logicalData = InfiniteUniverseSavedData.get(level);
        if (!logicalData.isRevealed(logical)) return "Logical system " + logical.id() + " is not known yet.";
        var source = InfiniteUniverseField.systemAt(seed, logical);
        if (source == null) return "Logical cell " + logical.id() + " has no system.";

        var bindings = FrontierBindings.get(level);
        int physicalCap = Math.min(XenoSpaceConfig.universeMaxRevealed(), FrontierBindings.MAX_BINDINGS);
        FrontierBindings.BindResult bound = bindings.bind(seed, logical, physicalCap);
        if (bound != FrontierBindings.BindResult.ADDED && bound != FrontierBindings.BindResult.ALREADY_BOUND)
            return switch (bound) {
                case EMPTY_LOGICAL_CELL -> "Logical cell " + logical.id() + " has no system.";
                case CAP_REACHED -> "Logical physical-region cap reached (" + physicalCap + ").";
                case NO_VACANT_SLOT -> "No vacant physical system region remains in the finite Voidsea field.";
                case FIELD_SEED_MISMATCH -> "Physical logical-system bindings belong to a different universe seed; refusing to reuse them.";
                case ALLOCATION_LOCKED -> "Physical logical-system allocation is locked because saved bindings need operator inspection; existing mapped systems remain available.";
                case ADDED, ALREADY_BOUND -> throw new IllegalStateException("unreachable binding result");
            };

        if (bound == FrontierBindings.BindResult.ADDED) {
            var frontier = frontierSnapshot(player.getServer());
            for (ServerPlayer viewer : player.getServer().getPlayerList().getPlayers())
                net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(viewer, frontier);
        }

        var physicalCell = bindings.physicalCell(logical);
        var physicalSystem = bindings.systemAt(seed, physicalCell);
        if (physicalSystem == null) return "The saved physical region could not resolve this logical system.";
        player.teleportTo(level, physicalSystem.x() + 3000, physicalSystem.y(), physicalSystem.z(),
                player.getYRot(), player.getXRot());
        return "Warped to " + source.name() + " (logical " + logical.id() + ", physical slot "
                + physicalCell.id() + ").";
    }

    /**
     * Player travel to an already discovered, already bound logical system. Unlike {@link #warpLogical}, this route
     * never allocates a physical slot and accepts only ordinary discoveries, so a player cannot turn an arbitrary
     * signed-long coordinate into a destination.
     */
    public static String travelDiscoveredLogical(ServerPlayer player, long x, long z) {
        ServerLevel level = voidsea(player.getServer());
        if (level == null) return "No Voidsea.";

        var logical = new InfiniteUniverseField.Cell(x, z);
        var logicalData = InfiniteUniverseSavedData.get(level);
        var entry = logicalData.entries().get(logical);
        var bindings = FrontierBindings.get(level);
        boolean seedMatches = !bindings.fieldSeedMismatch();
        UniverseField.Cell physical = seedMatches ? bindings.physicalCell(logical) : null;
        UniverseField.StarSystem system = physical == null ? null : bindings.systemAt(fieldSeed(player.getServer()), physical);
        var refusal = LogicalDiscoveryBridge.travelRefusal(XenoSpaceConfig.logicalUniverseEnabled(),
                XenoSpaceConfig.universeWarp(), player.serverLevel() == level, entry, seedMatches,
                physical != null, system != null);
        return switch (refusal) {
            case LOGICAL_DISABLED -> "Logical universe generation is disabled.";
            case WARP_DISABLED -> "Universe travel is disabled on this server.";
            case NOT_IN_VOIDSEA -> "Logical-system travel is only available from the Voidsea.";
            case NOT_DISCOVERED -> "Logical system " + logical.id() + " has not been discovered.";
            case CHART_ONLY -> "Logical system " + logical.id() + " is chart-only; it is not an ordinary physical discovery.";
            case SEED_MISMATCH -> "Physical logical-system bindings belong to a different universe seed.";
            case UNBOUND -> "Logical system " + logical.id() + " is charted but has no physical region.";
            case UNRESOLVED -> "The saved physical region could not resolve logical system " + logical.id() + ".";
            case NONE -> travelToBoundSystem(player, level, logical, physical, system);
        };
    }

    private static String travelToBoundSystem(ServerPlayer player, ServerLevel level,
                                               InfiniteUniverseField.Cell logical, UniverseField.Cell physical,
                                               UniverseField.StarSystem system) {
        Vec3 arrival = null;
        // Try four deterministic points around the star. Edge cells can put the eastern point outside the world border.
        double distance = 3000.0;
        Vec3[] candidates = {
                new Vec3(system.x() + distance, system.y(), system.z()),
                new Vec3(system.x() - distance, system.y(), system.z()),
                new Vec3(system.x(), system.y(), system.z() + distance),
                new Vec3(system.x(), system.y(), system.z() - distance)
        };
        for (Vec3 candidate : candidates) {
            if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(candidate))) continue;
            AABB movedBounds = player.getBoundingBox().move(candidate.subtract(player.position()));
            if (level.noCollision(player, movedBounds)) { arrival = candidate; break; }
        }
        if (arrival == null) return "Logical system " + logical.id()
                + " is bound, but no unobstructed arrival point is inside the Voidsea world border.";
        player.teleportTo(level, arrival.x, arrival.y, arrival.z, player.getYRot(), player.getXRot());
        return "Travelled to discovered logical system " + logical.id() + " (" + system.name()
                + ", physical slot " + physical.id() + ").";
    }

    private static void bridgeOrdinaryDiscovery(ServerLevel level, long seed, UniverseField.Cell source,
                                                 long day, String discoverer) {
        if (!XenoSpaceConfig.logicalUniverseEnabled()) return;
        var logicalData = InfiniteUniverseSavedData.get(level);
        var bindings = FrontierBindings.get(level);
        var logical = LogicalDiscoveryBridge.findOccupiedAddress(seed, source, candidate -> {
            var existing = logicalData.entries().get(candidate);
            return existing == null || existing.source() == InfiniteUniverseSavedData.Source.DISCOVERY;
        });
        if (logical == null) {
            announceLogicalStatus(level, "Logical frontier not extended from cell " + source.id()
                    + ": no available occupied address in the bounded candidate probe.", false);
            return;
        }
        var star = InfiniteUniverseField.systemAt(seed, logical);
        var revealed = logicalData.reveal(logical.x(), logical.z(), InfiniteUniverseSavedData.Source.DISCOVERY,
                day, XenoSpaceConfig.universeMaxRevealed());
        if (revealed == InfiniteUniverseSavedData.RevealResult.CAP_REACHED) {
            announceLogicalStatus(level, "Logical frontier not extended: logical chart cap reached; finite discovery remains saved.", false);
            return;
        }
        syncLogicalReveal(level, seed, logical);

        int physicalCap = Math.min(XenoSpaceConfig.universeMaxRevealed(), FrontierBindings.MAX_BINDINGS);
        FrontierBindings.BindResult result = bindings.bind(seed, logical, physicalCap);
        if (result != FrontierBindings.BindResult.ADDED && result != FrontierBindings.BindResult.ALREADY_BOUND) {
            announceLogicalStatus(level, "Logical system " + logical.id() + " charted from cell " + source.id()
                    + ", but not physically bound: " + bindRefusal(result) + ".", false);
            return;
        }
        if (result == FrontierBindings.BindResult.ADDED) syncFrontier(level.getServer());
        UniverseField.Cell physical = bindings.physicalCell(logical);
        UniverseField.StarSystem hosted = physical == null ? null : bindings.systemAt(seed, physical);
        if (physical == null || hosted == null) {
            announceLogicalStatus(level, "Logical system " + logical.id()
                    + " is charted and bound, but its physical region could not be resolved yet.", false);
            return;
        }
        var readiness = LogicalDiscoveryBridge.discoveryAnnouncementReadiness(
                XenoSpaceConfig.logicalUniverseEnabled(), XenoSpaceConfig.universeWarp(),
                logicalData.entries().get(logical), !bindings.fieldSeedMismatch(), physical != null, hosted != null);
        if (readiness != LogicalDiscoveryBridge.TravelRefusal.NONE) {
            announceLogicalStatus(level, "Logical system " + logical.id() + " (" + star.name()
                    + ") is charted and bound to physical slot " + physical.id()
                    + ", but logical travel is unavailable: " + discoveryTravelRefusal(readiness) + ".", false);
            return;
        }
        String text = "Successful discovery: " + star.name() + " (logical " + logical.id()
                + ") is bound for logical travel from the Voidsea; physical slot " + physical.id()
                + ". Use /xenospace universe logical travel " + logical.x() + " " + logical.z()
                + SmallPlanetActivationPolicy.discoveryNote(SmallPlanetPhysicalizer.activationGate())
                + ". Discovered by " + discoverer + ".";
        announceLogicalStatus(level, text, true);
    }

    private static String discoveryTravelRefusal(LogicalDiscoveryBridge.TravelRefusal refusal) {
        return switch (refusal) {
            case LOGICAL_DISABLED -> "logical universe generation is disabled";
            case WARP_DISABLED -> "universe warp is disabled";
            case NOT_IN_VOIDSEA -> "travel must start in the Voidsea";
            case NOT_DISCOVERED -> "the discovery record is unavailable";
            case CHART_ONLY -> "this system is charted but was not discovered by exploration";
            case SEED_MISMATCH -> "saved frontier data belongs to a different universe seed";
            case UNBOUND -> "no physical region is bound";
            case UNRESOLVED -> "the physical region could not be resolved";
            case NONE -> throw new IllegalArgumentException("no refusal reason for a ready discovery");
        };
    }

    private static String bindRefusal(FrontierBindings.BindResult result) {
        return switch (result) {
            case CAP_REACHED -> "physical-region cap reached (" + Math.min(XenoSpaceConfig.universeMaxRevealed(),
                    FrontierBindings.MAX_BINDINGS) + ")";
            case NO_VACANT_SLOT -> "no vacant in-border physical slot remains";
            case FIELD_SEED_MISMATCH -> "saved mappings belong to a different field seed";
            case ALLOCATION_LOCKED -> "saved mapping data is locked for operator inspection";
            case EMPTY_LOGICAL_CELL -> "logical address no longer resolves to an occupied star";
            case ADDED, ALREADY_BOUND -> throw new IllegalArgumentException("not a binding refusal: " + result);
        };
    }

    private static void syncLogicalReveal(ServerLevel level, long seed, InfiniteUniverseField.Cell logical) {
        InfiniteUniversePageService.markRevealed(seed, logical.x(), logical.z());
        var reveal = new InfiniteUniverseCellReveal(seed, logical.x(), logical.z());
        for (ServerPlayer viewer : level.getServer().getPlayerList().getPlayers())
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(viewer, reveal);
    }

    private static void syncFrontier(MinecraftServer server) {
        var frontier = frontierSnapshot(server);
        for (ServerPlayer viewer : server.getPlayerList().getPlayers())
            net.bullettrain.xenopixelsmod.api.network.AddonNetwork.sendToPlayer(viewer, frontier);
    }

    private static void announceLogicalStatus(ServerLevel level, String text, boolean successfulExplorable) {
        XenoPixelsMod.LOGGER.info("Universe logical frontier: {}", text);
        var component = Component.literal(text);
        if (successfulExplorable) component = component.withStyle(ChatFormatting.GREEN);
        for (ServerPlayer player : level.players()) player.displayClientMessage(component, false);
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
            case ADDED -> "Successful discovery: " + system.name() + " (" + x + ":" + z
                    + "). Logical chart entry saved; use /xenospace universe logical warp " + x + " " + z
                    + " to enter its capped physical region.";
            case ALREADY_REVEALED -> "Logical system " + system.name() + " (" + x + ":" + z + ") is already known.";
            case CAP_REACHED -> "Logical system was not recorded: configured discovery cap reached.";
        };
    }
}
