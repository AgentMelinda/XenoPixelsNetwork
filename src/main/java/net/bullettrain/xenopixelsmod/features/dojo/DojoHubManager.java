package net.bullettrain.xenopixelsmod.features.dojo;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 8: Dojo / Hub Progression
 */
public final class DojoHubManager {

    private DojoHubManager() {
    }

    public enum TrainingMode {
        COMBAT("Combat training", "Practice combat skills"),
        TECHNIQUE("Technique training", "Master specific techniques"),
        ENDURANCE("Endurance training", "Build power and stamina"),
        FORM("Form learning", "Learn new moves and combos");

        private final String displayName;
        private final String description;

        TrainingMode(String name, String description) {
            this.displayName = name;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum LoadoutSlot {
        MAIN_WEAPON("Main Weapon"),
        SECONDARY("Secondary Weapon"),
        ACCESSORY_1("Accessory Slot 1"),
        ACCESSORY_2("Accessory Slot 2"),
        ABILITY_1("Ability Slot 1"),
        ABILITY_2("Ability Slot 2"),
        ABILITY_3("Ability Slot 3");

        private final String displayName;

        LoadoutSlot(String name) {
            this.displayName = name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum QuestType {
        DAILY("Daily Challenge"),
        WEEKLY("Weekly Challenge"),
        SPECIAL("Special Event"),
        BOSS_PREP("Boss Preparation");

        private final String displayName;

        QuestType(String name) {
            this.displayName = name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum UpgradeType {
        POWER_LEVEL("Power Level", "Increase base power"),
        KI_CONTROL("Ki Control", "Improve ki generation and control"),
        DAMAGE_REDUCTION("Damage Reduction", "Reduce incoming damage"),
        MOVEMENT_SPEED("Movement Speed", "Increase movement speed"),
        COMBO_MASTERY("Combo Mastery", "Unlock advanced combos"),
        REGENERATION("Regeneration", "Faster energy regeneration");

        private final String displayName;
        private final String description;

        UpgradeType(String name, String description) {
            this.displayName = name;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    public static final class TrainingSession {
        private final String playerId;
        private final TrainingMode mode;
        private int completedSessions;
        private long totalTrainingTime;
        private final Set<String> unlockedTechniques = ConcurrentHashMap.newKeySet();

        public TrainingSession(String playerId, TrainingMode mode) {
            this.playerId = playerId;
            this.mode = mode;
            this.completedSessions = 0;
            this.totalTrainingTime = 0L;
        }

        public void completeSession() {
            completedSessions++;
            totalTrainingTime += 60L;
            XenoPixelsMod.LOGGER.debug(
                    "Dojo: Completed {} session for '{}' (total: {})",
                    mode.getDisplayName(), playerId, completedSessions);
        }

        public void unlockTechnique(String techniqueId) {
            if (techniqueId != null) {
                unlockedTechniques.add(techniqueId);
            }
        }

        public boolean isTechniqueUnlocked(String techniqueId) {
            return unlockedTechniques.contains(techniqueId);
        }

        public Set<String> getUnlockedTechniques() {
            return new HashSet<>(unlockedTechniques);
        }

        public int getCompletedSessions() {
            return completedSessions;
        }

        public long getTotalTrainingTimeMinutes() {
            return Math.round(totalTrainingTime / 60f);
        }

        public TrainingMode getMode() {
            return mode;
        }

        public String getPlayerId() {
            return playerId;
        }
    }

    public static final class DojoQuest {
        private final String questId;
        private final QuestType type;
        private final String title;
        private final String description;
        private boolean completed;

        public DojoQuest(String questId, QuestType type, String title, String description) {
            this.questId = questId;
            this.type = type;
            this.title = title;
            this.description = description;
            this.completed = false;
        }

        public void complete() {
            this.completed = true;
        }

        public void reset() {
            this.completed = false;
        }

        public boolean isCompleted() {
            return completed;
        }

        public String getQuestId() {
            return questId;
        }

        public QuestType getType() {
            return type;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }
    }

    public static final class CharacterUpgrade {
        private final String playerId;
        private final UpgradeType type;
        private int currentLevel;
        private final int maxLevel;
        private float currentStrength;

        public CharacterUpgrade(String playerId, UpgradeType type) {
            this.playerId = playerId;
            this.type = type;
            this.currentLevel = 1;
            switch (type) {
                case POWER_LEVEL -> {
                    this.maxLevel = 50;
                    this.currentStrength = 100f;
                }
                case KI_CONTROL -> {
                    this.maxLevel = 30;
                    this.currentStrength = 50f;
                }
                case DAMAGE_REDUCTION -> {
                    this.maxLevel = 40;
                    this.currentStrength = 75f;
                }
                default -> {
                    this.maxLevel = 20;
                    this.currentStrength = 20f;
                }
            }
        }

        public void increaseStrength(float amount) {
            this.currentStrength += amount;
            checkLevelUp();
        }

        private void checkLevelUp() {
            int expectedLevel = Math.round(currentStrength / 10f);
            if (expectedLevel > currentLevel) {
                this.currentLevel = Math.min(expectedLevel, maxLevel);
                if (currentLevel >= maxLevel) {
                    currentStrength = maxLevel * 10f;
                }
            }
        }

        public int getCurrentLevel() {
            return currentLevel;
        }

        public int getMaxLevel() {
            return maxLevel;
        }

        public float getCurrentStrength() {
            return currentStrength;
        }

        public UpgradeType getType() {
            return type;
        }

        public String getPlayerId() {
            return playerId;
        }
    }

    private static final Map<String, Map<TrainingMode, TrainingSession>> TRAINING_SESSIONS = new ConcurrentHashMap<>();
    private static final Set<DojoQuest> ALL_QUESTS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Map<UpgradeType, CharacterUpgrade>> CHARACTER_UPGRADES = new ConcurrentHashMap<>();

    public static TrainingSession getOrCreateTrainingSession(String playerId, TrainingMode mode) {
        Map<TrainingMode, TrainingSession> sessions =
                TRAINING_SESSIONS.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        return sessions.computeIfAbsent(mode, d -> new TrainingSession(playerId, mode));
    }

    public static void completeSession(String playerId, TrainingMode mode) {
        TrainingSession session = TRAINING_SESSIONS
                .getOrDefault(playerId, Collections.emptyMap())
                .get(mode);
        if (session != null) {
            session.completeSession();
            checkTechniqueUnlocks(playerId, mode);
        }
    }

    public static void unlockTechnique(String playerId, TrainingMode mode, String techniqueId) {
        TrainingSession session = TRAINING_SESSIONS
                .getOrDefault(playerId, Collections.emptyMap())
                .get(mode);
        if (session != null) {
            session.unlockTechnique(techniqueId);
        }
    }

    private static void checkTechniqueUnlocks(String playerId, TrainingMode mode) {
        TrainingSession session = TRAINING_SESSIONS
                .getOrDefault(playerId, Collections.emptyMap())
                .get(mode);
        if (session == null) {
            return;
        }
        if (session.getCompletedSessions() >= 5) {
            session.unlockTechnique(mode.name().toLowerCase() + "_basic");
        }
        if (session.getCompletedSessions() >= 15) {
            session.unlockTechnique(mode.name().toLowerCase() + "_advanced");
        }
    }

    public static Set<String> getUnlockedTechniques(String playerId, TrainingMode mode) {
        Map<TrainingMode, TrainingSession> sessions = TRAINING_SESSIONS.get(playerId);
        if (sessions == null) {
            return Collections.emptySet();
        }
        TrainingSession session = sessions.get(mode);
        return session != null ? session.getUnlockedTechniques() : Collections.emptySet();
    }

    public static void addQuest(DojoQuest quest) {
        if (quest != null) {
            ALL_QUESTS.add(quest);
        }
    }

    public static void removeQuest(String questId) {
        ALL_QUESTS.removeIf(q -> q.getQuestId().equals(questId));
    }

    public static void completeQuest(String questId) {
        ALL_QUESTS.stream()
                .filter(q -> q.getQuestId().equals(questId))
                .findFirst()
                .ifPresent(DojoQuest::complete);
    }

    public static void resetQuest(String questId) {
        ALL_QUESTS.stream()
                .filter(q -> q.getQuestId().equals(questId) && q.isCompleted())
                .findFirst()
                .ifPresent(DojoQuest::reset);
    }

    public static List<DojoQuest> getAllActiveQuests() {
        return new ArrayList<>(ALL_QUESTS);
    }

    public static CharacterUpgrade getOrCreateUpgrade(String playerId, UpgradeType type) {
        Map<UpgradeType, CharacterUpgrade> upgrades =
                CHARACTER_UPGRADES.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        return upgrades.computeIfAbsent(type, d -> new CharacterUpgrade(playerId, type));
    }

    public static void increaseUpgradeStrength(String playerId, UpgradeType type, float amount) {
        CharacterUpgrade upgrade = getOrCreateUpgrade(playerId, type);
        upgrade.increaseStrength(amount);
    }

    public static List<DojoQuest> getActiveQuestsByType(QuestType type) {
        return ALL_QUESTS.stream()
                .filter(q -> q.getType() == type)
                .toList();
    }

    public static Map<TrainingMode, TrainingSession> getAllSessions(String playerId) {
        return TRAINING_SESSIONS.getOrDefault(playerId, Collections.emptyMap());
    }

    public static void clearAllData() {
        TRAINING_SESSIONS.clear();
        ALL_QUESTS.clear();
        XenoPixelsMod.LOGGER.info("Dojo: Sessions and quests cleared");
    }
}
