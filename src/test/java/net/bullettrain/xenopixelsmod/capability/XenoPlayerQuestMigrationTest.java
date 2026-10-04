package net.bullettrain.xenopixelsmod.capability;

import net.bullettrain.xenopixelsmod.features.progression.QuestBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A save written before quests could run together still loads.
 *
 * <p>The old shape was three scalars plus one player-wide visited set. Losing a player's
 * in-progress quest on upgrade would be a silent data loss, so the old keys are read once and
 * folded into the book.
 */
class XenoPlayerQuestMigrationTest {

    /** A save in the pre-book shape. */
    private static CompoundTag legacySave() {
        CompoundTag tag = new CompoundTag();
        tag.putString("QuestId", "kill_mobs");
        tag.putInt("QuestProgress", 4);
        tag.putInt("QuestTarget", 10);
        ListTag visited = new ListTag();
        visited.add(StringTag.valueOf("npc-1"));
        tag.put("QuestVisited", visited);
        return tag;
    }

    @Test
    void anOldSingleQuestBecomesOneActiveQuest() {
        XenoPlayerData data = new XenoPlayerData();
        data.loadNBT(legacySave());

        QuestBook book = data.quests();
        assertEquals(1, book.actives().size());
        assertTrue(book.isActive("kill_mobs"));
        assertEquals(4, book.active("kill_mobs").progress());
        assertEquals(10, book.active("kill_mobs").target());
        assertTrue(book.active("kill_mobs").hasVisited("npc-1"),
                "the old player-wide visited set belongs to the quest that was running");
    }

    @Test
    void anOldSaveWithNoQuestLoadsEmpty() {
        XenoPlayerData data = new XenoPlayerData();
        data.loadNBT(new CompoundTag());
        assertTrue(data.quests().actives().isEmpty());
        assertTrue(data.quests().completed().isEmpty());
    }

    @Test
    void theNextSaveWritesTheNewShapeAndDropsTheOldKeys() {
        XenoPlayerData data = new XenoPlayerData();
        data.loadNBT(legacySave());

        CompoundTag written = new CompoundTag();
        data.saveNBT(written);
        assertTrue(written.contains("ActiveQuests"));
        assertFalse(written.contains("QuestId"), "the old shape is not written back");
        assertFalse(written.contains("QuestProgress"));
        assertFalse(written.contains("QuestVisited"));
    }

    @Test
    void theBookRoundTripsThroughPlayerData() {
        XenoPlayerData data = new XenoPlayerData();
        data.quests().start("a", 3);
        data.quests().active("a").addProgress(1);
        data.quests().start("b", 1);
        data.quests().complete("b", 77L);

        CompoundTag tag = new CompoundTag();
        data.saveNBT(tag);
        XenoPlayerData reloaded = new XenoPlayerData();
        reloaded.loadNBT(tag);

        assertEquals(1, reloaded.quests().active("a").progress());
        assertTrue(reloaded.quests().hasCompleted("b"));
    }

    @Test
    void questsSurviveTheDeathCopy() {
        // copyFrom runs on respawn and on the end-return clone. A player losing every quest on
        // death would be worse than losing the inventory they keep.
        XenoPlayerData before = new XenoPlayerData();
        before.quests().start("a", 3);
        before.quests().active("a").addProgress(2);
        before.quests().start("done", 1);
        before.quests().complete("done", 5L);

        XenoPlayerData after = new XenoPlayerData();
        after.copyFrom(before);

        assertEquals(2, after.quests().active("a").progress());
        assertTrue(after.quests().hasCompleted("done"));
    }
}
