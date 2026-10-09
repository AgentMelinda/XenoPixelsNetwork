package net.bullettrain.xenopixelsmod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerService;
import net.bullettrain.xenopixelsmod.combat.v3.V3CombatServer;
import net.bullettrain.xenopixelsmod.combat.v3.V3Config;
import net.bullettrain.xenopixelsmod.combat.v3.V3Direction;
import net.bullettrain.xenopixelsmod.combat.v3.V3FighterStore;
import net.bullettrain.xenopixelsmod.combat.v3.V3Input;
import net.bullettrain.xenopixelsmod.combat.v3.V3Targeting;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native fixtures for the admitted V3 route. Execution and packet binding proof belong to Task 12. */
@GameTestHolder(XenoPixelsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class V3TravelGameTests {
    private V3TravelGameTests() {}

    @GameTest(template = "empty", batch = "v3_cycle_targeting", timeoutTicks = 60)
    public static void explicitCycleUsesValidatedUuidAndPreservesLockOnRefusal(GameTestHelper helper) {
        var fixture = new Fixture(helper);
        try {
            var first = fixture.npc();
            var offRay = fixture.npc();
            offRay.setPos(first.position().add(7, 0, 0));
            fixture.face(first, 7);
            fixture.acquire(offRay.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) == null, "Primary acquisition refuses off-ray candidate");
            fixture.freshSession();
            fixture.acquire(first.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) != null, "Primary native ray establishes approved lock");
            fixture.cycle(offRay.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player).target().equals(first.getUUID()), "Rate-limited cycle preserves old lock");
            helper.runAfterDelay(6, () -> checked(fixture, () -> {
                fixture.cycle(offRay.getUUID());
                helper.assertTrue(V3Targeting.snapshot(fixture.player).target().equals(offRay.getUUID()), "Explicit validated cycle can select off-ray UUID");
            }));
            helper.runAfterDelay(12, () -> checked(fixture, () -> {
                fixture.cycle(UUID.randomUUID());
                helper.assertTrue(V3Targeting.snapshot(fixture.player).target().equals(offRay.getUUID()), "Invalid cycle candidate preserves approved lock");
            }));
            helper.runAfterDelay(18, () -> checked(fixture, () -> {
                fixture.clear();
                fixture.cycle(first.getUUID());
                helper.assertTrue(V3Targeting.snapshot(fixture.player) == null, "Cycling cannot establish a cold lock");
                fixture.close();
                helper.succeed();
                XenoPixelsMod.LOGGER.info("V3 targeting GameTest PASS: primary ray vs admitted explicit cycle, rate limit and refusal");
            }));
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void checked(Fixture fixture, Runnable check) {
        try { check.run(); }
        catch (RuntimeException | Error failure) {
            try { fixture.close(); } catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void loadedTargetBeyondTrackingAnd999Boundary(GameTestHelper helper) {
        run(helper, fixture -> {
            var target = fixture.npc();
            fixture.face(target, 999);
            helper.assertTrue(fixture.player.distanceTo(target) > 128, "Target is beyond native client tracking distance");
            fixture.acquire(target.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) != null
                    && V3Targeting.snapshot(fixture.player).target().equals(target.getUUID()), "999-block loaded UUID is approved");
            UUID session = V3FighterStore.get(fixture.player).session();
            V3CombatServer.handleInput(fixture.player, V3Input.LOCK_CLEAR, UUID.randomUUID(), null, V3Direction.NONE, Integer.MAX_VALUE);
            helper.assertTrue(V3Targeting.snapshot(fixture.player) != null, "Wrong session cannot cancel a lock");
            V3CombatServer.handleInput(fixture.player, V3Input.LOCK_CLEAR, session, null, V3Direction.NONE, 0);
            helper.assertTrue(V3Targeting.snapshot(fixture.player) != null, "Replayed sequence cannot cancel a lock");
            fixture.clear();
            // A new test-owned fighter removes acquisition cooldown only for the independent boundary case.
            V3FighterStore.remove(fixture.player.getUUID());
            fixture.sequence = 0;
            fixture.face(target, 999.01);
            fixture.acquire(target.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) == null, "999.01 blocks is refused by server range");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void differentDimensionMasterAndProtectedPlayerAreRefused(GameTestHelper helper) {
        run(helper, fixture -> {
            LivingEntity target = fixture.npc();
            fixture.face(target, 7);
            ServerLevel other = helper.getLevel().getServer().getLevel(Level.NETHER);
            helper.assertTrue(other != null && other != helper.getLevel(), "Independent server dimension exists");
            // Change only this test actor's level reference; no terrain reads or dimension tickets.
            fixture.player.setServerLevel(other);
            fixture.acquire(target.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) == null, "Another-dimension UUID cannot resolve here");
            fixture.player.setServerLevel(helper.getLevel());
            target.discard();
            target = fixture.own(helper.spawnWithNoFreeWill(MainEntities.MASTER_GOKU.get(), new BlockPos(2, 2, 6)));
            target.setNoGravity(true);
            fixture.freshSession();
            fixture.face(target, 7);
            fixture.acquire(target.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) == null, "Real DMZ master is protected");
            target.discard();
            ServerPlayer protectedPlayer = fixture.own(helper.makeMockServerPlayerInLevel());
            protectedPlayer.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 6))));
            helper.assertTrue(protectedPlayer.isCreative(), "Pinned mock ServerPlayer is a protected creative victim");
            fixture.freshSession();
            fixture.face(protectedPlayer, 7);
            fixture.acquire(protectedPlayer.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) == null, "Protected server player is refused");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void lostKiSenseDeadAndUnloadedTargetsClearImmediately(GameTestHelper helper) {
        run(helper, fixture -> {
            var target = fixture.npc();
            fixture.face(target, 7);
            fixture.acquire(target.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) != null, "Admitted server route establishes a lock");
            var stats = StatsProvider.get(StatsCapability.INSTANCE, fixture.player).orElseThrow(() -> new IllegalStateException("Missing real DMZ stats"));
            stats.getSkills().setSkillLevel("kisense", 0);
            helper.assertTrue(V3Targeting.resolve(fixture.player) == null && V3Targeting.snapshot(fixture.player) == null,
                    "Lost server ki sense clears the approved identity immediately");
            stats.getSkills().setSkillLevel("kisense", 1);
            fixture.freshSession();
            fixture.acquire(target.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) != null, "Reacquired through admitted server route");
            target.setHealth(0);
            helper.assertTrue(V3Targeting.resolve(fixture.player) == null && V3Targeting.refusal(fixture.player).contains("dead"), "Dead refusal is explicit");
            target.discard();
            fixture.freshSession();
            fixture.acquire(target.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) == null && V3Targeting.refusal(fixture.player).contains("unloaded"),
                    "An unloaded UUID is explicitly refused");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void missingVisibilityChunkDoesNotLoadTerrain(GameTestHelper helper) {
        run(helper, fixture -> {
            var target = fixture.npc();
            fixture.face(target, 999);
            XenoServerConfig.lockOnThroughBlocks = false;
            BlockPos midpoint = BlockPos.containing(fixture.player.position().add(target.position()).scale(0.5));
            int x = SectionPos.blockToSectionCoord(midpoint.getX()), z = SectionPos.blockToSectionCoord(midpoint.getZ());
            helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(x, z) == null, "Fixture has unloaded intervening terrain");
            fixture.acquire(target.getUUID());
            helper.assertTrue(V3Targeting.snapshot(fixture.player) == null, "Missing visibility data refuses the lock");
            helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(x, z) == null, "Acquisition did not load terrain");
        });
    }

    private static void run(GameTestHelper helper, Consumer<Fixture> checks) {
        try (var fixture = new Fixture(helper)) { checks.accept(fixture); }
        helper.succeed();
        XenoPixelsMod.LOGGER.info("V3 targeting GameTest PASS: admitted session/sequence server route and native actors");
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer player;
        final List<Entity> owned = new ArrayList<>();
        final String previousMode = XenoServerConfig.combatControllerMode;
        final boolean previousEnabled = XenoServerConfig.bt3CombatEnabled;
        final boolean previousThrough = XenoServerConfig.lockOnThroughBlocks;
        final V3Config.Values previousConfig = V3Config.get();
        int sequence;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = own(helper.makeMockServerPlayerInLevel());
            try {
            XenoServerConfig.combatControllerMode = "v3";
            XenoServerConfig.bt3CombatEnabled = true;
            XenoServerConfig.lockOnThroughBlocks = true;
            V3Config.apply(new V3Config.Values(999, 10, 10));
            CombatControllerService.reconcile(player.getServer());
            var stats = StatsProvider.get(StatsCapability.INSTANCE, player).orElseThrow(() -> new IllegalStateException("Missing real DMZ stats"));
            stats.getSkills().registerDefaultSkill("kisense", 1);
            stats.getSkills().setSkillLevel("kisense", 1);
            V3FighterStore.get(player);
            } catch (RuntimeException | Error failure) {
                try { close(); } catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }
        <T extends Entity> T own(T entity) { owned.add(entity); return entity; }
        LivingEntity npc() {
            var target = own(helper.spawnWithNoFreeWill(ModEntities.XENO_NPC_HUMANOID.get(), new BlockPos(2, 2, 6)));
            target.setNoGravity(true);
            return target;
        }
        void face(LivingEntity target, double distance) {
            player.setPos(target.position().add(0, 0, -distance));
            Vec3 delta = target.getEyePosition().subtract(player.getEyePosition());
            player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            player.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z))));
        }
        void acquire(UUID candidate) {
            var fighter = V3FighterStore.get(player);
            int sent = sequence++;
            V3CombatServer.handleInput(player, V3Input.LOCK_ACQUIRE, fighter.session(), candidate, V3Direction.NONE, sent);
            helper.assertTrue(fighter.acknowledgedSequence() == sent, "Intent reached admitted V3 server route");
        }
        void clear() {
            V3CombatServer.handleInput(player, V3Input.LOCK_CLEAR, V3FighterStore.get(player).session(), null, V3Direction.NONE, sequence++);
        }
        void cycle(UUID candidate) {
            var fighter = V3FighterStore.get(player);
            int sent = sequence++;
            V3CombatServer.handleInput(player, V3Input.LOCK_CYCLE, fighter.session(), candidate, V3Direction.NONE, sent);
            helper.assertTrue(fighter.acknowledgedSequence() == sent, "Cycle reached admitted V3 server route");
        }
        void freshSession() { V3FighterStore.remove(player.getUUID()); sequence = 0; }
        @Override public void close() {
            try {
            for (Entity entity : owned) {
                if (entity instanceof ServerPlayer actor) {
                    actor.setServerLevel(helper.getLevel());
                    actor.getServer().getPlayerList().remove(actor);
                    V3FighterStore.remove(actor.getUUID());
                } else entity.discard();
            }
            } finally {
            V3Config.apply(previousConfig);
            XenoServerConfig.combatControllerMode = previousMode;
            XenoServerConfig.bt3CombatEnabled = previousEnabled;
            XenoServerConfig.lockOnThroughBlocks = previousThrough;
            CombatControllerService.reconcile(helper.getLevel().getServer());
            }
        }
    }
}
