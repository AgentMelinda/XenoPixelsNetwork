package net.bullettrain.xenopixelsmod.features.skilltree;

import com.google.common.collect.Lists;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 1: Skill Tree System
 *
 * Xenoverse-inspired skill tree progression. Pure data layer keyed by player name/id —
 * no third-party scoreboard sync APIs.
 */
public class SkillTreeManager {

    public static final Map<String, SkillTreeData> PLAYER_SKILL_TREE_DATA = new ConcurrentHashMap<>();

    /** Public list of all registered skill nodes (read by mod bootstrap logging). */
    public static final List<SkillNode> ALL_SKILL_NODES = Lists.newArrayList();

    private static volatile boolean initialized = false;

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ALL_SKILL_NODES.clear();
        registerSkillNodes();
        XenoPixelsMod.LOGGER.info(
                ">>> Feature 1 [Skill Tree System] initialized: {} skill nodes loaded",
                ALL_SKILL_NODES.size());
    }

    private static void registerSkillNodes() {
        addBranch("SAIYAN",
                "GiantApeDefeat",
                "FlightMastery_1",
                "FlightMastery_2",
                "FlightMastery_3",
                "KiBlast_Mastery",
                "Transform_SuperSaiyan",
                "Transform_SuperSaiyan2",
                "Transform_GodSaiyan",
                "SuperFlyMastery",
                "KiBlast_MeteorSmash",
                "KiBlast_DivineKamehameha");

        addBranch("SUPER_SAIYAN",
                "SuperSaiyan3_Unlock",
                "SS3_PowerMastery",
                "SS3_DamageBonus",
                "SS3_CounterAttack");

        addBranch("ULTRA_INSTINCT",
                "UltraInstinct_Unlock",
                "UI_Mastered",
                "UI_PowerMode",
                "UI_KiBlastMastery");

        addBranch("VEGETA",
                "Vegeta_Heritage",
                "Vegeta_AngryMode",
                "Vegeta_DarkKi",
                "Vegeta_FinalFlash");

        addBranch("GOKU_BLACK",
                "GokuBlack_Unlock",
                "GokuBlack_HeartOfKi",
                "GokuBlack_KiBlaster",
                "GokuBlack_DragonFist");

        addBranch("TURLES",
                "Turles_Unlock",
                "Turles_PowerMastery",
                "Turles_ScienceSkills",
                "Turles_DevilFist");
    }

    private static void addBranch(String playstyle, String... nodes) {
        ALL_SKILL_NODES.addAll(SkillNode.fromDefinition(
                SkillNode.Definition.builder()
                        .playstyle(playstyle)
                        .nodes(nodes)
                        .build()));
    }

    public static void unlockSkill(String playerId, String skillId) {
        SkillTreeData data = getPlayerData(playerId);
        boolean unlocked = data.unlockSkill(skillId);
        XenoPixelsMod.LOGGER.info(
                "Skill tree: {} unlocked for {}: {}",
                skillId, playerId, unlocked ? "SUCCESS" : "FAILED");
    }

    public static Optional<SkillNode> getSkillNode(String skillId) {
        if (skillId == null) {
            return Optional.empty();
        }
        return ALL_SKILL_NODES.stream()
                .filter(n -> skillId.equals(n.getSkillId())
                        || skillId.equals(SkillNode.extractSkillId(n.getName())))
                .findFirst();
    }

    public static boolean isSkillUnlocked(String playerId, String skillId) {
        SkillTreeData data = getPlayerData(playerId);
        return data.isSkillUnlocked(skillId);
    }

    public static List<String> getUnlockedSkills(String playerId) {
        return new ArrayList<>(getPlayerData(playerId).getUnlockedSkills());
    }

    public static SkillTreeData getPlayerData(String playerId) {
        return PLAYER_SKILL_TREE_DATA.computeIfAbsent(playerId, SkillTreeData::new);
    }

    public static List<SkillNode> getNodesForPlaystyle(String playstyle) {
        return ALL_SKILL_NODES.stream()
                .filter(n -> Objects.equals(n.getPlaystyle(), playstyle))
                .toList();
    }

    public static List<SkillNode> getAccessibleSkills(String playerId) {
        SkillTreeData data = getPlayerData(playerId);
        return ALL_SKILL_NODES.stream()
                .filter(n -> isPrerequisitesMet(data, n))
                .toList();
    }

    private static boolean isPrerequisitesMet(SkillTreeData playerData, SkillNode node) {
        return node.getPrerequisites().stream().allMatch(playerData::isSkillUnlocked);
    }

    public static List<String> getPlaystyles() {
        Set<String> styles = new HashSet<>();
        for (SkillNode node : ALL_SKILL_NODES) {
            if (node.getPlaystyle() != null) {
                styles.add(node.getPlaystyle());
            }
        }
        return new ArrayList<>(styles);
    }

    public static boolean isPlaystyleUnlocked(String playerId, String playstyle) {
        SkillTreeData data = getPlayerData(playerId);
        return ALL_SKILL_NODES.stream()
                .filter(n -> Objects.equals(n.getPlaystyle(), playstyle))
                .anyMatch(n -> data.isSkillUnlocked(n.getSkillId()));
    }

    public static int getMasteryLevel(String playerId, String skillId) {
        SkillTreeData data = getPlayerData(playerId);
        int level = 0;
        for (int i = 1; i <= 3; i++) {
            String masterySkill = SkillNode.getMasteryVersion(skillId, i);
            if (data.isSkillUnlocked(masterySkill)) {
                level = i;
            } else {
                break;
            }
        }
        return level;
    }

    public static SkillTreeData getPlayerDataByUuid(Player player) {
        if (player == null || player.getGameProfile() == null) {
            return null;
        }
        return getPlayerData(player.getGameProfile().getName());
    }

    public static void clearAllData() {
        PLAYER_SKILL_TREE_DATA.clear();
        XenoPixelsMod.LOGGER.info("Skill tree: All player data cleared");
    }

    /**
     * Per-player unlock / mastery state.
     */
    public static final class SkillTreeData {
        private final String playerName;
        private final Set<String> unlockedSkills = ConcurrentHashMap.newKeySet();
        private final Map<String, Integer> masteryLevels = new ConcurrentHashMap<>();

        public SkillTreeData(String playerName) {
            this.playerName = playerName != null ? playerName : "unknown";
            XenoPixelsMod.LOGGER.debug("Skill tree: Created data for player '{}'", this.playerName);

            // Auto-unlock first base node of each playstyle when trees are already registered
            for (String playstyle : getPlaystyles()) {
                List<SkillNode> nodes = getNodesForPlaystyle(playstyle);
                if (nodes.isEmpty()) {
                    continue;
                }
                SkillNode first = nodes.get(0);
                if (first.isBaseUnlockable()) {
                    unlockedSkills.add(first.getSkillId());
                    masteryLevels.put(first.getSkillId(), 1);
                }
            }
        }

        public String getPlayerName() {
            return playerName;
        }

        public boolean unlockSkill(String skillId) {
            if (skillId == null || unlockedSkills.contains(skillId)) {
                return false;
            }

            Optional<SkillNode> node = getSkillNode(skillId);
            if (node.isEmpty()) {
                // Allow free-form mastery ids that are not pre-registered
                unlockedSkills.add(skillId);
                masteryLevels.putIfAbsent(skillId, 1);
                return true;
            }

            if (!isPrerequisitesMet(this, node.get())) {
                XenoPixelsMod.LOGGER.debug("Skill tree: Prerequisites not met for skill {}", skillId);
                return false;
            }

            unlockedSkills.add(skillId);
            masteryLevels.putIfAbsent(skillId, 1);
            return true;
        }

        public Set<String> getUnlockedSkills() {
            return Set.copyOf(unlockedSkills);
        }

        public int getMasteryLevel(String skillId) {
            return masteryLevels.getOrDefault(skillId, 0);
        }

        public boolean isSkillUnlocked(String skillId) {
            return skillId != null && unlockedSkills.contains(skillId);
        }

        public void initializeFromSave() {
            XenoPixelsMod.LOGGER.debug("Skill tree: initializeFromSave for '{}'", playerName);
        }
    }
}
