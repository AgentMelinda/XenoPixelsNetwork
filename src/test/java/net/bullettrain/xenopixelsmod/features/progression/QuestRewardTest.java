package net.bullettrain.xenopixelsmod.features.progression;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quest rewards, which quests did not previously have.
 *
 * <p>{@code completeQuest} handed out a flat two skill points for every quest because it had no way
 * to know which one had finished. These tests cover the shape of a reward and the compatibility
 * rule that matters most: a quest that declares nothing still pays exactly what it always did, so
 * nobody part-way through a quest finds it worth less than when they started.
 *
 * <p>{@code grant} itself needs a live {@code ServerPlayer}; it is checked in game.
 */
class QuestRewardTest {

    @Test
    void theDefaultIsTheRewardQuestsAlwaysPaid() {
        assertEquals(2, QuestReward.DEFAULT.skillPoints(),
                "two skill points is what every quest paid before rewards existed");
        assertEquals(0, QuestReward.DEFAULT.experience());
        assertTrue(QuestReward.DEFAULT.items().isEmpty());
        assertTrue(QuestReward.DEFAULT.commands().isEmpty());
    }

    @Test
    void aQuestThatDeclaresNoRewardGetsTheDefault() {
        ParallelQuests.QuestDef def =
                new ParallelQuests.QuestDef("x", "X", "desc", 1);
        assertSame(QuestReward.DEFAULT, def.reward());

        // And an explicit null is the same thing rather than an NPE waiting at completion time.
        ParallelQuests.QuestDef nulled =
                new ParallelQuests.QuestDef("x", "X", "desc", 1, null, null);
        assertSame(QuestReward.DEFAULT, nulled.reward());
        assertEquals(QuestObjective.KILL_MOBS, nulled.goal().type(),
                "a quest that declares no goal counts hostile kills, as it always did");
    }

    @Test
    void anUnknownQuestIdStillPaysSomething() {
        // Quest ids can come from a datapack or an old save. Completing one we no longer define
        // should not silently pay nothing.
        assertSame(QuestReward.DEFAULT, ParallelQuests.rewardFor("no-such-quest"));
        assertSame(QuestReward.DEFAULT, ParallelQuests.rewardFor(null));
        assertSame(QuestReward.DEFAULT, ParallelQuests.rewardFor(""));
    }

    @Test
    void everyShippedQuestStillPaysAtLeastTheOldSkillPoints() {
        // The compatibility rule: adding rewards must not make an in-progress quest worth less.
        for (ParallelQuests.QuestDef def : ParallelQuests.QUESTS.values()) {
            assertTrue(def.reward().skillPoints() >= 2,
                    def.id() + " pays fewer skill points than it used to");
        }
    }

    @Test
    void negativeAmountsAreClampedRatherThanPaidOut() {
        QuestReward reward = new QuestReward(-5, -10, List.of(), List.of());
        assertEquals(0, reward.skillPoints());
        assertEquals(0, reward.experience());
        assertTrue(reward.isEmpty());
    }

    @Test
    void aStackOfZeroBecomesOne() {
        // "Give them zero diamonds" is never what a pack meant.
        assertEquals(1, new QuestReward.ItemGrant("minecraft:diamond", 0).count());
        assertEquals(1, new QuestReward.ItemGrant("minecraft:diamond", -3).count());
        assertEquals(7, new QuestReward.ItemGrant("minecraft:diamond", 7).count());
    }

    @Test
    void theRewardIsImmutableOnceBuilt() {
        // It is handed to whatever completes the quest; a caller must not be able to edit it.
        QuestReward reward = new QuestReward(1, 0,
                List.of(new QuestReward.ItemGrant("minecraft:diamond", 1)), List.of("say hi"));
        assertThrows(UnsupportedOperationException.class,
                () -> reward.items().add(new QuestReward.ItemGrant("minecraft:dirt", 1)));
        assertThrows(UnsupportedOperationException.class, () -> reward.commands().add("say bye"));
    }

    @Test
    void nullListsBecomeEmptyOnesRatherThanFailingLater() {
        QuestReward reward = new QuestReward(1, 1, null, null);
        assertNotNull(reward.items());
        assertNotNull(reward.commands());
        assertTrue(reward.items().isEmpty());
        assertTrue(reward.commands().isEmpty());
    }

    @Test
    void describeReadsAsSomethingWorthPuttingInChat() {
        QuestReward reward = new QuestReward(2, 30,
                List.of(new QuestReward.ItemGrant("minecraft:diamond", 3)), List.of("say hi"));
        String text = reward.describe();
        assertTrue(text.contains("+2 skill points"), text);
        assertTrue(text.contains("+30 xp"), text);
        assertTrue(text.contains("3x diamond"), text);
        // Commands are counted, not pasted: "/give @s ..." tells a player nothing useful.
        assertTrue(text.contains("1 reward action"), text);
        assertFalse(text.contains("say hi"), text);
    }

    @Test
    void oneSkillPointIsNotPluralised() {
        assertTrue(QuestReward.ofSkillPoints(1).describe().contains("+1 skill point"));
        assertFalse(QuestReward.ofSkillPoints(1).describe().contains("points"));
    }

    @Test
    void anEmptyRewardSaysSoRatherThanReadingAsBlank() {
        assertEquals("nothing", new QuestReward(0, 0, List.of(), List.of()).describe());
    }

    @Test
    void aNonsenseItemIdResolvesToNullRatherThanAir() {
        // BuiltInRegistries.ITEM.get answers AIR for an unknown id, and silently handing out air
        // is worse than skipping the entry, so resolveItem uses getOptional.
        assertEquals(null, QuestReward.resolveItem("definitely:not-an-item"));
        assertEquals(null, QuestReward.resolveItem(null));
        assertEquals(null, QuestReward.resolveItem(""));
        assertEquals(null, QuestReward.resolveItem("not a resource location"));
    }

    @Test
    void factionPointsArrivedWithTheFactionSystem() {
        // This test used to assert the opposite: doco.md lists faction points as a reward type, and
        // the field was deliberately left out while there was no faction system to receive them -
        // a number written into a void. It was written as the reminder to add them when factions
        // landed, and it fired the moment they did. XenoFactions and XenoPlayerData's per-faction
        // standing are both here now, so the field belongs.
        boolean present = false;
        for (var component : QuestReward.class.getRecordComponents()) {
            present |= component.getName().toLowerCase().contains("faction");
        }
        assertTrue(present, "faction points should be a reward now that factions exist");
    }

    @Test
    void onlyNativeXenoNpcQuestGiversCanGrantXenoSkillPoints() {
        QuestReward declared = new QuestReward(2, 12, List.of(), List.of("say paid"));
        QuestReward imported = QuestRewardOriginPolicy.forCompletion(declared, false);
        assertEquals(0, imported.skillPoints());
        assertEquals(12, imported.experience());
        assertEquals(List.of("say paid"), imported.commands(),
                "reward actions remain separate from the XenoSkill toggle");
        assertEquals(2, QuestRewardOriginPolicy.forCompletion(declared, true).skillPoints());
        assertTrue(QuestRewardOriginPolicy.forCompletion(null, true).isEmpty(),
                "an unresolved quest must not fall back to the legacy points default");
    }
}
