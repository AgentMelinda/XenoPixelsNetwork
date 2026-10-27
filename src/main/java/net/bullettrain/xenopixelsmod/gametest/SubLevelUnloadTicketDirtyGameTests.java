package net.bullettrain.xenopixelsmod.gametest;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelTicketInfo;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.SubLevelTicketsSavedData;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.Objects;

/** Checks that an UNLOADED pointer refresh marks Sable's force-load ticket SavedData dirty. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SubLevelUnloadTicketDirtyGameTests {
    private static final SubLevelLoadingTicketType<Unit> KEEP_ALIVE = SubLevelLoadingTicketType.COMMAND_FORCED;

    private SubLevelUnloadTicketDirtyGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void refreshedUnloadPointerMarksForceLoadSavedDataDirty(GameTestHelper helper) {
        helper.assertTrue(XenoSpaceConfig.sableTicketUnloadKeyFix(),
                "the Sable unload-key fix must be enabled to test persistence of its refreshed pointer");

        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Vec3 centre = helper.absoluteVec(new Vec3(0.5, 2.5, 0.5));
        PlotBodyBuilder.Result built = PlotBodyBuilder.build(level, new Vector3d(centre.x, centre.y, centre.z), 1,
                (x, y, z) -> x == 0 && y == 0 && z == 0 ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState());
        helper.assertTrue(built != null && built.body() != null, "the disposable one-voxel body builds");

        ServerSubLevel body = built.body();
        boolean removed = false;
        try {
            container.addForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
            SubLevelTicketInfo info = container.getAllTickets().get(body.getUniqueId());
            helper.assertTrue(info != null, "the body has a saved force-load ticket entry");
            SubLevelTicketsSavedData savedData = SubLevelTicketsSavedData.getOrLoad(level);
            // Ticket insertion dirties the SavedData. Clear that prior change so this assertion isolates the
            // pointer write performed by the subsequent UNLOADED observer callback.
            savedData.setDirty(false);
            helper.assertTrue(!savedData.isDirty(), "the ticket SavedData starts clean before unload");

            GlobalSavedSubLevelPointer pointer = new GlobalSavedSubLevelPointer(
                    new ChunkPos(-999_997, -999_997), (short) 0, (short) 0);
            body.setLastSerializationPointer(pointer);
            container.removeSubLevel(body, SubLevelRemovalReason.UNLOADED);
            removed = true;

            helper.assertTrue(Objects.equals(info.getPointer(), pointer),
                    "the enabled UUID lookup updates the ticket entry to the body's pointer");
            helper.assertTrue(savedData.isDirty(),
                    "the pointer mutation marks Sable's ticket SavedData dirty for the next save");
            CompoundTag serialized = savedData.save(new CompoundTag(), level.registryAccess());
            var serializedTickets = serialized.getList("tickets", Tag.TAG_COMPOUND);
            GlobalSavedSubLevelPointer serializedPointer = null;
            for (int index = 0; index < serializedTickets.size(); index++) {
                CompoundTag ticketRecord = serializedTickets.getCompound(index);
                if (ticketRecord.hasUUID("uuid") && ticketRecord.getUUID("uuid").equals(body.getUniqueId())
                        && ticketRecord.contains("pointer")) {
                    serializedPointer = GlobalSavedSubLevelPointer.CODEC
                            .parse(NbtOps.INSTANCE, ticketRecord.get("pointer")).getOrThrow();
                    break;
                }
            }
            helper.assertTrue(Objects.equals(serializedPointer, pointer),
                    "Sable's actual SavedData serializer emits the refreshed pointer (got " + serializedPointer + ")");
            XenoPixelsMod.LOGGER.info("Unload ticket persistence GameTest PASS: pointer refreshed, SavedData dirtied, "
                            + "and serialized for UUID {}", body.getUniqueId());
            helper.succeed();
        } finally {
            if (!removed && !body.isRemoved()) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            container.removeForceLoadTicket(body, KEEP_ALIVE, Unit.INSTANCE);
        }
    }
}
