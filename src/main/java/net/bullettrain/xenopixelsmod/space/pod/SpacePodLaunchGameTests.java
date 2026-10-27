package net.bullettrain.xenopixelsmod.space.pod;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.item.ModsItems;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;


/** Optional server acceptance probe for launch gating, incremental hull assembly, and Sable rider binding. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpacePodLaunchGameTests {
    private SpacePodLaunchGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240, required = false, manualOnly = true,
            batch = "space_pod_launch")
    public static void classicEggCapsuleBuildsIncrementallyAndBindsPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID playerId = UUID.randomUUID();
        String playerName = "XPL_" + playerId.toString().replace("-", "").substring(0, 8);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(playerId, playerName));
        level.addNewPlayer(player);
        XenoSpaceConfig.Data previousConfig = XenoSpaceConfig.snapshot();
        ServerSubLevel[] created = new ServerSubLevel[1];
        player.setPos(helper.absoluteVec(new Vec3(2, 3, 2)));

        Runnable cleanup = () -> {
            SpacePodLifecycle.cancelPending(player);
            ServerSubLevel body = created[0];
            if (body != null && !body.isRemoved()) {
                var container = SubLevelContainer.getContainer(level);
                if (container != null) container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            }
            XenoSpaceConfig.apply(previousConfig);
            level.getServer().getPlayerList().remove(player);
        };

        try {
            helper.assertTrue(SpacePodLifecycle.requestLaunch(player) == SpacePodLifecycle.Request.SPACE_DISABLED,
                    "the production launch gate refuses builds while the space master switch is off");
            helper.assertTrue("Classic Egg Pod Capsule".equals(ModsItems.SPACE_POD_CAPSULE.get()
                            .getName(ItemStack.EMPTY).getString()),
                    "the registered launch item has its player-facing name");
            XenoSpaceConfig.Data launchConfig = XenoSpaceConfig.snapshot();
            launchConfig.enabled = true;
            launchConfig.spacePodsEnabled = true;
            XenoSpaceConfig.apply(launchConfig);
            ItemStack capsule = new ItemStack(ModsItems.SPACE_POD_CAPSULE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, capsule);
            ModsItems.SPACE_POD_CAPSULE.get().use(level, player, InteractionHand.MAIN_HAND);
            boolean queued = SpacePodLifecycle.pendingLaunch(level.getServer(), playerId);
            XenoPixelsMod.LOGGER.info("P15 launch GameTest production capsule accepted={}", queued);
            helper.assertTrue(queued,
                    "using the registered capsule with space enabled enters the production build queue");
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            helper.fail("capsule use raised " + failure);
            return;
        }

        int[] elapsed = {0};
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0]) return;
            SpacePodLifecycle.tick(level.getServer());
            if (elapsed[0] % 20 == 0)
                XenoPixelsMod.LOGGER.info("P15 launch GameTest progress {}",
                        SpacePodLifecycle.pendingStatus(level.getServer(), playerId));
            ServerSubLevel tracked = PodRiderBinding.trackedBody(player);
            if (tracked != null && PodTransferTransaction.podId(tracked) != null) {
                created[0] = tracked;
                finished[0] = true;
                UUID verifiedPodId = PodTransferTransaction.podId(tracked);
                helper.assertTrue(!tracked.isRemoved() && tracked.getLevel() == level,
                        "the launch creates a live Sable pod in the rider's dimension");
                cleanup.run();
                XenoPixelsMod.LOGGER.info("P15 Classic Egg launch GameTest PASS pod={} dimension={}",
                        verifiedPodId, level.dimension().location());
                helper.succeed();
            } else if (++elapsed[0] >= 200) {
                finished[0] = true;
                cleanup.run();
                helper.fail("the capsule did not produce and bind a Classic Egg Sable body within 200 ticks");
            }
        });
    }
}
