package net.bullettrain.xenopixelsmod.npc.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What an NPC leaves behind, and how much experience it is worth.
 *
 * <p>Nine rows, because the reference editor's Inventory page draws nine drop chances. Those nine
 * belong here rather than to the six worn slots - the page has seven slot buttons and nine chance
 * fields, so they were never one per slot, and the spec's own summary lists "drop items/chances"
 * separately from the weapon and armor slots.
 *
 * <p>Experience lives here too because it is the same page and the same moment: both are decided
 * when the NPC dies.
 */
public final class NpcDropList {

    /** The page draws nine, so nine is what an operator can fill. */
    public static final int MAX_DROPS = 9;

    /**
     * The reference GUI's own cap on the experience fields.
     *
     * <p>A short's worth, which is where it comes from; kept because an NPC worth more experience
     * than that is worth more than a wither, and an operator who typed an extra digit meant a
     * smaller number.
     */
    public static final int MAX_EXP = 32767;

    private static final String TAG_DROPS = "NpcDrops";
    private static final String TAG_MODE = "LootMode";
    private static final String TAG_MIN_EXP = "MinExp";
    private static final String TAG_MAX_EXP = "MaxExp";

    private final List<NpcDrop> drops = new ArrayList<>();
    private NpcLootMode lootMode = NpcLootMode.NORMAL;
    private int minExp;
    private int maxExp;

    public List<NpcDrop> all() {
        return Collections.unmodifiableList(drops);
    }

    public int size() {
        return drops.size();
    }

    /** One row, or an empty drop when the index is past the end. */
    public NpcDrop get(int index) {
        return index >= 0 && index < drops.size() ? drops.get(index) : NpcDrop.empty();
    }

    /** Adds a row, refusing past the cap rather than growing without bound. */
    public boolean add(NpcDrop drop) {
        if (drop == null || drops.size() >= MAX_DROPS) {
            return false;
        }
        drops.add(drop);
        return true;
    }

    /** Replaces one row, padding with empties when the index is past the end. */
    public void set(int index, NpcDrop drop) {
        if (index < 0 || index >= MAX_DROPS || drop == null) {
            return;
        }
        while (drops.size() <= index) {
            drops.add(NpcDrop.empty());
        }
        drops.set(index, drop);
    }

    public void remove(int index) {
        if (index >= 0 && index < drops.size()) {
            drops.remove(index);
        }
    }

    public void clear() {
        drops.clear();
        lootMode = NpcLootMode.NORMAL;
        minExp = 0;
        maxExp = 0;
    }

    public NpcLootMode lootMode() {
        return lootMode;
    }

    public void setLootMode(NpcLootMode mode) {
        lootMode = mode == null ? NpcLootMode.NORMAL : mode;
    }

    public int minExp() {
        return minExp;
    }

    public int maxExp() {
        return maxExp;
    }

    public void setMinExp(int value) {
        minExp = clampExp(value);
    }

    public void setMaxExp(int value) {
        maxExp = clampExp(value);
    }

    /**
     * How much experience this NPC is worth this time.
     *
     * <p>A range rather than a number, which is what the two fields are for. Max below Min is read
     * as the pair reversed rather than as an empty range, because that is what an operator who
     * lowered Max meant - not "no experience at all".
     */
    public int rollExperience(RandomSource random) {
        int low = Math.min(minExp, maxExp);
        int high = Math.max(minExp, maxExp);
        if (high <= 0) {
            return 0;
        }
        return low >= high ? high : low + random.nextInt(high - low + 1);
    }

    /**
     * The stacks this NPC drops this time.
     *
     * <p>Each row is rolled on its own: a hundred-percent row always lands, a fifty-percent row
     * lands half the time, and a row an operator is still typing lands never.
     */
    public List<ItemStack> roll(RandomSource random, HolderLookup.Provider registries) {
        List<ItemStack> out = new ArrayList<>();
        for (NpcDrop drop : drops) {
            if (!drop.usable()) {
                continue;
            }
            if (drop.chance() < 100.0f && random.nextFloat() * 100.0f >= drop.chance()) {
                continue;
            }
            ItemStack stack = drop.stack(registries);
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        return out;
    }

    /**
     * Which rows would drop, ignoring chance and the registry.
     *
     * <p>For the editor and for tests: {@link #roll} needs a live registry to turn a stored tag back
     * into a stack, and "is this row filled in" is a different question from "did it land".
     */
    public int usableCount() {
        int count = 0;
        for (NpcDrop drop : drops) {
            if (drop.usable()) {
                count++;
            }
        }
        return count;
    }

    public void copyFrom(NpcDropList other) {
        clear();
        if (other == null) {
            return;
        }
        drops.addAll(other.drops);
        lootMode = other.lootMode;
        minExp = other.minExp;
        maxExp = other.maxExp;
    }

    /** Written only when there is something to write, so an ordinary NPC's tag is unchanged. */
    public void saveTo(CompoundTag tag) {
        if (!drops.isEmpty()) {
            ListTag list = new ListTag();
            for (NpcDrop drop : drops) {
                list.add(drop.save());
            }
            tag.put(TAG_DROPS, list);
        }
        if (lootMode != NpcLootMode.NORMAL) {
            tag.putInt(TAG_MODE, lootMode.ordinal());
        }
        if (minExp != 0) {
            tag.putInt(TAG_MIN_EXP, minExp);
        }
        if (maxExp != 0) {
            tag.putInt(TAG_MAX_EXP, maxExp);
        }
    }

    public void loadFrom(CompoundTag tag) {
        clear();
        if (tag == null) {
            return;
        }
        if (tag.contains(TAG_DROPS)) {
            ListTag list = tag.getList(TAG_DROPS, Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(MAX_DROPS, list.size()); i++) {
                drops.add(NpcDrop.load(list.getCompound(i)));
            }
        }
        lootMode = NpcLootMode.byIndex(tag.getInt(TAG_MODE));
        minExp = clampExp(tag.getInt(TAG_MIN_EXP));
        maxExp = clampExp(tag.getInt(TAG_MAX_EXP));
    }

    private static int clampExp(int value) {
        return Math.max(0, Math.min(MAX_EXP, value));
    }
}
