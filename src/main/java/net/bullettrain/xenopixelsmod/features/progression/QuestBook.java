package net.bullettrain.xenopixelsmod.features.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every quest a player is on, and every one they have finished.
 *
 * <p>Deliberately pure - no player, no server, no Minecraft beyond NBT - so all of its rules can be
 * tested without a running game. {@code XenoPlayerData} owns one of these and does nothing with
 * quests itself.
 *
 * <p>Completed ids are kept because {@code MasterPrerequisites} needs them. Its gate requires a
 * quest to be both named and finished, and before this the id was erased at the moment of
 * completion, so the gate could never pass at any point in time.
 */
public final class QuestBook {

    /** More than this and the log stops being a log. Also bounds the client sync payload. */
    public static final int MAX_ACTIVE = 32;

    /** Completed ids live on disk forever; without a ceiling a long world grows without limit. */
    public static final int MAX_COMPLETED = 512;

    /** Per active quest. Was one player-wide set before quests could run together. */
    public static final int MAX_VISITED = 256;

    private final Map<String, ActiveQuest> active = new LinkedHashMap<>();
    private final Map<String, Long> completed = new LinkedHashMap<>();
    private final Map<String, Long> completedReal = new LinkedHashMap<>();

    private static String key(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isActive(String id) {
        return active.containsKey(key(id));
    }

    /** One active quest, or null. */
    public ActiveQuest active(String id) {
        return active.get(key(id));
    }

    /** Every active quest, in the order they were started. */
    public List<ActiveQuest> actives() {
        return List.copyOf(active.values());
    }

    /**
     * Starts a quest.
     *
     * <p>A quest already completed may be started again: repeat rules are not implemented, and
     * silently refusing would read as a bug rather than as a rule.
     *
     * @return null on success, or why it was refused
     */
    public String start(String id, int target) {
        return start(id, target, null);
    }

    public String start(String id, int target, net.minecraft.world.entity.Entity giver) {
        String wanted = key(id);
        if (wanted.isEmpty()) {
            return "that is not a quest id";
        }
        if (active.containsKey(wanted)) {
            return "you are already on " + wanted;
        }
        if (active.size() >= MAX_ACTIVE) {
            return "you are already on " + MAX_ACTIVE + " quests; finish or abandon one first";
        }
        ActiveQuest quest = new ActiveQuest(wanted, target);
        quest.setGiver(giver);
        active.put(wanted, quest);
        return null;
    }

    /** Forgets that a quest was ever completed; an active copy is untouched. */
    public void forgetCompleted(String id) {
        String wanted = key(id);
        completed.remove(wanted);
        completedReal.remove(wanted);
    }

    /** Every active and completed quest gone, as a fresh player. */
    public void clear() {
        active.clear();
        completed.clear();
        completedReal.clear();
    }

    /** Drops a quest without completing it. Its history, if any, is untouched. */
    public void abandon(String id) {
        active.remove(key(id));
    }

    /** Moves a quest from active to completed, remembering when. */
    public void complete(String id, long gameTime) {
        complete(id, gameTime, System.currentTimeMillis());
    }

    public void complete(String id, long gameTime, long epochMillis) {
        String wanted = key(id);
        active.remove(wanted);
        if (wanted.isEmpty()) {
            return;
        }
        // Re-inserted so a repeat completion moves to the back of the eviction order.
        completed.remove(wanted);
        completed.put(wanted, gameTime);
        completedReal.put(wanted, epochMillis);
        Iterator<String> oldest = completed.keySet().iterator();
        while (completed.size() > MAX_COMPLETED && oldest.hasNext()) {
            completedReal.remove(oldest.next());
            oldest.remove();
        }
    }

    public boolean hasCompleted(String id) {
        return completed.containsKey(key(id));
    }

    /** When a quest was completed, or 0 when it never was. */
    public long completedAt(String id) {
        return completed.getOrDefault(key(id), 0L);
    }

    public long completedAtReal(String id) {
        return completedReal.getOrDefault(key(id), 0L);
    }

    /** Completed ids, oldest first. */
    public List<String> completed() {
        return List.copyOf(completed.keySet());
    }

    public void saveTo(CompoundTag tag) {
        ListTag list = new ListTag();
        for (ActiveQuest quest : active.values()) {
            list.add(quest.save());
        }
        tag.put("ActiveQuests", list);

        CompoundTag done = new CompoundTag();
        completed.forEach(done::putLong);
        tag.put("CompletedQuests", done);
        CompoundTag real = new CompoundTag();
        completedReal.forEach(real::putLong);
        tag.put("CompletedQuestsReal", real);
    }

    /** Replaces everything. An empty tag empties the book rather than leaving it stale. */
    public void loadFrom(CompoundTag tag) {
        active.clear();
        completed.clear();
        completedReal.clear();
        if (tag == null) {
            return;
        }
        ListTag list = tag.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(MAX_ACTIVE, list.size()); i++) {
            ActiveQuest quest = ActiveQuest.load(list.getCompound(i));
            if (quest != null) {
                active.put(quest.id(), quest);
            }
        }
        CompoundTag done = tag.getCompound("CompletedQuests");
        CompoundTag real = tag.getCompound("CompletedQuestsReal");
        List<String> keys = new ArrayList<>(done.getAllKeys());
        for (int i = Math.max(0, keys.size() - MAX_COMPLETED); i < keys.size(); i++) {
            completed.put(keys.get(i), done.getLong(keys.get(i)));
            if (real.contains(keys.get(i))) completedReal.put(keys.get(i), real.getLong(keys.get(i)));
        }
    }
}
