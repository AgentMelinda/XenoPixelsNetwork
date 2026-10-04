package net.bullettrain.xenopixelsmod.features.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestCommandTargetsTest {
    @Test
    void resolvesClaimantAliasAndPreservesNearestPlayerSelector() {
        assertEquals("give Alex minecraft:diamond 1; say @p", QuestCommandTargets.resolve(
                "give @dp minecraft:diamond 1; say @p", "Alex"));
    }
}
