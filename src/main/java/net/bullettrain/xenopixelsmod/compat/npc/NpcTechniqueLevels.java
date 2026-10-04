package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.stats.techniques.KiAttackData;
import com.dragonminez.common.stats.techniques.StrikeAttackData;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Per-NPC DragonMineZ technique damage and cooldown upgrade levels. */
public final class NpcTechniqueLevels {
    /** DMZ's damage and cooldown formulas reach their effective cap at level 20. */
    public static final int MAX_EFFECTIVE_LEVEL = 20;

    private final Map<String, Levels> values = new LinkedHashMap<>();

    public record Levels(int damage, int cooldown) {
        public Levels {
            damage = clamp(damage);
            cooldown = clamp(cooldown);
        }
    }

    public static String canonical(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    public int damageLevel(String id) {
        Levels levels = values.get(canonical(id));
        return levels == null ? 0 : levels.damage();
    }

    public int cooldownLevel(String id) {
        Levels levels = values.get(canonical(id));
        return levels == null ? 0 : levels.cooldown();
    }

    public void set(String id, int damage, int cooldown) {
        String key = canonical(id);
        if (key.isEmpty()) {
            return;
        }
        Levels levels = new Levels(damage, cooldown);
        if (levels.damage() == 0 && levels.cooldown() == 0) {
            values.remove(key);
        } else {
            values.put(key, levels);
        }
    }

    public void setDamage(String id, int level) {
        set(id, level, cooldownLevel(id));
    }

    public void setCooldown(String id, int level) {
        set(id, damageLevel(id), level);
    }

    public void clear() {
        values.clear();
    }

    public Map<String, Levels> values() {
        return Map.copyOf(values);
    }

    public void apply(String id, KiAttackData data) {
        if (data != null) {
            data.setDamageLevel(damageLevel(id));
            data.setCooldownLevel(cooldownLevel(id));
        }
    }

    public void apply(String id, StrikeAttackData data) {
        if (data != null) {
            data.setDamageLevel(damageLevel(id));
            data.setCooldownLevel(cooldownLevel(id));
        }
    }

    public CompoundTag save() {
        CompoundTag result = new CompoundTag();
        values.forEach((id, levels) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Damage", levels.damage());
            entry.putInt("Cooldown", levels.cooldown());
            result.put(id, entry);
        });
        return result;
    }

    public void load(CompoundTag tag) {
        values.clear();
        if (tag == null) {
            return;
        }
        for (String rawId : tag.getAllKeys()) {
            String id = canonical(rawId);
            if (id.isEmpty() || !tag.contains(rawId, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag entry = tag.getCompound(rawId);
            set(id, entry.getInt("Damage"), entry.getInt("Cooldown"));
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_EFFECTIVE_LEVEL, value));
    }
}
