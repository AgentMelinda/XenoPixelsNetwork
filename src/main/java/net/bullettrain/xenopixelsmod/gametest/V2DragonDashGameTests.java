package net.bullettrain.xenopixelsmod.gametest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v2.V2DragonDashLanding;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native world checks for crossing the target rather than remaining on the approach side. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class V2DragonDashGameTests {
    private V2DragonDashGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void dragonDashCrossesNearFarAndDifferentHeights(GameTestHelper helper) {
        var fighter = helper.makeMockPlayer(GameType.SURVIVAL);
        var target = helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(1, 12, 1));
        target.setNoGravity(true);
        for (int yaw : new int[]{0, 90, 180, 270}) {
            target.yBodyRot = yaw;
            Vec3 side = new Vec3(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw)));
            for (double distance : new double[]{0.8, 7, 20}) {
                for (double height : new double[]{-5, 0, 5}) {
                    fighter.setPos(target.position().add(side.scale(distance)).add(0, height, 0));
                    Vec3 landing = V2DragonDashLanding.find(fighter, target, 24);
                    helper.assertTrue(landing != null, "Open landing within dash range");
                    helper.assertTrue(landing.subtract(target.position()).dot(side) < -1,
                            "N crosses the target even at melee distance");
                    helper.assertTrue(landing.y == target.getY(), "N follows the target height");
                }
            }
        }
        fighter.setPos(target.position().add(0, 4, 0));
        helper.assertTrue(V2DragonDashLanding.find(fighter, target, 24) != null, "Vertical alignment has a stable fallback");
        target.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("DragonDash GameTest PASS: opposite-side landings at near/far range and different heights");
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void dragonDashRefusesBlockedAndOutOfRangeLandings(GameTestHelper helper) {
        var fighter = helper.makeMockPlayer(GameType.SURVIVAL);
        var target = helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(1, 12, 1));
        target.setNoGravity(true);
        fighter.setPos(target.position().add(0, 0, -7));
        Vec3 landing = V2DragonDashLanding.find(fighter, target, 24);
        helper.assertTrue(landing != null, "Open destination before obstruction");
        BlockPos wall = BlockPos.containing(landing);
        helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(wall.above(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(V2DragonDashLanding.find(fighter, target, 24) == null, "Solid destination is refused");
        helper.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());
        fighter.setPos(target.position().add(0, 0, -25));
        helper.assertTrue(V2DragonDashLanding.find(fighter, target, 24) == null, "Range cannot be bypassed");
        target.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("DragonDash GameTest PASS: blocked and out-of-range destinations refused");
    }
}
