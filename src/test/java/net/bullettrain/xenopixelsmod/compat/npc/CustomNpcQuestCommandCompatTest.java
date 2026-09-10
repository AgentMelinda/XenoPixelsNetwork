package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomNpcQuestCommandCompatTest {
    @Test
    void normalizesEmbeddedPointCommandsOnly() {
        assertEquals("reward: dmzpoints add 5000 @dp",
                CustomNpcQuestCommandCompat.normalizeEmbedded(
                        "reward: dmzpoints add 5000 {RefPlayer}"));
        assertEquals("give {RefPlayer} stone",
                CustomNpcQuestCommandCompat.normalizeEmbedded("give {RefPlayer} stone"));
    }
    @Test
    void adaptsDedicatedServerQuestReward() {
        assertEquals("xenopoints add 5000 @dp",
                CustomNpcQuestCommandCompat.normalize("xenopoints add 5000 {RefPlayer}"));
    }

    @Test
    void adaptsDragonMineZPointsRewardsToo() {
        assertEquals("dmzpoints add 5000 @dp",
                CustomNpcQuestCommandCompat.normalize("dmzpoints add 5000 {RefPlayer}"));
    }

    @Test
    void acceptsOptionalSlashWhitespaceAndCommandCase() {
        assertEquals("  / XenoPoints add 5000 @dp  ",
                CustomNpcQuestCommandCompat.normalize("  / XenoPoints add 5000 {RefPlayer}  "));
    }

    @Test
    void replacesEveryExactTokenForXenoPoints() {
        assertEquals("xenopoints add 1 @dp @dp",
                CustomNpcQuestCommandCompat.normalize("xenopoints add 1 {RefPlayer} {RefPlayer}"));
    }

    @Test
    void leavesExistingValidTargetsUnchanged() {
        assertEquals("xenopoints add 5000 @dp",
                CustomNpcQuestCommandCompat.normalize("xenopoints add 5000 @dp"));
        assertEquals("xenopoints add 5000 @a",
                CustomNpcQuestCommandCompat.normalize("xenopoints add 5000 @a"));
        assertEquals("xenopoints add 5000 PlayerName",
                CustomNpcQuestCommandCompat.normalize("xenopoints add 5000 PlayerName"));
    }

    @Test
    void doesNotRewriteOtherCommandsOrNearMatches() {
        assertEquals("give {RefPlayer} stone",
                CustomNpcQuestCommandCompat.normalize("give {RefPlayer} stone"));
        assertEquals("xenopoints add 5000 {refplayer}",
                CustomNpcQuestCommandCompat.normalize("xenopoints add 5000 {refplayer}"));
        assertEquals("xenopointsExtra add 5000 {RefPlayer}",
                CustomNpcQuestCommandCompat.normalize("xenopointsExtra add 5000 {RefPlayer}"));
    }

    @Test
    void preservesNull() {
        assertNull(CustomNpcQuestCommandCompat.normalize(null));
    }
}
