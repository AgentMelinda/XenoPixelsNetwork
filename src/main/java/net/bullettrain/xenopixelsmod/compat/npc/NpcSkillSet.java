package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.SkillsConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which DragonMineZ skills an NPC has, and at what level.
 *
 * <p>DMZ stores skills as a flat {@code Map<String, Skill>} keyed by lower-case name
 * ({@code com.dragonminez.common.stats.skills.Skills}); the set of valid ids is data, not code —
 * {@code data/dragonminez/previousConfigs/skills.json} in the DMZ jar defines them, and
 * {@link SkillsConfig#getSkillCosts(String)} gives each one's cost table, whose size is the max
 * level ({@code Skills.calculateMaxLevel} uses exactly that). So this class never hard-codes a
 * skill list; it reads DMZ's own config.
 *
 * <p>Only skills an author actually configured are stored, so an NPC's NBT does not carry 45
 * entries for the two it uses.
 */
public final class NpcSkillSet {
    /** The one id this mod references directly, because flight also drives NPC navigation. */
    public static final String FLY = "fly";

    private static final String TAG_ID = "Id";
    private static final String TAG_ACTIVE = "On";
    private static final String TAG_LEVEL = "Level";

    /** A configured skill: whether it is switched on, and the level it sits at. */
    public record Entry(boolean active, int level) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /** Canonical form of a skill id, matching how DMZ keys its own map. */
    public static String canonical(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Max level DMZ allows for a skill, taken from the length of its cost table — the same
     * source {@code Skills.calculateMaxLevel} uses.
     *
     * <p>Returns {@code 1} when the config is unavailable, which still lets the skill be
     * switched on. That fallback is a display convenience only: anything that clamps a level
     * must call {@link #configuredMaxLevel} instead, because clamping against this value would
     * rewrite every level above 1 to 1 for any id DMZ ships no cost table for.
     */
    public static int maxLevelOf(String id) {
        int configured = configuredMaxLevel(id);
        return configured > 0 ? configured : 1;
    }

    /**
     * Max level DMZ's cost table actually declares, or {@code 0} when it declares none.
     *
     * <p>The zero is the whole point: a missing cost table means the real maximum is unknown,
     * not that it is one. {@link #maxLevelOf} folds that case into 1 for display, so callers
     * that would otherwise discard an author's edit must ask this method and skip the clamp.
     */
    public static int configuredMaxLevel(String id) {
        String key = canonical(id);
        if (key.isEmpty()) {
            return 0;
        }
        try {
            SkillsConfig config = ConfigManager.getSkillsConfig();
            if (config != null) {
                SkillsConfig.SkillCosts costs = config.getSkillCosts(key);
                if (costs != null && costs.getCosts() != null && !costs.getCosts().isEmpty()) {
                    return costs.getCosts().size();
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /** Every skill id DMZ's config knows about, for script listings and the editor GUI. */
    public static List<String> knownIds() {
        try {
            SkillsConfig config = ConfigManager.getSkillsConfig();
            if (config != null && config.getSkills() != null) {
                List<String> ids = new ArrayList<>(config.getSkills().keySet());
                ids.sort(String::compareTo);
                return List.copyOf(ids);
            }
        } catch (Throwable ignored) {
        }
        return List.of();
    }

    /** True when DMZ's config recognises this id. */
    public static boolean isKnown(String id) {
        return knownIds().contains(canonical(id));
    }

    public boolean isActive(String id) {
        Entry entry = entries.get(canonical(id));
        return entry != null && entry.active();
    }

    public int level(String id) {
        Entry entry = entries.get(canonical(id));
        return entry == null ? 0 : entry.level();
    }

    public boolean has(String id) {
        return entries.containsKey(canonical(id));
    }

    /** Sets both the active flag and the level, clamping the level to DMZ's own maximum. */
    public void set(String id, boolean active, int level) {
        String key = canonical(id);
        if (key.isEmpty()) {
            return;
        }
        entries.put(key, new Entry(active, clampLevel(key, level)));
    }

    /** Toggles a skill, keeping whatever level it already had (minimum 1 once configured). */
    public void setActive(String id, boolean active) {
        String key = canonical(id);
        if (key.isEmpty()) {
            return;
        }
        Entry existing = entries.get(key);
        int level = existing == null ? 1 : Math.max(1, existing.level());
        entries.put(key, new Entry(active, clampLevel(key, level)));
    }

    /** Sets the level, keeping whatever active state it already had. */
    public void setLevel(String id, int level) {
        String key = canonical(id);
        if (key.isEmpty()) {
            return;
        }
        Entry existing = entries.get(key);
        entries.put(key, new Entry(existing != null && existing.active(), clampLevel(key, level)));
    }

    public void remove(String id) {
        entries.remove(canonical(id));
    }

    public void clear() {
        entries.clear();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Live view of the configured skills, in insertion order. */
    public Map<String, Entry> entries() {
        return java.util.Collections.unmodifiableMap(entries);
    }

    public void copyFrom(NpcSkillSet other) {
        entries.clear();
        if (other != null) {
            entries.putAll(other.entries);
        }
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Map.Entry<String, Entry> entry : entries.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_ID, entry.getKey());
            tag.putBoolean(TAG_ACTIVE, entry.getValue().active());
            tag.putInt(TAG_LEVEL, entry.getValue().level());
            list.add(tag);
        }
        return list;
    }

    public void load(ListTag list) {
        entries.clear();
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            String key = canonical(tag.getString(TAG_ID));
            if (key.isEmpty()) {
                continue;
            }
            entries.put(key, new Entry(tag.getBoolean(TAG_ACTIVE),
                    clampLevel(key, tag.getInt(TAG_LEVEL))));
        }
    }

    private static int clampLevel(String key, int level) {
        int configured = configuredMaxLevel(key);
        // No cost table means the maximum is unknown, so the requested level is kept rather than
        // collapsed to 1. Clamping against maxLevelOf's display fallback here was what silently
        // threw away every level an author typed for an id DMZ ships no costs for.
        if (configured <= 0) {
            return Math.max(1, level);
        }
        return Math.max(1, Math.min(configured, level));
    }
}
