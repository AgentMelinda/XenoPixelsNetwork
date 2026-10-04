package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Key-level differences between two profile tags, so an editor save applies only what the editor
 * changed. DMZ screens edit a client copy that the server never fully synced; replacing the whole
 * server profile with it reverted script and command edits. Top-level keys are the unit: a key
 * whose value differs, appears, or disappears between baseline and edited counts as changed.
 */
public final class NpcProfileDiff {
    private NpcProfileDiff() {}

    /** Keys added, removed or changed from {@code baseline} to {@code edited}. */
    public static Set<String> changedKeys(CompoundTag baseline, CompoundTag edited) {
        Set<String> keys = new LinkedHashSet<>();
        for (String key : edited.getAllKeys()) {
            if (!Objects.equals(baseline.get(key), edited.get(key))) keys.add(key);
        }
        for (String key : baseline.getAllKeys()) {
            if (!edited.contains(key)) keys.add(key);
        }
        return keys;
    }

    /**
     * {@code current} with the editor's changes applied: every key that differs between
     * {@code baseline} and {@code edited} takes the edited value (or is removed); every other key
     * keeps the current server value. No input is mutated.
     */
    public static CompoundTag merge(CompoundTag current, CompoundTag baseline, CompoundTag edited) {
        CompoundTag merged = current.copy();
        for (String key : changedKeys(baseline, edited)) {
            if (edited.contains(key)) merged.put(key, edited.get(key).copy());
            else merged.remove(key);
        }
        return merged;
    }
}
