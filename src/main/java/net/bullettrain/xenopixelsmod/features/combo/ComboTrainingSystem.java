package net.bullettrain.xenopixelsmod.features.combo;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 3: Combo Training System
 */
public class ComboTrainingSystem {

    public enum DifficultyTier {
        BEGINNER(1, "Beginner", 0.5f),
        INTERMEDIATE(2, "Intermediate", 0.65f),
        ADVANCED(3, "Advanced", 0.8f),
        EXPERT(4, "Expert", 0.9f),
        MASTER(5, "Master", 1.0f);

        private final int level;
        private final String displayName;
        private final float accuracyRequired;

        DifficultyTier(int level, String displayName, float accuracyRequired) {
            this.level = level;
            this.displayName = displayName;
            this.accuracyRequired = accuracyRequired;
        }

        public int getLevel() {
            return level;
        }

        public String getDisplayName() {
            return displayName;
        }

        public float getAccuracyRequired() {
            return accuracyRequired;
        }
    }

    public enum ComboFrame {
        SETUP("setup"),
        COMBO("combo"),
        FINISH("finish"),
        COUNTER("counter"),
        DEFEND("defend");

        private final String displayName;

        ComboFrame(String name) {
            this.displayName = name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static final class TrainingSession {
        private final String playerId;
        private final DifficultyTier currentDifficulty;
        private int combosCompleted;
        private long totalComboTime;
        private final Set<ComboExecutionRecord> executionRecords = ConcurrentHashMap.newKeySet();

        public TrainingSession(String playerId, DifficultyTier difficulty) {
            this.playerId = playerId;
            this.currentDifficulty = difficulty != null ? difficulty : DifficultyTier.BEGINNER;
            this.combosCompleted = 0;
            this.totalComboTime = 0L;
            XenoPixelsMod.LOGGER.debug(
                    "Combo training: Started session for player '{}' at {} difficulty",
                    playerId, this.currentDifficulty);
        }

        public void recordExecution(String comboId, ComboFrame frameType, long startTimeMs) {
            executionRecords.add(new ComboExecutionRecord(comboId, frameType, startTimeMs));
        }

        public boolean isWithinTimingWindow(String comboId, long currentTimestampMs) {
            return true;
        }

        public double calculateMasteryPercentage() {
            if (executionRecords.isEmpty()) {
                return 0.0;
            }
            return Math.min(1.0, combosCompleted / 50.0);
        }

        public List<MasteryReward> getUnlockedRewards() {
            List<MasteryReward> rewards = new ArrayList<>();
            if (combosCompleted >= 10) {
                rewards.add(MasteryReward.create("combo_basic", "Basic Combo Mastery"));
            }
            if (combosCompleted >= 50) {
                rewards.add(MasteryReward.create("combo_advanced", "Advanced Combo Mastery"));
            }
            if (combosCompleted >= 100) {
                rewards.add(MasteryReward.create("combo_expert", "Expert Combo Mastery"));
            }
            if (combosCompleted >= 250) {
                rewards.add(MasteryReward.create("combo_master", "Master Combo Mastery"));
            }

            switch (currentDifficulty) {
                case BEGINNER -> {
                    if (combosCompleted >= 10) {
                        rewards.add(MasteryReward.create("reward_beginner_1", "Beginner Tier Reward 1"));
                    }
                }
                case INTERMEDIATE -> {
                    if (combosCompleted >= 20) {
                        rewards.add(MasteryReward.create("reward_intermediate_1", "Intermediate Tier Reward 1"));
                    }
                }
                case ADVANCED -> {
                    if (combosCompleted >= 30) {
                        rewards.add(MasteryReward.create("reward_advanced_1", "Advanced Tier Reward 1"));
                    }
                }
                case EXPERT -> {
                    if (combosCompleted >= 40) {
                        rewards.add(MasteryReward.create("reward_expert_1", "Expert Tier Reward 1"));
                    }
                }
                case MASTER -> {
                    if (combosCompleted >= 50) {
                        rewards.add(MasteryReward.create("reward_master_1", "Master Tier Reward 1"));
                    }
                }
            }
            return rewards;
        }

        public int getCombosCompleted() {
            return combosCompleted;
        }

        public long getTotalComboTime() {
            return totalComboTime;
        }

        public DifficultyTier getCurrentDifficulty() {
            return currentDifficulty;
        }

        public String getPlayerId() {
            return playerId;
        }

        public void incrementCombosCompleted() {
            combosCompleted++;
            totalComboTime += 1000L;
        }
    }

    public static final class ComboExecutionRecord {
        private final String comboId;
        private final ComboFrame frameType;
        private final long startTimeMs;

        public ComboExecutionRecord(String comboId, ComboFrame frameType, long startTimeMs) {
            this.comboId = comboId;
            this.frameType = frameType;
            this.startTimeMs = startTimeMs;
        }

        public String getComboId() {
            return comboId;
        }

        public ComboFrame getFrameType() {
            return frameType;
        }

        public long getStartTimeMs() {
            return startTimeMs;
        }
    }

    public static final class MasteryReward {
        private final String rewardId;
        private final String displayName;

        private MasteryReward(String rewardId, String displayName) {
            this.rewardId = rewardId;
            this.displayName = displayName;
        }

        public static MasteryReward create(String id, String name) {
            return new MasteryReward(id, name);
        }

        public String getRewardId() {
            return rewardId;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final Map<String, Map<DifficultyTier, TrainingSession>> SESSIONS = new ConcurrentHashMap<>();

    public static TrainingSession getSession(String playerId, DifficultyTier difficulty) {
        Map<DifficultyTier, TrainingSession> sessions =
                SESSIONS.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        return sessions.computeIfAbsent(difficulty, d -> new TrainingSession(playerId, difficulty));
    }

    public static Map<DifficultyTier, TrainingSession> getAllSessions(String playerId) {
        return SESSIONS.getOrDefault(playerId, Map.of());
    }

    public static TrainingSession startTraining(String playerId, DifficultyTier difficulty) {
        return getSession(playerId, difficulty);
    }

    public static void recordComboExecution(String playerId, String comboId, ComboFrame frameType) {
        TrainingSession session = getSession(playerId, DifficultyTier.BEGINNER);
        session.recordExecution(comboId, frameType, System.currentTimeMillis());
        session.incrementCombosCompleted();
    }

    public static List<MasteryReward> getUnlockedRewards(String playerId, DifficultyTier difficulty) {
        return getSession(playerId, difficulty).getUnlockedRewards();
    }

    public static Optional<MasteryReward> getHighestUnlockedReward(String playerId, DifficultyTier difficulty) {
        List<MasteryReward> rewards = getUnlockedRewards(playerId, difficulty);
        return rewards.stream().max(Comparator.comparing(MasteryReward::getRewardId));
    }

    public static void clearAllSessions() {
        SESSIONS.clear();
        XenoPixelsMod.LOGGER.info("Combo training: All sessions cleared");
    }
}
