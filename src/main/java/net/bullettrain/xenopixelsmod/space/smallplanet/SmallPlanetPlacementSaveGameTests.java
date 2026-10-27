package net.bullettrain.xenopixelsmod.space.smallplanet;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Server-only proof of player placement through save-boundary and post-tick pinning into Sable serialization. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmallPlanetPlacementSaveGameTests {
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;

    private SmallPlanetPlacementSaveGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void zeroMassPlayerPlacementIsPinnedBeforeSableSave(GameTestHelper helper) {
        // GameTestServer loads vanilla dimensions only. The package-private fixture seam marks this level
        // as its save-test host without widening the production Voidsea gate.
        ServerLevel host = helper.getLevel();
        helper.assertTrue(host != null, "the GameTest fixture level is available");
        if (host == null) return;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(host);
        PlotBodyBuilder.Result saveBuilt = PlotBodyBuilder.build(host, new Vector3d(64.5, 64.5, 64.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        PlotBodyBuilder.Result tickBuilt = PlotBodyBuilder.build(host, new Vector3d(160.5, 64.5, 64.5), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(saveBuilt != null && saveBuilt.body() != null && tickBuilt != null && tickBuilt.body() != null,
                "the two one-voxel Sable fixture bodies are built");
        if (saveBuilt == null || saveBuilt.body() == null || tickBuilt == null || tickBuilt.body() == null) return;

        ServerSubLevel saveBody = saveBuilt.body();
        ServerSubLevel tickBody = tickBuilt.body();
        // These are test fixtures, not production-managed bodies; hold them across scheduled test ticks
        // so Sable's ordinary unload path cannot remove them before the placement callbacks execute.
        container.addForceLoadTicket(saveBody, KEEP_ALIVE, Unit.INSTANCE);
        container.addForceLoadTicket(tickBody, KEEP_ALIVE, Unit.INSTANCE);
        UUID saveBodyId = saveBody.getUniqueId();
        UUID tickBodyId = tickBody.getUniqueId();
        BlockPos saveSupport = saveBody.getPlot().getCenterBlock();
        BlockPos saveTorchPos = saveSupport.above();
        BlockPos tickSupport = tickBody.getPlot().getCenterBlock();
        BlockPos tickTorchPos = tickSupport.above();
        Vector3d saveCentre = saveBody.logicalPose().position();
        Vector3d tickCentre = tickBody.logicalPose().position();
        var saveCandidate = new SmallPlanetField.Candidate(
                // Sol has only procedural slots 0..3; reserving 6 and 7 makes test identities impossible to generate.
                new SmallPlanetField.ObjectId(new UniverseField.Cell(0, 0), 6, 0, 0),
                0x5A11L, saveCentre.x, saveCentre.y, saveCentre.z, 10, SmallPlanetField.Profile.ROCK, 0.2, false);
        var tickCandidate = new SmallPlanetField.Candidate(
                new SmallPlanetField.ObjectId(new UniverseField.Cell(0, 0), 7, 0, 0),
                0x5A12L, tickCentre.x, tickCentre.y, tickCentre.z, 10, SmallPlanetField.Profile.ROCK, 0.2, false);
        SmallPlanetPhysicalizer.registerUnpinnedPlacementTestBody(host, saveBody, saveCandidate);
        SmallPlanetPhysicalizer.registerUnpinnedPlacementTestBody(host, tickBody, tickCandidate);
        boolean previousCaptureState = host.captureBlockSnapshots;
        try {
            host.captureBlockSnapshots = true;
            helper.assertTrue(SmallPlanetPhysicalizer.hasPendingEditReservations(host),
                    "the fixture dimension passes the capture-window gate before the reservation exists");
        } finally {
            host.captureBlockSnapshots = previousCaptureState;
        }

        double originalSaveMass = saveBody.getMassTracker().getMass();
        double originalTickMass = tickBody.getMassTracker().getMass();
        helper.assertTrue(host.getBlockState(saveSupport).is(Blocks.STONE)
                        && host.getBlockState(tickSupport).is(Blocks.STONE),
                "both placed-block support voxels resolve through their host plots");
        // The isolated GameTest world may be reused across invocations. Clear only the two voxels owned by these
        // newly allocated fixtures so a prior successful run cannot make the next placement look unchanged.
        if (!host.getBlockState(saveTorchPos).isAir())
            host.setBlock(saveTorchPos, Blocks.AIR.defaultBlockState(), 3);
        if (!host.getBlockState(tickTorchPos).isAir())
            host.setBlock(tickTorchPos, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(host.getBlockState(saveTorchPos).isAir() && host.getBlockState(tickTorchPos).isAir(),
                "placement fixture torch voxels were cleared before this run");
        helper.assertTrue(!saveBody.getUserDataTag().getCompound(SmallPlanetPhysicalizer.TAG).getBoolean("touched")
                        && !tickBody.getUserDataTag().getCompound(SmallPlanetPhysicalizer.TAG).getBoolean("touched"),
                "both fixtures start as unpinned zero-mass bodies");

        // FakePlayerFactory caches by (ServerLevel, username) for the process lifetime. A unique profile
        // prevents a second GameTest invocation from reusing the previous run's discarded FakePlayer.
        UUID fakePlayerId = UUID.randomUUID();
        String fakePlayerName = "XPT_" + fakePlayerId.toString().replace("-", "").substring(0, 8);
        FakePlayer player = FakePlayerFactory.get(host,
                new GameProfile(fakePlayerId, fakePlayerName));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(saveSupport.getX() + 0.5, saveSupport.getY() + 1.5, saveSupport.getZ() + 0.5);
        ItemStack saveTorch = new ItemStack(Blocks.TORCH);
        player.setItemInHand(InteractionHand.MAIN_HAND, saveTorch);
        host.addNewPlayer(player);

        AtomicReference<InteractionResult> savePlacementResult = new AtomicReference<>();
        AtomicReference<InteractionResult> tickPlacementResult = new AtomicReference<>();
        AtomicReference<RuntimeException> placementFailure = new AtomicReference<>();
        AtomicReference<CompoundTag> savedSaveBodyUserData = new AtomicReference<>();
        BlockHitResult saveHit = new BlockHitResult(
                new Vec3(saveSupport.getX() + 0.5, saveSupport.getY() + 1.0, saveSupport.getZ() + 0.5),
                Direction.UP, saveSupport, false);
        BlockHitResult tickHit = new BlockHitResult(
                new Vec3(tickSupport.getX() + 0.5, tickSupport.getY() + 1.0, tickSupport.getZ() + 0.5),
                Direction.UP, tickSupport, false);
        helper.runAfterDelay(1, () -> {
            // tell() deliberately queues this after the current Post event, matching the real between-tick player task.
            host.getServer().tell(new TickTask(host.getServer().getTickCount(), () -> {
                try {
                    savePlacementResult.set(player.gameMode.useItemOn(player, host, saveTorch, InteractionHand.MAIN_HAND, saveHit));
                    // Exercise the exact production boundary callback before Sable serialization. Calling
                    // ServerLevel.save synchronously from a GameTest tick blocks in ChunkMap.saveAllChunks
                    // while it waits for chunk work that requires the test server to keep ticking.
                    SmallPlanetPhysicalizer.beforeHostSave(host);
                    savedSaveBodyUserData.set(SubLevelSerializer.toData(saveBody, List.of()).fullTag()
                            .getCompound("user_data").copy());
                    if (Math.abs(saveBody.getMassTracker().getMass() - originalSaveMass) > 1.0e-9)
                        throw new IllegalStateException("zero-mass torch changed save-body Sable mass");
                    player.setPos(tickSupport.getX() + 0.5, tickSupport.getY() + 1.5, tickSupport.getZ() + 0.5);
                    ItemStack tickTorch = new ItemStack(Blocks.TORCH);
                    player.setItemInHand(InteractionHand.MAIN_HAND, tickTorch);
                    tickPlacementResult.set(player.gameMode.useItemOn(player, host, tickTorch, InteractionHand.MAIN_HAND, tickHit));
                } catch (RuntimeException error) {
                    placementFailure.set(error);
                }
            }));
            helper.runAfterDelay(3, () -> {
                try {
                    helper.assertTrue(placementFailure.get() == null,
                            "the queued FakePlayer useItemOn did not throw: " + placementFailure.get());
                    helper.assertTrue(savePlacementResult.get() != null && savePlacementResult.get().consumesAction(),
                            "the save-hook FakePlayer action was accepted");
                    helper.assertTrue(tickPlacementResult.get() != null && tickPlacementResult.get().consumesAction(),
                            "the tick-flush FakePlayer action was accepted");
                    helper.assertTrue(host.getBlockState(saveTorchPos).is(Blocks.TORCH)
                                    && host.getBlockState(tickTorchPos).is(Blocks.TORCH),
                            "both torch placements survived final-state validation");
                    helper.assertTrue(savedSaveBodyUserData.get() != null
                                    && savedSaveBodyUserData.get().getCompound(SmallPlanetPhysicalizer.TAG).getBoolean("touched"),
                            "the host-save boundary hook pinned the save-body before Sable serialization");
                    double massAfter = tickBody.getMassTracker().getMass();
                    helper.assertTrue(Math.abs(massAfter - originalTickMass) <= 1.0e-9,
                            "placing the second torch did not change Sable body mass");
                    helper.assertTrue(tickBody.getUserDataTag().getCompound(SmallPlanetPhysicalizer.TAG).getBoolean("touched"),
                            "the deferred tick flush pinned the second previously unpinned body");

                    SmallPlanetPhysicalizer.beforeHostSave(host);
                    var tickSerialized = SubLevelSerializer.toData(tickBody, List.of()).fullTag();
                    helper.assertTrue(tickSerialized.getCompound("user_data").contains(SmallPlanetPhysicalizer.TAG),
                            "Sable's serialized tick-flush sub-level retains the small-planet identity");
                    helper.assertTrue(tickSerialized.getCompound("user_data").getCompound(SmallPlanetPhysicalizer.TAG)
                                    .getBoolean("touched"),
                            "the ordinary Post-tick flush pins the zero-mass edit before Sable serializes it");
                    XenoPixelsMod.LOGGER.info("Small planet placement-save GameTest PASS: saveBody={} tickBody={} savedTouched={} tickTouched=true",
                            saveBodyId, tickBodyId, savedSaveBodyUserData.get().getCompound(SmallPlanetPhysicalizer.TAG).getBoolean("touched"));
                    helper.succeed();
                } finally {
                    player.discard();
                    SmallPlanetPhysicalizer.unregisterPlacementTestBody(host, saveBody, saveCandidate);
                    SmallPlanetPhysicalizer.unregisterPlacementTestBody(host, tickBody, tickCandidate);
                    if (container.getSubLevel(saveBodyId) instanceof ServerSubLevel live && !live.isRemoved())
                        container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
                    if (container.getSubLevel(tickBodyId) instanceof ServerSubLevel live && !live.isRemoved())
                        container.removeSubLevel(live, SubLevelRemovalReason.REMOVED);
                    container.removeForceLoadTicket(saveBody, KEEP_ALIVE, Unit.INSTANCE);
                    container.removeForceLoadTicket(tickBody, KEEP_ALIVE, Unit.INSTANCE);
                }
            });
        });
    }
}
