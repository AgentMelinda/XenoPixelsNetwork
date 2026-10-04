package net.bullettrain.xenopixelsmod.npc.spawn;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcClones;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One natural spawn rule: a saved clone that the world may place on its own, in certain biomes, at
 * a certain relative weight, and optionally only by day or only by night.
 *
 * <p>My NPCs keeps these in one gzipped {@code spawns.dat}. Here each rule is one entry of the
 * {@code spawns/} store category, so a bad rule fails alone instead of taking every rule with it —
 * the reason the whole store is one-file-per-entry.
 *
 * <p>A rule never carries an NPC's identity. It carries a <em>reference</em> into the clone library
 * ({@code cloneTab} + {@code cloneId}), which is what {@code docs/xeno-npc-schema.md} requires of
 * shared content: an id, never a copy. Copying a template's payload into the rule would give two
 * writers of one NPC and no rule for which wins — the mistake transports made. Change the saved
 * clone and every rule pointing at it changes with it.
 *
 * <p>Nothing here touches a level or a server, which is what makes the matching and weighting
 * decisions testable headlessly.
 */
public record NpcNaturalSpawn(String id, String title, List<String> biomes, int weight,
                              int cloneTab, String cloneId, String time) {

    public static final int MAX_BIOMES = 32;
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 1000;
    public static final int DEFAULT_WEIGHT = 10;

    /** The values the Type cycle offers, as stored. */
    public static final String TIME_ANY = "any";
    public static final String TIME_DAY = "day";
    public static final String TIME_NIGHT = "night";
    public static final List<String> TIMES = List.of(TIME_ANY, TIME_DAY, TIME_NIGHT);

    private static final int MAX_ID = XenoNpcStorePaths.MAX_ID;
    private static final int MAX_TITLE = 128;
    private static final int MAX_BIOME = 128;

    public NpcNaturalSpawn {
        id = clean(id, MAX_ID);
        title = clean(title, MAX_TITLE);
        if (title.isBlank()) {
            title = id;
        }
        biomes = clampBiomes(biomes);
        weight = Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
        cloneTab = XenoNpcClones.clampTab(cloneTab);
        cloneId = clean(cloneId, MAX_ID);
        time = normaliseTime(time);
    }

    /** A draft with nothing in it yet, for the editor's "New". */
    public static NpcNaturalSpawn blank(String id) {
        return new NpcNaturalSpawn(id, "", List.of(), DEFAULT_WEIGHT, XenoNpcClones.MIN_TAB,
                "", TIME_ANY);
    }

    /** Reads a stored rule. Absent or malformed members fall back rather than dropping the entry. */
    public static NpcNaturalSpawn load(String id, CompoundTag tag) {
        CompoundTag safe = tag == null ? new CompoundTag() : tag;
        List<String> biomes = readBiomes(safe.getList("Biomes", Tag.TAG_STRING));
        return new NpcNaturalSpawn(id,
                safe.getString("Name"),
                biomes,
                safe.contains("Weight") ? safe.getInt("Weight") : DEFAULT_WEIGHT,
                safe.contains("CloneTab") ? safe.getInt("CloneTab") : XenoNpcClones.MIN_TAB,
                safe.getString("CloneId"),
                safe.getString("Time"));
    }

    /** The stored form. Writes every member, so an explicitly lowered value survives a round trip. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", title);
        ListTag list = new ListTag();
        for (String biome : biomes) {
            list.add(StringTag.valueOf(biome));
        }
        tag.put("Biomes", list);
        tag.putInt("Weight", weight);
        tag.putInt("CloneTab", cloneTab);
        tag.putString("CloneId", cloneId);
        tag.putString("Time", time);
        return tag;
    }

    /**
     * Whether a payload may be written at all, or why it may not.
     *
     * <p>Stricter than {@link #load}: loading must be forgiving, because a file already on disk is
     * not something a player can fix from a chat message. A write is, so a rule that could never
     * place an NPC is refused with a reason rather than stored and silently ignored forever.
     */
    public static String rejectPayload(CompoundTag payload) {
        if (payload == null) {
            return "spawn rule has no payload";
        }
        String cloneId = payload.getString("CloneId");
        if (cloneId.isBlank()) {
            return "spawn rule needs a saved clone; pick one with \"Select NPC\"";
        }
        String refusal = XenoNpcStorePaths.reject(cloneId);
        if (refusal != null) {
            return "spawn rule: " + refusal;
        }
        int tab = payload.contains("CloneTab") ? payload.getInt("CloneTab") : XenoNpcClones.MIN_TAB;
        if (tab < XenoNpcClones.MIN_TAB || tab > XenoNpcClones.MAX_TAB) {
            return "spawn rule: clone tab must be " + XenoNpcClones.MIN_TAB + "-"
                    + XenoNpcClones.MAX_TAB;
        }
        int weight = payload.contains("Weight") ? payload.getInt("Weight") : DEFAULT_WEIGHT;
        if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
            return "spawn rule: weight must be " + MIN_WEIGHT + "-" + MAX_WEIGHT;
        }
        String time = payload.getString("Time");
        if (!time.isEmpty() && !normaliseTime(time).equals(time.toLowerCase(Locale.ROOT))) {
            return "spawn rule: time must be any, day or night";
        }
        ListTag biomes = payload.getList("Biomes", Tag.TAG_STRING);
        if (biomes.size() > MAX_BIOMES) {
            return "spawn rule: at most " + MAX_BIOMES + " biomes";
        }
        for (int i = 0; i < biomes.size(); i++) {
            String biome = biomes.getString(i);
            if (biome.isBlank() || biome.indexOf(':') < 0) {
                return "spawn rule: biome " + (i + 1) + " needs a namespace, like minecraft:plains";
            }
        }
        return null;
    }

    /** Whether this rule's biome list admits the given registered biome name. */
    public boolean matchesBiome(String biomeId) {
        // An empty list is "anywhere", not "nowhere": a rule whose author never got round to
        // picking biomes should still be usable, and never spawning is the worse default.
        if (biomes.isEmpty()) {
            return true;
        }
        return biomeId != null && biomes.contains(biomeId.toLowerCase(Locale.ROOT));
    }

    /** Whether this rule's time restriction admits the current sky. */
    public boolean matchesTime(boolean day) {
        return switch (time) {
            case TIME_DAY -> day;
            case TIME_NIGHT -> !day;
            default -> true;
        };
    }

    /** Whether the rule points at something that could be placed. */
    public boolean usable() {
        return !cloneId.isBlank();
    }

    /** One line for a row or a log: what it places, where, and how often. */
    public String describe() {
        return title + " -> clone " + cloneTab + "/" + cloneId + " weight " + weight + " " + time
                + (biomes.isEmpty() ? " any biome" : " " + biomes.size() + " biome(s)");
    }

    /** Sum of the weights of a set of rules, or 0 when there is nothing to choose between. */
    public static int totalWeight(List<NpcNaturalSpawn> rules) {
        int total = 0;
        for (NpcNaturalSpawn rule : rules == null ? List.<NpcNaturalSpawn>of() : rules) {
            total += rule.weight();
        }
        return total;
    }

    /**
     * The weighted pick, for a roll already reduced to {@code 0 <= roll < totalWeight}.
     *
     * <p>The roll is passed in rather than drawn here so selection stays a pure function: a test can
     * prove <em>which</em> rule a given roll lands on, not only that some rule did.
     */
    public static NpcNaturalSpawn pick(List<NpcNaturalSpawn> rules, int roll) {
        List<NpcNaturalSpawn> list = rules == null ? List.of() : rules;
        int total = totalWeight(list);
        if (total <= 0) {
            return null;
        }
        int target = Math.max(0, Math.min(total - 1, roll));
        int running = 0;
        for (NpcNaturalSpawn rule : list) {
            running += rule.weight();
            if (target < running) {
                return rule;
            }
        }
        return list.get(list.size() - 1);
    }

    /** The rules admitting this biome and sky, in store order, unusable ones dropped. */
    public static List<NpcNaturalSpawn> eligible(List<NpcNaturalSpawn> rules, String biomeId,
                                                 boolean day) {
        List<NpcNaturalSpawn> out = new ArrayList<>();
        for (NpcNaturalSpawn rule : rules == null ? List.<NpcNaturalSpawn>of() : rules) {
            if (rule.usable() && rule.matchesBiome(biomeId) && rule.matchesTime(day)) {
                out.add(rule);
            }
        }
        return List.copyOf(out);
    }

    private static String normaliseTime(String raw) {
        String value = clean(raw, 16).toLowerCase(Locale.ROOT);
        return TIMES.contains(value) ? value : TIME_ANY;
    }

    private static List<String> readBiomes(ListTag list) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String value = list.getString(i).trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private static List<String> clampBiomes(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String biome : raw == null ? List.<String>of() : raw) {
            if (out.size() >= MAX_BIOMES) {
                break;
            }
            String value = clean(biome, MAX_BIOME).toLowerCase(Locale.ROOT);
            if (!value.isEmpty() && !out.contains(value)) {
                out.add(value);
            }
        }
        return List.copyOf(out);
    }

    private static String clean(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
