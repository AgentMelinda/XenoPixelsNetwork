package net.bullettrain.xenopixelsmod.gametest;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisherKamehameha;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisherThrow;
import net.bullettrain.xenopixelsmod.combat.v2.V2VanishLanding;
import net.bullettrain.xenopixelsmod.combat.v2.V2Config;
import net.bullettrain.xenopixelsmod.combat.v2.LockRules;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real server-level regression checks for the finisher's native beam and damage-owned push. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateFinisherGameTests {
    private UltimateFinisherGameTests() {}

    private static Player caster(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
        player.setNoGravity(true);
        player.setYRot(0);
        player.setXRot(0);
        return player;
    }

    private static StatsData combatStats(Player player) {
        StatsData stats = new StatsData(player);
        stats.getStats().setKiPower(100);
        stats.getResources().setCurrentEnergy(10000);
        stats.getResources().setPowerRelease(100);
        return stats;
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void vanishUsesDistantTargetsHeight(GameTestHelper helper) {
        Player player = caster(helper);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(35, 30, 1));
        target.setNoGravity(true);
        var cfg = new V2Config.Values();
        double lockRange = LockRules.range(128, 1, false);
        for (int side : new int[]{-1, 0, 1}) {
            Vec3 landing = V2VanishLanding.find(player, target, side, cfg, lockRange);
            helper.assertTrue(landing != null, "Distant elevated locked target has a vanish landing");
            helper.assertTrue(Math.abs(landing.y - target.getY()) < 1e-6,
                    "Vanish arrives at the target's height, not the caster's");
            helper.assertTrue(landing.x > target.getX() && landing.distanceTo(target.position()) < 4,
                    "Vanish arrives just beyond the target with the requested side offset");
        }
        target.setPos(target.getX(), player.getY() - 20, target.getZ());
        Vec3 lower = V2VanishLanding.find(player, target, 0, cfg, lockRange);
        helper.assertTrue(lower != null && Math.abs(lower.y - target.getY()) < 1e-6,
                "Lower target also determines the landing height");
        cfg.vanishUsesLockRange = false;
        helper.assertTrue(V2VanishLanding.find(player, target, 0, cfg, lockRange) == null,
                "Explicit shorter configured vanish reach is enforced");
        target.discard();
        player.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("V2 GameTest PASS: distant elevated/lower targets set vanish landing height and side");
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void heldMobActuallyTravelsThrowArc(GameTestHelper helper) {
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 6, 1));
        assertThrowArc(helper, target);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void heldXenoNpcActuallyTravelsThrowArc(GameTestHelper helper) {
        Mob target = helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(1, 6, 1));
        assertThrowArc(helper, target);
    }

    private static void assertThrowArc(GameTestHelper helper, Mob target) {
        target.setNoAi(true);
        target.setNoGravity(true);
        Vec3 origin = target.position();
        for (int tick = 1; tick <= 20; tick++) {
            int step = tick;
            helper.runAfterDelay(tick, () -> {
                helper.assertTrue(UltimateFinisherThrow.step(target, origin, new Vec3(0, 0, 1), step),
                        "Throw path stays clear at step " + step);
            });
        }
        helper.runAfterDelay(11, () -> helper.assertTrue(target.getY() > origin.y + 3.5,
                "Held mob actually rises along the throw arc; displacement=" + target.position().subtract(origin)));
        helper.runAfterDelay(21, () -> {
            Vec3 displacement = target.position().subtract(origin);
            helper.assertTrue(Math.abs(displacement.z - 15) < 0.1 && Math.abs(displacement.y) < 0.1,
                    "Held mob actually moves fifteen blocks and lands at arc height; displacement=" + displacement);
            helper.assertTrue(target.isNoAi(), "Throw preserves the hold's temporary AI suspension");
            helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Throw leaves no queued second movement");
            String type = target.getType().toString();
            target.discard();
            helper.succeed();
            XenoPixelsMod.LOGGER.info("UltimateFinisher GameTest PASS: held {} throw displacement={}", type, displacement);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void heldThrowRefusesBlockedPath(GameTestHelper helper) {
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 6, 1));
        target.setNoAi(true);
        target.setNoGravity(true);
        Vec3 origin = target.position();
        helper.setBlock(new BlockPos(1, 6, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 7, 2), Blocks.STONE);
        helper.assertTrue(!UltimateFinisherThrow.step(target, origin, new Vec3(0, 0, 1), 1),
                "Blocked throw refuses movement");
        helper.assertTrue(target.position().equals(origin) && target.getDeltaMovement().equals(Vec3.ZERO),
                "Blocked throw neither moves nor queues a push through the wall");
        target.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("UltimateFinisher GameTest PASS: blocked throw does not move through a wall");
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void heldThrowRespectsNpcKnockbackProtection(GameTestHelper helper) {
        Mob target = helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(1, 6, 1));
        target.setNoAi(true);
        target.setNoGravity(true);
        NpcCombatProfile profile = NpcCombatProfile.read(target);
        profile.knockable = false;
        profile.write(target);
        Vec3 origin = target.position();
        helper.assertTrue(!UltimateFinisherThrow.step(target, origin, new Vec3(0, 0, 1), 1),
                "Protected NPC refuses throw");
        helper.assertTrue(target.position().equals(origin) && target.getDeltaMovement().equals(Vec3.ZERO),
                "Protected NPC neither moves nor receives throw velocity");
        target.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("UltimateFinisher GameTest PASS: NPC knockback protection refuses throw");
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void kamehamehaChargesThenFiresAsNativeWave(GameTestHelper helper) {
        Player player = caster(helper);
        StatsData stats = combatStats(player);
        var technique = PredefinedTechniques.REGISTRY.get("kamehameha");
        KiWaveEntity wave = UltimateFinisherKamehameha.start(player, stats, technique);
        helper.assertTrue(wave != null, "Kamehameha spawned");
        helper.assertTrue(wave.getKiType() == AbstractKiProjectile.KiType.WAVE,
                "Kamehameha must have WAVE type, found " + wave.getKiType());
        helper.assertTrue(wave.getKiRenderType() == 1 && wave.getColor() == technique.getColorInterior(),
                "Native Kamehameha renderer and color");
        helper.assertTrue(!wave.isFiring(), "Charge does not fire immediately");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(wave.isAlive() && !wave.isFiring(), "Beam stays alive throughout charge");
            helper.assertTrue(UltimateFinisherKamehameha.release(player, stats, technique, wave), "Native release accepted");
            helper.assertTrue(wave.isFiring(), "Native wave is firing");
            helper.assertTrue(wave.getKiDamage() > 0, "Native release supplies damage");
            float firingYaw = wave.getYRot();
            player.setYRot(90);
            wave.setBlockDestructionEnabled(false);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(wave.getBeamLength() > 0, "Fired Kamehameha grows a beam");
                helper.assertTrue(wave.getYRot() == firingYaw, "Native beam keeps its firing direction");
                wave.discard();
                player.discard();
                helper.succeed();
                XenoPixelsMod.LOGGER.info("UltimateFinisher GameTest PASS: native WAVE charge, release and fixed firing direction");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void finisherBeamPushesOnlyAfterAcceptedDamage(GameTestHelper helper) {
        Player player = caster(helper);
        StatsData stats = combatStats(player);
        var technique = PredefinedTechniques.REGISTRY.get("kamehameha");
        KiWaveEntity wave = UltimateFinisherKamehameha.start(player, stats, technique);
        helper.assertTrue(wave != null, "Finisher beam spawned");
        helper.assertTrue(UltimateFinisherKamehameha.release(player, stats, technique, wave), "Finisher beam fired");
        // Vary the source direction: vanilla hurt must never overwrite the fixed beam impulse.
        for (int sample = 0; sample < 8; sample++) {
            Pig victim = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 2, 2));
            wave.setPos(victim.position().add(Math.cos(sample * Math.PI / 4), 0, Math.sin(sample * Math.PI / 4)));
            victim.setInvulnerable(true);
            victim.setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(!wave.applyDamageOrHeal(victim, 1), "Invulnerable victim refuses damage");
            helper.assertTrue(victim.getDeltaMovement().equals(Vec3.ZERO), "Refused damage does not push");
            victim.setInvulnerable(false);
            helper.assertTrue(wave.applyDamageOrHeal(victim, 1), "Victim accepts beam damage");
            helper.assertTrue(victim.getDeltaMovement().z > 0.5 && victim.getDeltaMovement().y > 0,
                    "Accepted beam damage pushes along the ray and lifts the victim; velocity=" + victim.getDeltaMovement()
                            + ", knockable=" + CombatKnockback.canKnockBack(victim)
                            + ", yaw=" + wave.getYRot() + ", pitch=" + wave.getXRot());
            helper.assertTrue(Math.abs(victim.getDeltaMovement().x) < 0.01,
                    "Beam impulse keeps its firing direction after vanilla damage");
            victim.discard();
        }
        wave.discard();
        player.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("UltimateFinisher GameTest PASS: accepted beam damage pushes; refused damage does not");
    }
}
