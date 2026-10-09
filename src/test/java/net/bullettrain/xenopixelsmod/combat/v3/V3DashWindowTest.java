package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class V3DashWindowTest {
    private static final UUID TARGET = UUID.randomUUID();

    @Test void crossDuringTheApproach() {
        assertTrue(V3Dash.crossAllowed(true, 0, false, TARGET, TARGET));
        assertTrue(V3Dash.crossAllowed(true, 0, true, TARGET, TARGET));
    }

    @Test void renewedWindowAllowsRepeatedCrossesAtTheSameTarget() {
        assertEquals(60, V3Dash.CONTINUATION_TICKS);
        assertTrue(V3Dash.crossAllowed(false, 24, false, TARGET, TARGET));
        assertTrue(V3Dash.crossAllowed(false, 1, false, TARGET, TARGET));
        assertTrue(V3Dash.crossAllowed(false, 1, true, TARGET, TARGET));
    }

    @Test void lateInputIsRefused() {
        assertFalse(V3Dash.crossAllowed(false, 0, false, TARGET, TARGET));
        assertFalse(V3Dash.crossAllowed(false, -3, false, TARGET, TARGET));
    }

    @Test void theCrossMustBeAtTheDashedTarget() {
        assertFalse(V3Dash.crossAllowed(true, 0, false, TARGET, UUID.randomUUID()));
        assertFalse(V3Dash.crossAllowed(true, 0, false, TARGET, null));
        assertFalse(V3Dash.crossAllowed(false, 10, false, null, TARGET));
    }

    @Test void followCannotReleaseForeignOrStaleMotionOwners() {
        UUID session = UUID.randomUUID();
        var idle = new V3Motion.Lease();
        assertTrue(V3Dash.followMotionAllowed(V3State.IDLE, false, idle, session));
        assertFalse(V3Dash.followMotionAllowed(V3State.IDLE, true, idle, session));
        assertFalse(V3Dash.followMotionAllowed(V3State.TRAVEL, true, idle, session));
        for (var owner : V3Motion.Owner.values()) {
            var lease = new V3Motion.Lease();
            assertTrue(lease.acquire(owner, session, false));
            assertFalse(V3Dash.followMotionAllowed(V3State.IDLE, false, lease, session));
            assertEquals(owner == V3Motion.Owner.APPROACH,
                    V3Dash.followMotionAllowed(V3State.TRAVEL, true, lease, session));
            assertFalse(V3Dash.followMotionAllowed(V3State.TRAVEL, true, lease, UUID.randomUUID()));
            assertFalse(V3Dash.followMotionAllowed(V3State.CINEMATIC, true, lease, session));
            assertEquals(owner, lease.owner(), "refusing a follow must retain the existing lease");
        }
    }

    @Test void dashRangeComesFromConfigThroughTheSharedRule() {
        assertTrue(V3Dash.inDashRange(999 * 999, 999));
        assertFalse(V3Dash.inDashRange(999.01 * 999.01, 999));
        assertTrue(V3Dash.inDashRange(24 * 24, 24));
        assertFalse(V3Dash.inDashRange(24.5 * 24.5, 24));
    }

    @Test void initialDashLaunchesAreVerticalAndNeutralReversesWithTheSide() {
        var away = new net.minecraft.world.phys.Vec3(5, 2, 0);
        assertEquals(new net.minecraft.world.phys.Vec3(0, 1, 0), V3Dash.launchDirection(V3Direction.FORWARD, away));
        assertEquals(new net.minecraft.world.phys.Vec3(0, -1, 0), V3Dash.launchDirection(V3Direction.BACK, away));
        assertEquals(new net.minecraft.world.phys.Vec3(1, 0, 0), V3Dash.launchDirection(V3Direction.NONE, away));
        var reverse = V3Dash.launchDirection(V3Direction.NONE, away.scale(-1));
        assertArrayEquals(new double[]{-1, 0, 0}, new double[]{reverse.x, reverse.y, reverse.z}, 1e-6);
    }

    @Test void launchTravelsAtLeastFifteenBlocksAndOldChargedArcRemainsUnchanged() {
        var old = net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules.arcOffset(12);
        assertArrayEquals(new double[]{10, 6}, old, 1e-6);
        var end = net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules.arcOffset(24, V3Dash.LAUNCH_DISTANCE, 3);
        assertTrue(end[0] >= 15);
        assertEquals(0, end[1], 1e-6);
        var vertical = net.bullettrain.xenopixelsmod.combat.v2.V2ChargeRules.arcOffset(12, V3Dash.LAUNCH_DISTANCE, 0);
        assertArrayEquals(new double[]{10, 0}, vertical, 1e-6);
    }

    @Test void followLandingIsBehindTheTargetsBackAtTheConfiguredGap() {
        var target = new net.minecraft.world.phys.Vec3(10, 64, 10);
        // Body yaw 0 faces +Z in Minecraft, so "behind" is -Z.
        var behind = V3Dash.behind(target, 0f, 2.5);
        assertArrayEquals(new double[]{10, 64, 7.5}, new double[]{behind.x, behind.y, behind.z}, 1e-6);
        // Yaw 90 faces -X, so behind is +X.
        var east = V3Dash.behind(target, 90f, 3);
        assertArrayEquals(new double[]{13, 64, 10}, new double[]{east.x, east.y, east.z}, 1e-6);
        // Yaw 180 faces -Z, so behind is +Z.
        var south = V3Dash.behind(target, 180f, 2);
        assertArrayEquals(new double[]{10, 64, 12}, new double[]{south.x, south.y, south.z}, 1e-6);
    }

    @Test void followLandingUsesFarSideFromAttackerNotBodyRelativeFront() {
        // Attacker at +Z of target → far side is further -Z past the target.
        var attacker = new net.minecraft.world.phys.Vec3(10, 64, 20);
        var target = new net.minecraft.world.phys.Vec3(10, 64, 10);
        var far = V3Dash.farSideFromAttacker(attacker, target, 2.5);
        assertArrayEquals(new double[]{10, 64, 7.5}, new double[]{far.x, far.y, far.z}, 1e-6);
        // Attacker west of target → far side is further east.
        var fromWest = V3Dash.farSideFromAttacker(new net.minecraft.world.phys.Vec3(0, 64, 10), target, 3);
        assertArrayEquals(new double[]{13, 64, 10}, new double[]{fromWest.x, fromWest.y, fromWest.z}, 1e-6);
    }

    @Test void chainTimingDefaultsComeFromTheServerConfig() {
        assertEquals(V3Config.Values.DEFAULT_DASH_FOLLOW_WINDOW, V3Dash.CONTINUATION_TICKS);
        assertEquals(V3Config.Values.DEFAULT_DASH_LAUNCH, V3Dash.LAUNCH_DISTANCE);
        var defaults = new V3Config.Values();
        assertEquals(60, defaults.dragonDashFollowWindowTicks());
        assertEquals(8, defaults.dragonDashFollowCooldownTicks());
        assertEquals(2.5, defaults.dragonDashFollowDistance());
        assertEquals(20, defaults.dragonDashLaunchDistance());
        assertEquals(3.0, defaults.dragonDashSpeed());
        assertFalse(defaults.dashCamera(), "no dash camera unless the owner turns it on");
        assertTrue(defaults.strikeCinematicCamera());
        assertEquals(20, defaults.strikeLaunchDistance());
    }

    @Test void chaseAndCounterWindowsDoNotStealTheNBinding() {
        assertFalse(net.bullettrain.xenopixelsmod.client.combat.v3.V3InputLayer.dashFollow(
                V3State.IDLE, V3Window.CHASE, 40));
        assertFalse(net.bullettrain.xenopixelsmod.client.combat.v3.V3InputLayer.dashFollow(
                V3State.TRAVEL, V3Window.NONE, 0));
        assertTrue(net.bullettrain.xenopixelsmod.client.combat.v3.V3InputLayer.dashFollow(
                V3State.TRAVEL, V3Window.DASH_CROSS, 60));
        assertTrue(net.bullettrain.xenopixelsmod.client.combat.v3.V3InputLayer.dashFollow(
                V3State.IDLE, V3Window.DASH_CROSS, 1));
        assertFalse(net.bullettrain.xenopixelsmod.client.combat.v3.V3InputLayer.dashFollow(
                V3State.IDLE, V3Window.DASH_CROSS, 0));
    }
}
