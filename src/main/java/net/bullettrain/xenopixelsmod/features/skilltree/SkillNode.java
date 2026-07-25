package net.bullettrain.xenopixelsmod.features.skilltree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Skill node definition for the skill tree system.
 */
public final class SkillNode {

    private final String playstyle;
    private final String skillId;
    private final String displayName;
    private final int tier;
    private final String category;
    private final Set<String> prerequisites;
    private final boolean baseUnlockable;
    private final String description;
    private final int zeniCost;
    private final long xpRequired;

    private SkillNode(Builder builder) {
        this.playstyle = Objects.requireNonNull(builder.playstyle, "playstyle");
        this.skillId = Objects.requireNonNull(builder.skillId, "skillId");
        this.displayName = builder.displayName != null ? builder.displayName : builder.skillId;
        this.tier = Math.max(1, builder.tier);
        this.category = builder.category != null ? builder.category : "COMBO";
        this.prerequisites = new LinkedHashSet<>(builder.prerequisites);
        this.baseUnlockable = builder.baseUnlockable;
        this.description = builder.description != null ? builder.description : "";
        this.zeniCost = Math.max(0, builder.zeniCost);
        this.xpRequired = Math.max(0L, builder.xpRequired);
    }

    public static Builder builder(String playstyle, String skillId) {
        return new Builder(playstyle, skillId);
    }

    /**
     * Expand a playstyle definition into one node per skill id string.
     */
    public static List<SkillNode> fromDefinition(Definition definition) {
        if (definition == null) {
            return List.of();
        }
        List<SkillNode> nodes = new ArrayList<>();
        List<String> ids = definition.getNodes();
        for (int i = 0; i < ids.size(); i++) {
            String skillId = ids.get(i);
            String category = guessCategory(skillId);
            boolean base = i == 0 || skillId.contains("Unlock") || skillId.contains("Base")
                    || skillId.contains("Defeat");
            nodes.add(builder(definition.getPlaystyle(), skillId)
                    .displayName(skillId.replace('_', ' '))
                    .tier(guessTier(skillId, i))
                    .category(category)
                    .baseUnlockable(base)
                    .xpRequired((long) (i + 1) * 50_000L)
                    .build());
        }
        // Chain prerequisites: each node after the first requires the previous id
        for (int i = 1; i < nodes.size(); i++) {
            nodes.get(i).prerequisites.add(nodes.get(i - 1).getSkillId());
        }
        return nodes;
    }

    private static int guessTier(String skillId, int index) {
        Optional<Integer> mastery = getMasteryLevelFromId(skillId);
        if (mastery.isPresent()) {
            return mastery.get();
        }
        return Math.min(5, index + 1);
    }

    private static String guessCategory(String skillId) {
        String s = skillId.toLowerCase();
        if (s.contains("flight") || s.contains("fly")) {
            return "FLIGHT";
        }
        if (s.contains("kiblast") || s.contains("kamehameha") || s.contains("beam")) {
            return "KI_BLAST";
        }
        if (s.contains("transform") || s.contains("saiyan") || s.contains("instinct")) {
            return "TRANSFORM";
        }
        if (s.contains("finisher") || s.contains("flash") || s.contains("fist")) {
            return "FINISHER";
        }
        if (s.contains("counter")) {
            return "COMBAT";
        }
        if (s.contains("unlock") || s.contains("base") || s.contains("defeat")) {
            return "UNLOCKABLE";
        }
        if (s.contains("mastery")) {
            return "MASTERY";
        }
        return "COMBO";
    }

    public static String extractSkillId(String nodeName) {
        if (nodeName == null || nodeName.isEmpty()) {
            return null;
        }
        return nodeName.replace(' ', '_');
    }

    public static String getMasteryVersion(String skillId, int level) {
        String base = getBaseVersion(skillId);
        return base + "_Mastery_" + level;
    }

    public static String getBaseVersion(String skillId) {
        if (skillId == null || skillId.isEmpty()) {
            return null;
        }
        return skillId.replaceAll("_Mastery_\\d+$", "");
    }

    public static boolean isMasteryVersion(String skillId, String baseSkillId) {
        if (skillId == null || baseSkillId == null) {
            return false;
        }
        return skillId.contains("Mastery") && getBaseVersion(skillId).equals(getBaseVersion(baseSkillId));
    }

    public static Optional<Integer> getMasteryLevelFromId(String skillId) {
        if (skillId == null || !skillId.contains("Mastery_")) {
            return Optional.empty();
        }
        try {
            String[] parts = skillId.split("_");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("Mastery".equals(parts[i])) {
                    return Optional.of(Integer.parseInt(parts[i + 1]));
                }
            }
        } catch (NumberFormatException ignored) {
            // invalid format
        }
        return Optional.empty();
    }

    public String getPlaystyle() {
        return playstyle;
    }

    public String getSkillId() {
        return skillId;
    }

    public String getName() {
        return displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getTier() {
        return tier;
    }

    public String getCategory() {
        return category;
    }

    public Set<String> getPrerequisites() {
        return Collections.unmodifiableSet(prerequisites);
    }

    public boolean isBaseUnlockable() {
        return baseUnlockable;
    }

    public String getDescription() {
        return description;
    }

    public int getZeniCost() {
        return zeniCost;
    }

    public long getXpRequired() {
        return xpRequired;
    }

    public SkillNode addPrerequisite(String prerequisiteId) {
        if (prerequisiteId != null && !prerequisiteId.isEmpty()) {
            prerequisites.add(prerequisiteId);
        }
        return this;
    }

    @Override
    public String toString() {
        return "SkillNode{playstyle='" + playstyle + "', skillId='" + skillId + "', tier=" + tier + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillNode other)) {
            return false;
        }
        return skillId.equals(other.skillId);
    }

    @Override
    public int hashCode() {
        return skillId.hashCode();
    }

    public static final class Builder {
        private final String playstyle;
        private final String skillId;
        private String displayName;
        private int tier = 1;
        private String category = "COMBO";
        private final Set<String> prerequisites = new LinkedHashSet<>();
        private boolean baseUnlockable;
        private String description = "";
        private int zeniCost;
        private long xpRequired;

        private Builder(String playstyle, String skillId) {
            this.playstyle = playstyle;
            this.skillId = skillId;
        }

        public Builder displayName(String name) {
            this.displayName = name;
            return this;
        }

        public Builder tier(int tier) {
            this.tier = tier;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder baseUnlockable(boolean base) {
            this.baseUnlockable = base;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder zeniCost(int cost) {
            this.zeniCost = cost;
            return this;
        }

        public Builder xpRequired(long xp) {
            this.xpRequired = xp;
            return this;
        }

        public Builder prerequisite(String skillId) {
            if (skillId != null) {
                this.prerequisites.add(skillId);
            }
            return this;
        }

        public SkillNode build() {
            return new SkillNode(this);
        }
    }

    /**
     * Playstyle branch definition used during registration.
     */
    public static final class Definition {
        private final String playstyle;
        private final List<String> nodes;

        public Definition(String playstyle, List<String> nodes) {
            this.playstyle = Objects.requireNonNull(playstyle, "playstyle");
            this.nodes = new ArrayList<>(Objects.requireNonNull(nodes, "nodes"));
        }

        public static DefinitionBuilder builder() {
            return new DefinitionBuilder();
        }

        public String getPlaystyle() {
            return playstyle;
        }

        public List<String> getNodes() {
            return Collections.unmodifiableList(nodes);
        }
    }

    public static final class DefinitionBuilder {
        private String playstyle;
        private final List<String> nodes = new ArrayList<>();

        public DefinitionBuilder playstyle(String playstyle) {
            this.playstyle = playstyle;
            return this;
        }

        public DefinitionBuilder nodes(String... nodeIds) {
            if (nodeIds != null) {
                Collections.addAll(this.nodes, nodeIds);
            }
            return this;
        }

        public Definition build() {
            return new Definition(playstyle, nodes);
        }
    }
}
