package net.bullettrain.xenopixelsmod.gametest;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatBrainVersion;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcNativePursuit;
import net.bullettrain.xenopixelsmod.compat.npc.NpcResources;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.movement.NpcMovementOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Runtime proof of the v9 combat movement owner: DMZ's own dash numbers, and the arbitration
 * switch actually reaching the path that steers a fighting NPC.
 *
 * <p>The impulse, lift, cost and cooldown asserted here are the literals in the pinned jar's
 * {@code DashHandler}, checked by reading its bytecode rather than by trusting the comment in
 * {@code NpcNativePursuit}, because a dash that is a little too long or a little too cheap is felt
 * as a different game and no unit test would notice.
 *
 * <p>Run with {@code ./gradlew runGameTestServer -PofflineMcMeta -PgameTestWithoutMyNpcs}.
 */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcCombatMovementGameTests {
    private NpcCombatMovementGameTests() {}

    /** A v9 brain NPC and a fighter eight blocks away, off the floor so nothing collides. */
    private static XenoNpcEntity npc(GameTestHelper helper, BlockPos at) {
        XenoNpcEntity npc = helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), at);
        npc.setNoGravity(true);
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.combatBrain = true;
        profile.setBrainVersion(NpcCombatBrainVersion.V9);
        profile.write(npc);
        return npc;
    }

    private static NpcCombatProfile profile(XenoNpcEntity npc) {
        return NpcCombatProfile.read(npc);
    }

    private static LivingEntity fighter(GameTestHelper helper, Vec3 at) {
        LivingEntity target = helper.makeMockPlayer(GameType.SURVIVAL);
        target.setPos(at.x, at.y, at.z);
        return target;
    }

    /**
     * The dash a player recognises: native's impulse, native's lift, native's price, native's
     * cooldown - and the price taken out of stamina rather than ki.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void dashMatchesDragonsMineZImpulseLiftAndStaminaCost(GameTestHelper helper) {
        XenoNpcEntity npc = npc(helper, new BlockPos(2, 3, 2));
        LivingEntity target = fighter(helper, npc.position().add(8, 0, 0));
        NpcCombatProfile profile = profile(npc);
        NpcNativePursuit.tick(npc, profile, target);

        double speed = 4.0 * (npc.getAttributeValue(Attributes.MOVEMENT_SPEED) / 0.1) * 0.3;
        double drain;
        int cooldownSeconds;
        try {
            var combat = com.dragonminez.common.config.ConfigManager.getCombatConfig();
            drain = combat.getBaselineFormDrain();
            cooldownSeconds = combat.getDashCooldownSeconds();
        } catch (Throwable unavailable) {
            helper.fail("DMZ's combat config is unreadable, so the native dash cost has no source of truth");
            return;
        }
        double cost = Math.ceil(drain * 0.12);

        double liftBefore = npc.getDeltaMovement().y;
        NpcResources.Snapshot before = NpcResources.get(npc, profile);
        helper.assertTrue(before.stamina() >= cost, "the NPC starts with enough stamina to pay for a dash");
        boolean dashed = NpcNativePursuit.dash(npc, profile, target);
        NpcResources.Snapshot after = NpcResources.get(npc, profile);
        helper.assertTrue(dashed, "a v9 NPC eight blocks from its fighter can dash");
        Vec3 motion = npc.getDeltaMovement();
        helper.assertTrue(Math.abs(Math.hypot(motion.x, motion.z) - speed) < 1e-6,
                "the impulse is native's 4 * (movement / 0.1) * 0.3: " + Math.hypot(motion.x, motion.z)
                        + " against " + speed);
        helper.assertTrue(Math.abs((motion.y - liftBefore) - (npc.onGround() ? 0.35 : 0.2)) < 1e-9,
                "the lift is added, in the amount native uses for this ground state: " + liftBefore
                        + " then " + motion.y);
        helper.assertTrue(Math.abs((before.stamina() - after.stamina()) - cost) < 1e-6,
                "the dash costs ceil(baselineFormDrain * 0.12) stamina: paid "
                        + (before.stamina() - after.stamina()) + " against " + cost);
        helper.assertTrue(Math.abs(after.energy() - before.energy()) < 1e-6,
                "and it does not touch ki: a normal dash costs no energy natively");

        helper.assertTrue(!NpcNativePursuit.dash(npc, profile, target),
                "the dash does not repeat while it is still running");
        npc.tickCount += cooldownSeconds * 20 - 1;
        NpcResources.setStamina(npc, profile, after.maxStamina());
        helper.assertTrue(!NpcNativePursuit.dash(npc, profile, target),
                "nor one tick before native's " + (cooldownSeconds * 20) + "-tick cooldown ends");
        npc.tickCount += 1;
        helper.assertTrue(NpcNativePursuit.dash(npc, profile, target),
                "and it is available again the tick the cooldown ends");
        target.discard();
        npc.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcCombatMovement GameTest PASS: native dash impulse, lift, stamina cost, cooldown");
    }

    /**
     * The dash taken off a jump. Native never required footing - {@code onGround} only picks which
     * lift to add - and requiring it made every flying brawler lose its gap-closer.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void anAirborneNpcDashesWithTheNativeLift(GameTestHelper helper) {
        XenoNpcEntity npc = npc(helper, new BlockPos(2, 8, 2));
        LivingEntity target = fighter(helper, npc.position().add(7, 2, 0));
        NpcCombatProfile profile = profile(npc);
        NpcNativePursuit.tick(npc, profile, target);
        helper.assertTrue(!npc.onGround(), "the fixture is actually airborne, or this proves nothing");
        double liftBefore = npc.getDeltaMovement().y;
        helper.assertTrue(NpcNativePursuit.dash(npc, profile, target), "an airborne NPC still dashes");
        helper.assertTrue(Math.abs((npc.getDeltaMovement().y - liftBefore) - 0.2) < 1e-9,
                "with native's in-air lift of 0.2, not the ground 0.35: " + liftBefore + " then "
                        + npc.getDeltaMovement().y);
        target.discard();
        npc.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcCombatMovement GameTest PASS: airborne dash with the 0.2 lift");
    }

    /**
     * The same impulse reading the ground number. Native chooses 0.35 or 0.2 from footing alone, and
     * an airborne fixture cannot show the ground half - which is the dash everybody actually uses.
     *
     * <p>Footing is forced rather than waited for: this mod's {@code empty} template has no floor and
     * a no-AI NPC is never asked to fall, so the ground state is built by putting a block under the
     * NPC and running the one vanilla movement call that sets it.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void aGroundedNpcDashesWithTheGroundLift(GameTestHelper helper) {
        XenoNpcEntity npc = npc(helper, new BlockPos(2, 3, 2));
        LivingEntity target = fighter(helper, npc.position().add(8, 0, 0));
        helper.getLevel().setBlockAndUpdate(npc.blockPosition().below(),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        npc.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -0.1, 0));
        helper.assertTrue(npc.onGround(), "the fixture is actually standing on something");
        NpcCombatProfile profile = profile(npc);
        NpcNativePursuit.tick(npc, profile, target);
        double before = npc.getDeltaMovement().y;
        helper.assertTrue(NpcNativePursuit.dash(npc, profile, target), "a grounded NPC dashes");
        helper.assertTrue(Math.abs((npc.getDeltaMovement().y - before) - 0.35) < 1e-9,
                "native's ground lift is added to whatever the NPC already had, not set over it: "
                        + before + " then " + npc.getDeltaMovement().y);
        target.discard();
        npc.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcCombatMovement GameTest PASS: grounded dash lifts by the native 0.35");
    }

    /**
     * Native's flight dash: a different impulse, a different resource, and no lift at all.
     *
     * <p>{@code DashHandler} branches on the fly skill before it chooses any of the three, and the
     * numbers read out of the pinned jar are {@code min(3.8, max(0.55, 12*MOVEMENT_SPEED +
     * max(velocity, dmz_server_speed)*2.5))} applied to x and z with y set to <em>exactly</em> zero,
     * fall distance cleared, and the cost taken from ki. The ground-only dash this replaced made an
     * NPC that flies hop upward on every gap-closer and pay stamina it was never meant to spend.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void aFlyingNpcTakesTheNativeFlightDash(GameTestHelper helper) {
        XenoNpcEntity npc = npc(helper, new BlockPos(2, 8, 2));
        LivingEntity target = fighter(helper, npc.position().add(12, 0, 0));
        NpcCombatProfile profile = profile(npc);
        profile.flySkillOn = true;
        NpcResources.Snapshot full = NpcResources.get(npc, profile);
        NpcResources.setEnergy(npc, profile, full.maxEnergy());
        NpcResources.setStamina(npc, profile, full.maxStamina());
        NpcNativePursuit.tick(npc, profile, target);
        double base = 12 * npc.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double cruise = npc.getDeltaMovement().length();
        double wanted = Math.min(3.8, Math.max(0.55, base + cruise * 2.5));
        double xBefore = npc.getDeltaMovement().x;
        NpcResources.Snapshot before = NpcResources.get(npc, profile);
        helper.assertTrue(NpcNativePursuit.dash(npc, profile, target), "a flying NPC dashes");
        Vec3 moved = npc.getDeltaMovement();
        helper.assertTrue(Math.abs(moved.x - (xBefore + wanted)) < 1e-6,
                "the flight impulse " + wanted + ", not the ground one: " + moved);
        helper.assertTrue(moved.y == 0, "native zeroes the vertical component rather than lifting: " + moved);
        helper.assertTrue(npc.fallDistance == 0, "and clears fall distance");
        NpcResources.Snapshot after = NpcResources.get(npc, profile);
        double cost = Math.max(1, (int) Math.ceil(com.dragonminez.common.config.ConfigManager
                .getCombatConfig().getBaselineFormDrain() * 0.12));
        helper.assertTrue(Math.abs((before.energy() - after.energy()) - cost) < 1e-9,
                "a flight dash is paid in ki: expected " + cost + ", saw "
                        + (before.energy() - after.energy()));
        helper.assertTrue(before.stamina() == after.stamina(),
                "and spends no stamina, which is the ground branch's resource");
        target.discard();
        npc.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcCombatMovement GameTest PASS: flight dash impulse, zeroed lift, ki cost");
    }

    /**
     * The combat path asks the arbitration switch instead of going round it.
     *
     * <p>{@code npcMovementArbitration=false} is an operator's escape hatch for the whole
     * coordinator. The v9 pursuit claimed through {@code NpcMovementOwner} directly, so turning the
     * switch off left the most active movement system in the game still arbitrating - and still
     * refusing to steer when something else held the claim.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void pursuitRespectsTheArbitrationSwitch(GameTestHelper helper) {
        XenoNpcEntity npc = npc(helper, new BlockPos(2, 3, 2));
        LivingEntity target = fighter(helper, npc.position().add(8, 0, 0));
        NpcCombatProfile profile = profile(npc);
        boolean arbitration = net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcMovementArbitration;
        try {
            net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcMovementArbitration = true;
            NpcNativePursuit.tick(npc, profile, target);
            helper.assertTrue(NpcMovementOwner.current(npc) == NpcMovementOwner.Claim.COMBAT,
                    "with arbitration on, the pursuit takes the combat claim: " + NpcMovementOwner.current(npc));

            net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcMovementArbitration = false;
            NpcMovementOwner.release(npc, NpcMovementOwner.Claim.COMBAT);
            NpcMovementOwner.claim(npc, NpcMovementOwner.Claim.SCENE);
            npc.tickCount += 1; // the pursuit issues one steer per tick, so a repeat tick is a no-op
            NpcNativePursuit.tick(npc, profile, target);
            helper.assertTrue(NpcMovementOwner.current(npc) == NpcMovementOwner.Claim.SCENE,
                    "with arbitration off the pursuit does not record a claim of its own, so a scene "
                            + "keeps the NPC it placed: " + NpcMovementOwner.current(npc));
            helper.assertTrue(NpcNativePursuit.owns(npc, profile), "and it is still the v9 path being ticked");
        } finally {
            net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcMovementArbitration = arbitration;
            NpcMovementOwner.clearAll();
        }
        target.discard();
        npc.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcCombatMovement GameTest PASS: npcMovementArbitration reaches the v9 pursuit");
    }

    /**
     * A knocked-back NPC stays its fight's, instead of being handed to the leash for ten ticks.
     *
     * <p>The hit-recovery window exists so the push can play out: every brain path returns early while
     * it is active. Returning early is not the same as letting go, and the v9 pursuit used to do both -
     * its yield path ran {@code reset}, which releases the COMBAT claim, and {@code tickLeash} has no
     * combat check of its own, because that claim is the only thing telling it a fight is in progress.
     * So an NPC outside its home radius could be claimed by the leash mid-knockback and started toward
     * home, only for the pursuit to claim it back the instant recovery ended.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void pursuitHoldsTheClaimThroughAKnockback(GameTestHelper helper) {
        XenoNpcEntity npc = npc(helper, new BlockPos(2, 3, 2));
        LivingEntity target = fighter(helper, npc.position().add(8, 0, 0));
        NpcCombatProfile profile = profile(npc);
        boolean arbitration = net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcMovementArbitration;
        try {
            net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcMovementArbitration = true;
            NpcNativePursuit.tick(npc, profile, target);
            helper.assertTrue(NpcMovementOwner.current(npc) == NpcMovementOwner.Claim.COMBAT,
                    "the fixture is a fighting NPC: " + NpcMovementOwner.current(npc));
            net.bullettrain.xenopixelsmod.compat.npc.NpcKnockbackGrace.mark(npc);
            helper.assertTrue(net.bullettrain.xenopixelsmod.compat.npc.NpcKnockbackGrace.active(npc),
                    "the recovery window is open");
            npc.tickCount += 1;
            NpcNativePursuit.tick(npc, profile, target);
            helper.assertTrue(NpcMovementOwner.current(npc) == NpcMovementOwner.Claim.COMBAT,
                    "yielding the velocity must not release the claim: " + NpcMovementOwner.current(npc));
            helper.assertTrue(!net.bullettrain.xenopixelsmod.npc.XenoNpcBehaviour
                            .claimMovement(npc, NpcMovementOwner.Claim.LEASH),
                    "and the leash is still locked out for the length of the hit");
        } finally {
            net.bullettrain.xenopixelsmod.config.XenoServerConfig.npcMovementArbitration = arbitration;
            NpcMovementOwner.clearAll();
        }
        target.discard();
        npc.discard();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcCombatMovement GameTest PASS: claim held through hit recovery");
    }

    /**
     * The plan's 1 / 16 / 64 NPC fight measurement, recorded as numbers instead of a vibe.
     *
     * <p>What it measures is the synchronous steering work the v9 owner does per NPC per re-issue:
     * the range and curve arithmetic, the sprint apply, and the {@code moveTo} call. It deliberately
     * does <em>not</em> include Minecraft's path computation, which vanilla runs on its own async
     * pathfinder thread - so the figure is the cost this mod adds to the tick, not the whole cost of
     * an NPC moving. The server's own average tick time is logged alongside it as context for that
     * window; on this JVM it also contains every other live system, so it is a measurement of a
     * game-test server and not a TPS claim about a production one.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "npc_combat_scale")
    public static void pursuitSteeringCostIsMeasuredAtOneSixteenAndSixtyFour(GameTestHelper helper) {
        XenoNpcEntity anchor = npc(helper, new BlockPos(1, 3, 1));
        LivingEntity target = fighter(helper, anchor.position().add(9, 0, 9));
        java.util.List<XenoNpcEntity> field = new java.util.ArrayList<>();
        field.add(anchor);
        for (int index = 1; index < 64; index++) {
            BlockPos at = new BlockPos(1 + index % 8, 3, 1 + index / 8);
            field.add(npc(helper, at));
        }
        NpcCombatProfile profile = profile(anchor);
        for (int count : new int[]{1, 16, 64}) {
            // Warm the interpreter out of the way, then measure. Every NPC gets its own tick clock,
            // because the pursuit issues one steer per entity tick and would otherwise no-op.
            steer(field, count, profile, target, 60);
            long start = System.nanoTime();
            int iterations = 400;
            steer(field, count, profile, target, iterations);
            long elapsed = System.nanoTime() - start;
            double perNpcNanos = (double) elapsed / ((double) iterations * count);
            int claimed = 0;
            for (int index = 0; index < count; index++) {
                if (NpcMovementOwner.current(field.get(index)) == NpcMovementOwner.Claim.COMBAT) claimed++;
            }
            helper.assertTrue(claimed == count,
                    "every measured NPC is steering under its own combat claim: " + claimed + " of " + count);
            XenoPixelsMod.LOGGER.info("NpcCombatMovement measurement: count=" + count
                    + " steering=" + String.format(java.util.Locale.ROOT, "%.1f", perNpcNanos / 1000.0)
                    + " us per NPC per re-issue"
                    + " batchOf" + count + "=" + String.format(java.util.Locale.ROOT, "%.3f", elapsed / 1.0e6)
                    + " ms over " + iterations + " ticks"
                    + " serverAverageTick=" + String.format(java.util.Locale.ROOT, "%.3f",
                            helper.getLevel().getServer().getAverageTickTimeNanos() / 1.0e6) + " ms");
        }
        for (XenoNpcEntity each : field) each.discard();
        target.discard();
        NpcMovementOwner.clearAll();
        helper.succeed();
        XenoPixelsMod.LOGGER.info("NpcCombatMovement GameTest PASS: steering cost measured at 1, 16 and 64 NPCs");
    }

    /** Runs the v9 steering pass for the first {@code count} NPCs, advancing each one's own clock. */
    private static void steer(java.util.List<XenoNpcEntity> field, int count, NpcCombatProfile profile,
                              LivingEntity target, int iterations) {
        for (int iteration = 0; iteration < iterations; iteration++) {
            for (int index = 0; index < count; index++) {
                XenoNpcEntity npc = field.get(index);
                npc.tickCount++;
                NpcNativePursuit.tick(npc, profile, target);
            }
        }
    }
}
