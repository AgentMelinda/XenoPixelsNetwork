package net.bullettrain.xenopixelsmod.space;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Headless server checks for the entity state used by the post-handoff collision lease. */
@GameTestHolder("xenopixels_space_config")
@PrefixGameTestTemplate(false)
public final class HandoffIntangibilityGameTests {
    private HandoffIntangibilityGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "handoff_intangibility")
    public static void leaseSuppressesCollisionThenRestoresTheOriginalEntityFlag(GameTestHelper helper) {
        var entity = EntityType.PIG.create(helper.getLevel());
        helper.assertTrue(entity != null, "the test entity is available");
        entity.setNoAi(true);
        entity.setPos(2.5, 2.0, 2.5);
        helper.getLevel().addFreshEntity(entity);
        helper.assertTrue(!entity.noPhysics, "the ordinary test entity starts with collisions enabled");
        long start = helper.getLevel().getServer().getTickCount();

        HandoffIntangibility.mark(entity, start);
        helper.assertTrue(entity.noPhysics, "the entity is collision-free during the handoff window");
        HandoffIntangibility.tick(start + HandoffIntangibility.DURATION_TICKS - 1);
        helper.assertTrue(entity.noPhysics, "collision suppression remains active before the full window elapses");
        HandoffIntangibility.tick(start + HandoffIntangibility.DURATION_TICKS);
        helper.assertTrue(!entity.noPhysics, "the entity regains collision at the end of the handoff window"
                + " (noPhysics=" + entity.noPhysics + ", removed=" + entity.isRemoved()
                + ", activeLeases=" + HandoffIntangibility.activeCount() + ", startTick=" + start + ")");

        entity.noPhysics = true;
        HandoffIntangibility.mark(entity, start + 20);
        HandoffIntangibility.tick(start + 40);
        helper.assertTrue(entity.noPhysics, "a pre-existing noPhysics state is preserved");
        HandoffIntangibility.clear();
        helper.succeed();
    }
}
