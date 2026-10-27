package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.compat.sable.SableLoadingTickets;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.Set;

/** Runtime probe for fail-closed recovery of a tagged body whose logical identity is unreadable. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmallPlanetQuarantineGameTests {
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;

    private SmallPlanetQuarantineGameTests() {}

    /** Dedicated-server-only end-to-end check of the normal admin release path through exact save read-back. */
    @GameTest(template = "empty", timeoutTicks = 120, batch = "xenopixelsmod_small_planet_operator_release_save",
            required = false, manualOnly = true)
    public static void operatorQuarantineReleasePersistsAndPrunesAfterExactSableReadback(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (level == null) {
            helper.fail("This manual integration test requires a normal dedicated server with the Voidsea level");
            return;
        }
        if (!server.getPlayerList().getPlayers().isEmpty()) {
            helper.fail("Run this all-dimensions save test only on an isolated dedicated server with no connected players");
            return;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(12_500.5, 1_000.5, 12_000.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the operator-release Sable fixture builds");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        CompoundTag malformed = new CompoundTag();
        malformed.putLongArray("key", new long[]{1});
        malformed.putBoolean("building", false);
        malformed.putBoolean("touched", true);
        CompoundTag userData = new CompoundTag();
        userData.put(SmallPlanetPhysicalizer.TAG, malformed);
        body.setUserDataTag(userData);
        GlobalSavedSubLevelPointer pointer = null;
        boolean keepAliveAdded = false;
        boolean releaseRecorded = false;
        try {
            container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            keepAliveAdded = true;
            SmallPlanetPhysicalizer.reconcileForGameTest(level);
            helper.assertTrue(SmallPlanetPhysicalizer.quarantineList(server).contains(id.toString()),
                    "production adoption quarantines the fixture's malformed identity before release");

            final int commandResult;
            try {
                commandResult = server.getCommands().getDispatcher().execute(
                        "xenospace smallplanets quarantine release " + id,
                        server.createCommandSourceStack());
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                helper.fail("the registered operator quarantine-release command parses and executes: " + failure.getMessage());
                return;
            }
            helper.assertTrue(commandResult == 1,
                    "the registered admin command executes the production operator release method");
            releaseRecorded = SmallPlanetSavedData.get(level).isQuarantineReleased(id);
            helper.assertTrue(releaseRecorded, "SavedData contains the operator's release decision");
            helper.assertTrue(body.getUserDataTag() != null
                            && body.getUserDataTag().getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "the production release path writes the body fast-path release marker");
            SmallPlanetPhysicalizer.reconcileForGameTest(level);
            helper.assertTrue(SmallPlanetPhysicalizer.isReleaseMarkerAuditQueuedForGameTest(level, id),
                    "adoption queues the operator release for exact Sable storage verification");

            holding.saveAll();
            pointer = body.getLastSerializationPointer();
            helper.assertTrue(pointer != null, "Sable assigns a storage pointer to the operator-released body");
            var beforeFlush = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(beforeFlush != null && beforeFlush.uuid().equals(id)
                            && beforeFlush.fullTag().getCompound("user_data")
                            .getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "the exact Sable slot contains the same UUID and production-written release marker");

            helper.assertTrue(server.saveEverything(true, true, true),
                    "the isolated dedicated-server save flush completes");
            helper.assertFalse(SmallPlanetSavedData.get(level).isQuarantineReleased(id),
                    "the real Voidsea LevelEvent.Save handler prunes the operator decision after exact read-back");
            var afterFlush = holding.getStorage().attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            helper.assertTrue(afterFlush != null && afterFlush.uuid().equals(id)
                            && afterFlush.fullTag().getCompound("user_data")
                            .getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "the exact slot still verifies the released UUID and marker after the production audit");
            helper.assertFalse(SmallPlanetPhysicalizer.isReleaseMarkerAuditQueuedForGameTest(level, id),
                    "the verified operator release leaves the audit queue");
            XenoPixelsMod.LOGGER.info("Small-planet operator release/save/read-back GameTest PASS: uuid={} pointer={}",
                    id, pointer);
            helper.succeed();
        } finally {
            SmallPlanetPhysicalizer.cleanupReleaseMarkerSaveEventGameTest(level, id);
            if (pointer != null) holding.getStorage().attemptSaveSubLevel(pointer, null);
            if (keepAliveAdded) container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            if (!body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            holding.processChanges();
            if (!releaseRecorded) SmallPlanetSavedData.get(level).clearQuarantineReleaseForGameTest(id);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void malformedEditedBodyIsHeldAndCanBeReleasedWithoutDeletion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        helper.assertTrue(level != null, "Voidsea host dimension is loaded for the quarantine probe");
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(12_000.5, 1_000.5, 12_000.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the malformed-identity Sable fixture is built");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        SmallPlanetField.ObjectId repairedIdentity = new SmallPlanetField.ObjectId(
                new UniverseField.Cell(1_234, -4_321), 3, 4, -4);
        CompoundTag malformed = new CompoundTag();
        malformed.putLongArray("key", new long[]{1});
        malformed.putBoolean("building", false);
        malformed.putBoolean("touched", true);
        CompoundTag userData = new CompoundTag();
        userData.put(SmallPlanetPhysicalizer.TAG, malformed);
        body.setUserDataTag(userData);
        try {
            SmallPlanetPhysicalizer.addSmallPlanetTicketForGameTest(level, body, "legacy-orphan");
            SmallPlanetPhysicalizer.reconcileForGameTest(level);
            helper.assertTrue(container.getSubLevel(id) == body && !body.isRemoved(),
                    "adoption does not remove an edited body whose identity cannot be decoded");
            var info = container.getAllTickets().get(id);
            helper.assertTrue(info != null && info.tickets().stream()
                            .anyMatch(ticket -> ("quarantine/" + id).equals(SableLoadingTickets.key(ticket))),
                    "the quarantined body has its UUID-keyed Sable ticket");
            helper.assertTrue(SmallPlanetPhysicalizer.quarantineList(level.getServer()).contains(id.toString()),
                    "the operator listing exposes the quarantined UUID");
            helper.assertTrue(SmallPlanetPhysicalizer.runQuarantineHoldForGameTest(level, 0.05) > 0,
                    "the orbit pass executes the quarantined-body hold");
            String released = SmallPlanetPhysicalizer.releaseQuarantine(level.getServer(), id);
            helper.assertTrue(released.startsWith("Persistently recorded release"), "the admin release path succeeds");
            helper.assertTrue(released.contains("removed 1 Xeno small-planet ticket(s)"),
                    "the operator response reports the exact count of purged Xeno ticket keys");
            helper.assertTrue(body.getUserDataTag().getBoolean(SmallPlanetPhysicalizer.TAG + "_quarantine_released"),
                    "the body carries the fast-path release marker separately from planet identity fields");
            helper.assertTrue(SmallPlanetSavedData.get(level).isQuarantineReleased(id),
                    "the release decision is stored in Xeno-owned SavedData, independently of Sable body serialization");
            helper.assertTrue(SmallPlanetPhysicalizer.smallPlanetTicketKeysForGameTest(level, id).isEmpty(),
                    "release removes every Xeno-owned ticket key, including a stale key from an older version");
            SmallPlanetPhysicalizer.reconcileForGameTest(level);
            helper.assertFalse(SmallPlanetPhysicalizer.quarantineList(level.getServer()).contains(id.toString()),
                    "a subsequent adoption pass honors the persistent release decision");
            var releasedInfo = container.getAllTickets().get(id);
            helper.assertTrue(releasedInfo == null || releasedInfo.tickets().stream()
                            .noneMatch(ticket -> ("quarantine/" + id).equals(SableLoadingTickets.key(ticket))),
                    "a subsequent adoption pass removes any stale quarantine ticket");
            helper.assertTrue(SmallPlanetPhysicalizer.smallPlanetTicketKeysForGameTest(level, id).isEmpty(),
                    "the released malformed body remains free of all Xeno-owned small-planet ticket keys");
            helper.assertTrue(container.getSubLevel(id) == body && !body.isRemoved(),
                    "release removes the hold but leaves the body available to Sable storage");

            SmallPlanetField.Candidate repaired = new SmallPlanetField.Candidate(repairedIdentity, 0x1912L,
                    80_000, 700, -80_000, 10, SmallPlanetField.Profile.ROCK, 0.2, false);
            SmallPlanetPhysicalizer.tagAdoptionGameTestBody(body, repaired, false, false);
            SmallPlanetPhysicalizer.addSmallPlanetTicketForGameTest(level, body, repairedIdentity.bodyId());
            SmallPlanetPhysicalizer.addSmallPlanetTicketForGameTest(level, body, "legacy-restored/" + id);
            SmallPlanetPhysicalizer.reconcileForGameTest(level);
            helper.assertTrue(SmallPlanetPhysicalizer.bodies(level).get(repairedIdentity.bodyId()) == body,
                    "a released body repaired with a unique valid identity resumes canonical management");
            helper.assertTrue(Set.of(repairedIdentity.bodyId()).equals(
                            SmallPlanetPhysicalizer.smallPlanetTicketKeysForGameTest(level, id)),
                    "released canonical reconciliation keeps the identity ticket and purges stale keys");
            SmallPlanetPhysicalizer.addSmallPlanetTicketForGameTest(level, body, "legacy-restored-again/" + id);
            SmallPlanetPhysicalizer.reconcileForGameTest(level);
            helper.assertTrue(Set.of(repairedIdentity.bodyId()).equals(
                            SmallPlanetPhysicalizer.smallPlanetTicketKeysForGameTest(level, id)),
                    "repeated released-body reconciliation preserves the identity ticket and removes stale keys");
            XenoPixelsMod.LOGGER.info("Small-planet quarantine GameTest PASS: uuid={} releaseMarkerHonored=true repairedCanonical=true staleTicketsRemoved=true",
                    id);
            helper.succeed();
        } finally {
            SmallPlanetPhysicalizer.cleanupAdoptionGameTest(level, repairedIdentity, id);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void editedDuplicateLoserTicketIsReplacedAndReleaseDoesNotPromoteIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        helper.assertTrue(level != null, "Voidsea host dimension is loaded for duplicate adoption probe");
        var container = SubLevelContainer.getContainer(level);
        SmallPlanetField.ObjectId identity = new SmallPlanetField.ObjectId(
                new UniverseField.Cell(1_234, -1_234), 7, 4, -4);
        SmallPlanetField.Candidate candidate = new SmallPlanetField.Candidate(identity, 0x5312L,
                80_000, 700, -80_000, 10, SmallPlanetField.Profile.ROCK, 0.2, false);
        ServerSubLevel first = buildTaggedBody(level, new Vector3d(12_000.5, 1_100.5, 12_000.5));
        ServerSubLevel second = buildTaggedBody(level, new Vector3d(12_100.5, 1_100.5, 12_000.5));
        helper.assertTrue(first != null && second != null, "both duplicate Sable body fixtures are built");
        UUID firstId = first.getUniqueId(), secondId = second.getUniqueId();
        try {
            SmallPlanetPhysicalizer.tagAdoptionGameTestBody(first, candidate, true, false);
            SmallPlanetPhysicalizer.tagAdoptionGameTestBody(second, candidate, true, false);
            SmallPlanetPhysicalizer.addSmallPlanetTicketForGameTest(level, first, identity.bodyId());
            SmallPlanetPhysicalizer.addSmallPlanetTicketForGameTest(level, second, identity.bodyId());
            SmallPlanetPhysicalizer.reconcileForGameTest(level);

            ServerSubLevel expectedWinner = firstId.compareTo(secondId) < 0 ? first : second;
            ServerSubLevel expectedLoser = expectedWinner == first ? second : first;
            helper.assertTrue(SmallPlanetPhysicalizer.bodies(level).get(identity.bodyId()) == expectedWinner,
                    "the stable UUID winner owns the active logical identity");
            helper.assertTrue(Set.of("quarantine/" + expectedLoser.getUniqueId()).equals(
                            SmallPlanetPhysicalizer.smallPlanetTicketKeysForGameTest(level, expectedLoser.getUniqueId())),
                    "an edited loser drops its old identity ticket and keeps only its quarantine ticket");

            String released = SmallPlanetPhysicalizer.releaseQuarantine(level.getServer(), expectedLoser.getUniqueId());
            helper.assertTrue(released.startsWith("Persistently recorded release"), "the duplicate loser release is recorded");
            SmallPlanetPhysicalizer.reconcileForGameTest(level);
            helper.assertTrue(SmallPlanetPhysicalizer.bodies(level).get(identity.bodyId()) == expectedWinner,
                    "releasing a duplicate quarantine does not promote it over the deterministic winner");
            helper.assertTrue(SmallPlanetPhysicalizer.smallPlanetTicketKeysForGameTest(level,
                            expectedLoser.getUniqueId()).isEmpty(),
                    "the released duplicate loser has no Xeno force-load ticket");
            helper.assertTrue(Set.of(identity.bodyId()).equals(
                            SmallPlanetPhysicalizer.smallPlanetTicketKeysForGameTest(level, expectedWinner.getUniqueId())),
                    "the canonical winner remains force-loaded under the identity ticket rather than the quarantine key");
            XenoPixelsMod.LOGGER.info("Small-planet duplicate quarantine GameTest PASS: identity={} canonical={} releasedLoserUnheld=true releaseDidNotPromote=true",
                    identity.bodyId(), expectedWinner.getUniqueId());
            helper.succeed();
        } finally {
            SmallPlanetPhysicalizer.cleanupAdoptionGameTest(level, identity, firstId, secondId);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void sableRemovalRequiresCallerToPurgeForceLoadTickets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        helper.assertTrue(level != null, "Voidsea host dimension is loaded for the ticket-removal probe");
        var container = SubLevelContainer.getContainer(level);
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(12_200.5, 1_000.5, 12_000.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the Sable ticket-removal fixture is built");
        ServerSubLevel body = built.body();
        UUID id = body.getUniqueId();
        String key = "remove-probe/" + id;
        try {
            SmallPlanetPhysicalizer.addSmallPlanetTicketForGameTest(level, body, key);
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            helper.assertTrue(container.getAllTickets().containsKey(id),
                    "Sable removeSubLevel leaves the UUID ticket table for callers to purge explicitly");
            helper.assertTrue(SmallPlanetPhysicalizer.removeSmallPlanetTicketForGameTest(level, body, key),
                    "the ticket remains removable while the caller retains the body object");
            helper.assertFalse(container.getAllTickets().containsKey(id),
                    "explicit ticket removal clears the Sable ticket tombstone");
            helper.succeed();
        } finally {
            if (container.getAllTickets().containsKey(id))
                SmallPlanetPhysicalizer.removeSmallPlanetTicketForGameTest(level, body, key);
            if (container.getSubLevel(id) instanceof ServerSubLevel remaining && !remaining.isRemoved())
                container.removeSubLevel(remaining, SubLevelRemovalReason.REMOVED);
        }
    }

    private static ServerSubLevel buildTaggedBody(ServerLevel level, Vector3d position) {
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, position, 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        return built == null ? null : built.body();
    }
}
