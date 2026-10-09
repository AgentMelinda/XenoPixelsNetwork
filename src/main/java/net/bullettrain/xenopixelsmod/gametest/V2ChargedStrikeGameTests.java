package net.bullettrain.xenopixelsmod.gametest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules;
import net.bullettrain.xenopixelsmod.combat.v2.V2ChargedArc;
import net.bullettrain.xenopixelsmod.combat.v2.V2ChargedLanding;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native world proof for the charged landing and the previously motionless NoAI Xeno NPC. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class V2ChargedStrikeGameTests {
    private V2ChargedStrikeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void chargedKickMovesXenoNpcTwentyBlocks(GameTestHelper helper) {
        Mob target = helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(1, 12, 1));
        target.setNoAi(true);
        target.setNoGravity(true);
        Vec3 origin = target.position();
        for (int tick = 1; tick <= V2ChargeRules.ARC_TICKS; tick++) {
            int step = tick;
            helper.runAfterDelay(tick, () -> helper.assertTrue(
                    V2ChargedArc.step(target, origin, new Vec3(0, 0, 1), step), "Arc remains clear at " + step));
        }
        helper.runAfterDelay(13, () -> helper.assertTrue(target.getY() > origin.y + 5.9, "Six-block arc apex"));
        helper.runAfterDelay(25, () -> {
            Vec3 delta = target.position().subtract(origin);
            helper.assertTrue(Math.abs(delta.z - 20) < 0.01 && Math.abs(delta.y) < 0.01,
                    "Actual NoAI NPC displacement is twenty blocks: " + delta);
            helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "No duplicate vanilla movement");
            target.discard();
            helper.succeed();
            XenoPixelsMod.LOGGER.info("ChargedStrike GameTest PASS: Xeno NPC twenty-block arc displacement={}", delta);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void chargedLandingRespectsFrontBackHeightAndWalls(GameTestHelper helper) {
        var fighter = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 base = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 12, 1)));
        fighter.setPos(base);
        Mob target = helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(1, 12, 1));
        target.setNoGravity(true);
        for (int yaw : new int[]{0, 90, 180, 270}) {
            target.yBodyRot = yaw;
            Vec3 kick = V2ChargedLanding.find(fighter, target, true);
            Vec3 punch = V2ChargedLanding.find(fighter, target, false);
            helper.assertTrue(kick != null && punch != null, "Both sides have room");
            Vec3 forward = new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
            helper.assertTrue(kick.subtract(target.position()).dot(forward) < -1, "Kick lands behind the body");
            helper.assertTrue(punch.subtract(target.position()).dot(forward) > 1, "Punch lands in front of the body");
            helper.assertTrue(kick.y == target.getY() && punch.y == target.getY(), "Landing follows target height");
        }
        target.yBodyRot = 0;
        Vec3 front = V2ChargedLanding.find(fighter, target, false);
        BlockPos wall = BlockPos.containing(front);
        helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(V2ChargedLanding.find(fighter, target, false) == null, "Blocked landing is refused");
        helper.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());
        target.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("ChargedStrike GameTest PASS: front/back body yaw, height and blocked landing");
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void chargedArcStopsAtSolidObstacle(GameTestHelper helper) {
        Mob target = helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(1, 12, 1));
        target.setNoGravity(true);
        Vec3 origin = target.position();
        Vec3 next = origin.add(0, 0.959, 20.0 / 24);
        BlockPos block = BlockPos.containing(next.add(0, 1, 0));
        helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
        helper.assertTrue(!V2ChargedArc.step(target, origin, new Vec3(0, 0, 1), 1), "Arc cannot move through stone");
        helper.assertTrue(target.position().equals(origin), "Blocked step leaves NPC in place");
        helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
        target.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("ChargedStrike GameTest PASS: arc obstacle refuses movement");
    }
}
