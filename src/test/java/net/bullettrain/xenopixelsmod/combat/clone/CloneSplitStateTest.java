package net.bullettrain.xenopixelsmod.combat.clone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CloneSplitStateTest {
    @Test
    void damagedFighterDistributesOnlyCurrentHealthAndFailedSpawnsCostNothing() {
        CloneSplitState<String> split = new CloneSplitState<>(List.of("a", "b"));
        float share = CloneSplitState.healthShare(7f, split.bodies());
        assertEquals(3, split.bodies());
        assertEquals(7f, share * split.bodies(), 1e-6f);
        assertEquals(7f, CloneSplitState.healthShare(7f, 1));
    }

    @Test
    void fractionalHealthDoesNotMintOneHealthPerBody() {
        assertEquals(0.125f, CloneSplitState.healthShare(0.5f, 4));
        assertEquals(0f, CloneSplitState.healthShare(0f, 4));
    }

    @Test
    void recallRemainsPendingAndCannotRestartUntilAllBodiesAreGone() {
        CloneSplitState<String> split = new CloneSplitState<>(List.of("a", "b"));
        assertTrue(split.beginRecall());
        assertTrue(split.isRecalling());
        assertFalse(split.beginRecall());
        assertFalse(split.isEmpty());
        assertEquals(2f, split.arrive("a", 2f));
        assertFalse(split.isEmpty());
        assertTrue(split.remove("b"));
        assertTrue(split.isEmpty());
        assertFalse(split.beginRecall());
    }

    @Test
    void removalForfeitsHealthAndCannotRefundLater() {
        CloneSplitState<String> split = new CloneSplitState<>(List.of("copy"));
        split.beginRecall();
        assertTrue(split.remove("copy"));
        assertFalse(split.remove("copy"));
        assertEquals(0f, split.arrive("copy", 5f));
        assertTrue(split.isEmpty());
    }

    @Test
    void onlySurvivingHealthReturnsExactlyOnce() {
        CloneSplitState<String> split = new CloneSplitState<>(List.of("a", "b", "c"));
        assertEquals(0f, split.arrive("a", 5f), "travel is not recall");
        assertTrue(split.contains("a"));
        split.beginRecall();
        split.remove("b");
        float owner = 3f;
        owner = CloneSplitState.reunitedHealth(owner, 20f, split.arrive("a", 2f));
        owner = CloneSplitState.reunitedHealth(owner, 20f, split.arrive("a", 2f));
        owner = CloneSplitState.reunitedHealth(owner, 20f, split.arrive("b", 5f));
        owner = CloneSplitState.reunitedHealth(owner, 20f, split.arrive("c", 4f));
        assertEquals(9f, owner);
        assertTrue(split.isEmpty());
    }

    @Test
    void recallRespectsCurrentMaxAndNeverResurrectsOwner() {
        assertEquals(6f, CloneSplitState.reunitedHealth(5f, 6f, 4f));
        assertEquals(0f, CloneSplitState.reunitedHealth(0f, 20f, 4f));
        assertEquals(5f, CloneSplitState.reunitedHealth(5f, 20f, -4f));
    }

    @Test
    void repeatedUndamagedSplitRecallNeverHealsDamagedOwner() {
        float health = 7f;
        for (int cycle = 0; cycle < 100; cycle++) {
            CloneSplitState<String> split = new CloneSplitState<>(List.of("a", "b", "c"));
            float share = CloneSplitState.healthShare(health, split.bodies());
            health -= 3f * share;
            split.beginRecall();
            for (String member : split.members()) {
                health = CloneSplitState.reunitedHealth(health, 20f, split.arrive(member, share));
            }
            assertEquals(7f, health, 1e-6f);
        }
    }

    @Test
    void lossDoesNotReassignSlotsOrChangeOriginalPowerDivision() {
        CloneSplitState<String> split = new CloneSplitState<>(List.of("a", "b", "c"));
        split.remove("b");
        assertEquals(List.of("a", "c"), split.members());
        assertEquals(4, split.bodies());
        assertEquals(0.25f, CloneFormation.damageShare(split.bodies(), 0));
        split.remove("a");
        split.remove("c");
        assertTrue(split.isEmpty());
    }
}
