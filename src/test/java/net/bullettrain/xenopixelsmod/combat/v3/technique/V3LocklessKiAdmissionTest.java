package net.bullettrain.xenopixelsmod.combat.v3.technique;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Owner rule: Strike lock can be required or optional via xenoset for melee and pure Ki separately. */
class V3LocklessKiAdmissionTest {
    @Test void lockRequirementsHonorStrikeAndKiFlagsIndependently() {
        // pureKi, strikeRequireLock, strikeKiRequireLock
        assertFalse(V3TechniqueRuntime.requiresApprovedLock(true, true, false));
        assertTrue(V3TechniqueRuntime.requiresApprovedLock(true, true, true));
        assertTrue(V3TechniqueRuntime.requiresApprovedLock(false, true, false),
                "melee/mixed default: approved lock required");
        assertFalse(V3TechniqueRuntime.requiresApprovedLock(false, false, false),
                "v3.strikeRequireLock false allows lockless melee/mixed look-aim");
        assertFalse(V3TechniqueRuntime.requiresApprovedLock(false, false, true),
                "melee uses strikeRequireLock only; Ki flag does not force melee lock");
    }

    @Test void effectiveRangeUsesTechniqueOverrideThenGlobalKiOrApproach() {
        assertEquals(40.0, V3TechniqueRuntime.effectiveRange(40.0, true, 64.0, 32.0));
        assertEquals(64.0, V3TechniqueRuntime.effectiveRange(null, true, 64.0, 32.0));
        assertEquals(32.0, V3TechniqueRuntime.effectiveRange(null, false, 64.0, 32.0));
        assertEquals(12.0, V3TechniqueRuntime.effectiveRange(12.0, false, 64.0, 32.0));
    }

    @Test void castTargetSurvivesWithoutApprovedLockIdentity() {
        assertTrue(V3TechniqueRuntime.castTargetMatches(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                null,
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")));
        assertFalse(V3TechniqueRuntime.castTargetMatches(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                null,
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")));
        assertTrue(V3TechniqueRuntime.castTargetMatches(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000099"),
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")),
                "approved lock still authorizes its own identity");
    }

    @Test void pureKiCatalogEntriesExistForLocklessPath() {
        List<V3TechniqueDefinition> pure = V3TechniqueCatalog.all().stream()
                .filter(net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi::pureKi)
                .toList();
        assertFalse(pure.isEmpty());
        assertTrue(pure.stream().allMatch(t -> t.kiTechnique() != null && !t.kiTechnique().isBlank()));
    }
}
