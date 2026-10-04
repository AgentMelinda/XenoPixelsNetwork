package net.bullettrain.xenopixelsmod.features.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One quest a player is part-way through.
 *
 * <p>Its {@code visited} set belongs to <em>this</em> quest. It used to be one set on the player,
 * which is correct for one quest and wrong for several: a "speak to three masters" quest and a kill
 * quest running together would have consumed each other's entries.
 */
public final class ActiveQuest {

    public static final int MAX_STEPS = 8;

    private final String id;
    private final int target;
    private int progress;
    private boolean ready;
    private final int[] stepProgress = new int[MAX_STEPS];
    private final Set<String> visited = new LinkedHashSet<>();
    private String giverDimension = "";
    private double giverX;
    private double giverY;
    private double giverZ;
    private boolean xenoNpcGiver;
    /** The entity that gave the quest, so a "kill target hunts player" quest never turns it hostile. */
    private java.util.UUID giverId;

    public ActiveQuest(String id, int target) {
        this.id = id == null ? "" : id;
        this.target = Math.max(1, target);
    }

    public String id() {
        return id;
    }

    public int progress() {
        return progress;
    }

    public int target() {
        return target;
    }

    /** Whether this quest has reached its target and is waiting to be handed in. */
    public boolean ready() {
        return ready;
    }

    public void markReady() {
        ready = true;
    }

    /** Stores the quest giver position so reward {@code @p} selectors use their location. */
    public void setGiver(Entity giver) {
        if (giver == null || giver.level().isClientSide) return;
        xenoNpcGiver = giver instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
        giverId = giver.getUUID();
        giverDimension = giver.level().dimension().location().toString();
        giverX = giver.getX();
        giverY = giver.getY();
        giverZ = giver.getZ();
    }

    public ResourceLocation giverDimension() {
        return ResourceLocation.tryParse(giverDimension);
    }

    public Vec3 giverPosition() {
        return giverDimension() != null ? new Vec3(giverX, giverY, giverZ) : null;
    }

    /** Only an Xeno NPC dialogue may enable this quest's optional XenoSkill-point reward. */
    public boolean xenoNpcGiver() { return xenoNpcGiver; }

    /** The giving entity's UUID, or null for a quest started without one (command, old save). */
    public java.util.UUID giverId() { return giverId; }

    /**
     * Advances this quest.
     *
     * <p>A quest already at target answers false rather than true a second time: a quest waiting to
     * be handed in must not keep firing completions for kills it will never use.
     *
     * @return true when this call reached the target
     */
    public boolean addProgress(int amount) {
        if (progress >= target) {
            return false;
        }
        progress = Math.min(target, progress + Math.max(0, amount));
        stepProgress[0] = progress;
        return progress >= target;
    }

    /**
     * Advances one objective of a quest that has several.
     *
     * @return true when every objective has reached its own target
     */
    public boolean addStepProgress(int index, int amount, int[] required) {
        if (required == null || required.length == 0 || index < 0 || index >= required.length
                || index >= MAX_STEPS) {
            return false;
        }
        int cap = Math.max(1, required[index]);
        if (stepProgress[index] < cap) {
            stepProgress[index] = Math.min(cap, stepProgress[index] + Math.max(0, amount));
            if (index == 0) {
                progress = Math.min(target, stepProgress[0]);
            }
        }
        return allStepsDone(required.length, required);
    }

    /** Whether every stored step has reached the matching requirement. */
    public boolean allStepsDone(int steps, int[] required) {
        int count = Math.max(1, Math.min(MAX_STEPS, steps));
        for (int i = 0; i < count; i++) {
            int need = required == null ? (i == 0 ? target : 1) : Math.max(1, required[i]);
            if (stepProgress[i] < need) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sets one objective's progress outright, for scripts (XenoAPI {@code IQuestObjective.setProgress}).
     * Clamped to 0..{@code cap}; the quest's own completion check still decides when it finishes.
     */
    public void setStepProgress(int index, int value, int cap) {
        if (index < 0 || index >= MAX_STEPS) {
            return;
        }
        stepProgress[index] = Math.max(0, Math.min(Math.max(1, cap), value));
        if (index == 0) {
            progress = Math.min(target, stepProgress[0]);
        }
    }

    public int stepProgress(int index) {
        return index < 0 || index >= MAX_STEPS ? 0 : stepProgress[index];
    }

    /**
     * Records that this quest has counted {@code key}.
     *
     * @return true the first time only, so a caller counts it once
     */
    public boolean markVisited(String key) {
        if (key == null || key.isEmpty() || visited.size() >= QuestBook.MAX_VISITED) {
            return false;
        }
        return visited.add(key);
    }

    public boolean hasVisited(String key) {
        return key != null && visited.contains(key);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putInt("Target", target);
        tag.putInt("Progress", progress);
        tag.putBoolean("Ready", ready);
        if (!giverDimension.isBlank()) {
            tag.putString("GiverDimension", giverDimension);
            tag.putDouble("GiverX", giverX);
            tag.putDouble("GiverY", giverY);
            tag.putDouble("GiverZ", giverZ);
        }
        tag.putBoolean("XenoNpcGiver", xenoNpcGiver);
        if (giverId != null) tag.putUUID("GiverId", giverId);
        ListTag steps = new ListTag();
        for (int value : stepProgress) {
            steps.add(net.minecraft.nbt.IntTag.valueOf(value));
        }
        tag.put("StepProgress", steps);
        ListTag seen = new ListTag();
        for (String key : visited) {
            seen.add(StringTag.valueOf(key));
        }
        tag.put("Visited", seen);
        return tag;
    }

    /** Reads one quest, or null when the tag names none. */
    public static ActiveQuest load(CompoundTag tag) {
        if (tag == null || tag.getString("Id").isEmpty()) {
            return null;
        }
        ActiveQuest quest = new ActiveQuest(tag.getString("Id"), tag.getInt("Target"));
        quest.progress = Math.min(quest.target, Math.max(0, tag.getInt("Progress")));
        quest.stepProgress[0] = quest.progress;
        quest.ready = tag.getBoolean("Ready");
        quest.giverDimension = tag.getString("GiverDimension");
        quest.giverX = tag.getDouble("GiverX");
        quest.giverY = tag.getDouble("GiverY");
        quest.giverZ = tag.getDouble("GiverZ");
        quest.xenoNpcGiver = tag.getBoolean("XenoNpcGiver");
        if (tag.hasUUID("GiverId")) quest.giverId = tag.getUUID("GiverId");
        ListTag steps = tag.getList("StepProgress", Tag.TAG_INT);
        for (int i = 0; i < Math.min(MAX_STEPS, steps.size()); i++) {
            quest.stepProgress[i] = Math.max(0, steps.getInt(i));
        }
        ListTag seen = tag.getList("Visited", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(QuestBook.MAX_VISITED, seen.size()); i++) {
            quest.visited.add(seen.getString(i));
        }
        return quest;
    }
}
