package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V3TargetingRulesTest {
    @Test void primaryRayGeometryDiffersFromExplicitValidatedCycle() {
        Vec3 from = Vec3.ZERO, to = new Vec3(0, 0, 24);
        assertTrue(Double.isFinite(V3TargetingRules.rayDistance(new AABB(-1, -1, 6, 1, 1, 8), from, to)));
        assertEquals(Double.POSITIVE_INFINITY, V3TargetingRules.rayDistance(new AABB(6, -1, 6, 8, 1, 8), from, to));
        UUID approved = UUID.randomUUID(), candidate = UUID.randomUUID();
        assertTrue(V3TargetingRules.cycleAllowed(approved, candidate, true, true), "Validated explicit cycle can leave the eye ray");
        assertFalse(V3TargetingRules.cycleAllowed(null, candidate, true, true), "Cycle cannot create a cold lock");
        assertFalse(V3TargetingRules.cycleAllowed(approved, null, true, true));
        assertFalse(V3TargetingRules.cycleAllowed(approved, candidate, false, true));
        assertFalse(V3TargetingRules.cycleAllowed(approved, candidate, true, false));
        assertEquals(14, V3Input.COUNTER.ordinal(), "Existing wire intents retain ordinal");
        assertEquals(15, V3Input.LOCK_CYCLE.ordinal());
    }
    @Test void rangeIncludes999ButRejectsBeyondAndNonFinite() {
        assertTrue(V3TargetingRules.inRange(999 * 999, 999));
        assertFalse(V3TargetingRules.inRange(999.01 * 999.01, 999));
        assertFalse(V3TargetingRules.inRange(Double.NaN, 999));
        assertFalse(V3TargetingRules.inRange(Double.POSITIVE_INFINITY, 999));
        assertFalse(V3TargetingRules.inRange(-1, 999));
        assertFalse(V3TargetingRules.inRange(1, Double.NaN));
    }
    @Test void staleRevisionsAndReusedEntityIdsCannotReplaceIdentity() {
        UUID approved = UUID.randomUUID();
        assertTrue(V3TargetingRules.matches(approved, approved));
        assertFalse(V3TargetingRules.matches(approved, UUID.randomUUID()));
        assertFalse(V3TargetingRules.matches(approved, null));
        assertFalse(V3TargetingRules.newer(7, 7));
        assertFalse(V3TargetingRules.newer(6, 7));
        assertTrue(V3TargetingRules.newer(8, 7));
    }
    @Test void snapshotUpdatesAreSignificantAndAtMostEveryTwoTicks() {
        var old = new V3TargetSnapshot(UUID.randomUUID(), 4, Vec3.ZERO, Vec3.ZERO, 1);
        assertFalse(V3TargetingRules.motionDue(old, new Vec3(1, 0, 0), Vec3.ZERO, 11, 10));
        assertTrue(V3TargetingRules.motionDue(old, new Vec3(1, 0, 0), Vec3.ZERO, 12, 10));
        assertFalse(V3TargetingRules.motionDue(old, Vec3.ZERO, Vec3.ZERO, 100, 10));
        assertFalse(V3TargetingRules.motionDue(old, new Vec3(0.00001, 0, 0), Vec3.ZERO, 100, 10));
        assertTrue(V3TargetingRules.motionDue(old, Vec3.ZERO, new Vec3(0.1, 0, 0), 12, 10));
    }
    @Test void lockRangeIsTheFullV3RangeAndDoesNotFollowTheDashSetting() throws java.io.IOException {
        assertEquals(999.0, V3TargetingRules.LOCK_RANGE);
        // A dash range of 24 must not drop a lock held from 25 or from 900 blocks away.
        assertTrue(V3TargetingRules.inRange(25.0 * 25.0, V3TargetingRules.LOCK_RANGE));
        assertTrue(V3TargetingRules.inRange(900.0 * 900.0, V3TargetingRules.LOCK_RANGE));
        assertFalse(V3TargetingRules.inRange(999.01 * 999.01, V3TargetingRules.LOCK_RANGE));
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        while (dir != null && !java.nio.file.Files.isDirectory(dir.resolve("src/main/java/net/bullettrain"))) dir = dir.getParent();
        String targeting = java.nio.file.Files.readString(dir.resolve(
                "src/main/java/net/bullettrain/xenopixelsmod/combat/v3/V3Targeting.java"));
        assertFalse(targeting.contains("dragonDashRange"), "lock range must not be read from the dash setting");
    }

    @Test void techniquesAreFreeWhileSparkingAndPricedOtherwise() {
        assertEquals(0f, V3Resources.kiCost(40f, true));
        assertEquals(40f, V3Resources.kiCost(40f, false));
        assertEquals(0f, V3Resources.kiCost(-5f, false));
        assertEquals(0f, V3Resources.kiCost(Float.NaN, false));
    }
}
