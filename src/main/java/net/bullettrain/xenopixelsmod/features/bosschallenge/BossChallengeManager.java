package net.bullettrain.xenopixelsmod.features.bosschallenge;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 6: Boss Challenge Mode
 */
public final class BossChallengeManager {

    private BossChallengeManager() {
    }

    public enum ChallengeState {
        IDLE("Boss is idle"),
        ENGAGED("Boss is engaged in combat"),
        WEAK_POINT_REVEALED("Weak point is revealed"),
        FINISHING_MOVEMENT("Boss is charging finishing move"),
        DEFEATED("Boss has been defeated");

        private final String description;

        ChallengeState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum WeakPointType {
        ARM("Arm/Hand area"),
        CHEST("Chest/Cloak area"),
        CORE("Core/Energy source"),
        WEAPON("Weapon/Blade"),
        EYES("Eyes/Vision sensors"),
        BACK("Back vulnerability"),
        LEGS("Legs/Knees");

        private final String description;

        WeakPointType(String name) {
            this.description = name;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum AchievementType {
        FIRST_DEFEAT("First time defeating boss"),
        PERFECT_FORMATION("Defeated using optimal formation"),
        WEAK_POINT_MASTER("Revealed all weak points for boss"),
        SPEEDRUN("Defeated boss under 30 seconds"),
        COMBO_FINISHER("Finished boss with special combo finisher");

        private final String description;

        AchievementType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static final class WeakPoint {
        private final String bossId;
        private final String weakPointId;
        private final int healthPercent;
        private final WeakPointType type;
        private boolean revealed;

        public WeakPoint(String bossId, String weakPointId, int healthPercent, WeakPointType type) {
            this.bossId = bossId;
            this.weakPointId = weakPointId;
            this.healthPercent = healthPercent;
            this.type = type;
            this.revealed = false;
        }

        public boolean isRevealed() {
            return revealed;
        }

        public void reveal() {
            this.revealed = true;
        }

        public void hide() {
            this.revealed = false;
        }

        public int getHealthPercent() {
            return healthPercent;
        }

        public WeakPointType getType() {
            return type;
        }

        public String getBossId() {
            return bossId;
        }

        public String getWeakPointId() {
            return weakPointId;
        }
    }

    public static final class BossChallengeData {
        private final String bossId;
        private final String displayName;
        private final int maxHealth;
        private final List<WeakPoint> weakPoints = new ArrayList<>();
        private final Set<String> unlockedAchievements = ConcurrentHashMap.newKeySet();

        public BossChallengeData(String bossId, String displayName, int maxHealth) {
            this.bossId = bossId;
            this.displayName = displayName;
            this.maxHealth = maxHealth;

            String id = bossId.toLowerCase();
            if (id.contains("shell")) {
                addWeakPoint(70, WeakPointType.EYES);
                addWeakPoint(40, WeakPointType.WEAPON);
            } else if (id.contains("frieza")) {
                addWeakPoint(80, WeakPointType.EYES);
                addWeakPoint(60, WeakPointType.ARM);
            } else if (id.contains("majin")) {
                addWeakPoint(90, WeakPointType.CORE);
            } else {
                addWeakPoint(50, WeakPointType.CORE);
            }
        }

        public void addWeakPoint(int healthPercent, WeakPointType type) {
            WeakPoint wp = new WeakPoint(bossId, "weak_point_" + healthPercent, healthPercent, type);
            weakPoints.add(wp);
            weakPoints.sort((a, b) -> Integer.compare(a.getHealthPercent(), b.getHealthPercent()));
        }

        public List<WeakPoint> getWeakPoints() {
            return new ArrayList<>(weakPoints);
        }

        public boolean isWeakPointRevealed(String weakPointId) {
            return findWeakPoint(weakPointId).map(WeakPoint::isRevealed).orElse(false);
        }

        public void revealWeakPoint(String weakPointId) {
            findWeakPoint(weakPointId).ifPresent(WeakPoint::reveal);
        }

        public void hideWeakPoint(String weakPointId) {
            findWeakPoint(weakPointId).ifPresent(WeakPoint::hide);
        }

        private Optional<WeakPoint> findWeakPoint(String weakPointId) {
            return weakPoints.stream()
                    .filter(p -> p.getWeakPointId().equals(weakPointId))
                    .findFirst();
        }

        public void addAchievement(String achievementType) {
            if (achievementType != null) {
                unlockedAchievements.add(achievementType);
            }
        }

        public boolean isAchievementUnlocked(String achievementType) {
            return unlockedAchievements.contains(achievementType);
        }

        public Set<String> getUnlockedAchievements() {
            return new HashSet<>(unlockedAchievements);
        }

        public List<String> getUnlockedSkillTreeBranches() {
            return getAllBranches().keySet().stream()
                    .filter(id -> id.contains(bossId))
                    .toList();
        }

        public String getBossId() {
            return bossId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getMaxHealth() {
            return maxHealth;
        }
    }

    private static final Map<String, String> ALL_BRANCHES = new ConcurrentHashMap<>();
    private static final Map<String, BossChallengeData> BOSS_DATA = new ConcurrentHashMap<>();

    public static void createBoss(String bossId, String displayName, int maxHealth) {
        BossChallengeData data = new BossChallengeData(bossId, displayName, maxHealth);
        registerSkillTreeBranches(bossId, data.getWeakPoints().size());
        BOSS_DATA.put(bossId, data);
        XenoPixelsMod.LOGGER.info("Boss challenge: Created boss '{}'", displayName);
    }

    private static List<String> registerSkillTreeBranches(String bossId, int weakPointCount) {
        List<String> branches = new ArrayList<>();
        String baseBranch = "boss_skill_tree_base_" + bossId;
        ALL_BRANCHES.put(baseBranch, "base_branch");
        branches.add(baseBranch);

        if (weakPointCount >= 3) {
            String wpMastery = "boss_skill_tree_weakpoint_mastery_" + bossId;
            ALL_BRANCHES.put(wpMastery, "wp_mastery");
            branches.add(wpMastery);
        }
        return branches;
    }

    private static Map<String, String> getAllBranches() {
        return Collections.unmodifiableMap(ALL_BRANCHES);
    }

    public static void revealWeakPoint(String bossId, String weakPointId) {
        BossChallengeData data = BOSS_DATA.get(bossId);
        if (data != null) {
            data.revealWeakPoint(weakPointId);
        }
    }

    public static void hideWeakPoint(String bossId, String weakPointId) {
        BossChallengeData data = BOSS_DATA.get(bossId);
        if (data != null) {
            data.hideWeakPoint(weakPointId);
        }
    }

    public static void addAchievement(String bossId, String achievementType) {
        BossChallengeData data = BOSS_DATA.get(bossId);
        if (data != null && !data.isAchievementUnlocked(achievementType)) {
            data.addAchievement(achievementType);
        }
    }

    public static boolean isAchievementUnlocked(String bossId, String achievementType) {
        BossChallengeData data = BOSS_DATA.get(bossId);
        return data != null && data.isAchievementUnlocked(achievementType);
    }

    public static Set<String> getUnlockedAchievements(String bossId) {
        BossChallengeData data = BOSS_DATA.get(bossId);
        return data != null ? data.getUnlockedAchievements() : Collections.emptySet();
    }

    public static List<String> getUnlockedSkillTreeBranches(String bossId) {
        BossChallengeData data = BOSS_DATA.get(bossId);
        return data != null ? data.getUnlockedSkillTreeBranches() : Collections.emptyList();
    }

    public static boolean isWeakPointRevealed(String bossId, String weakPointId) {
        BossChallengeData data = BOSS_DATA.get(bossId);
        return data != null && data.isWeakPointRevealed(weakPointId);
    }

    public static Set<String> getAllBosses() {
        return Collections.unmodifiableSet(BOSS_DATA.keySet());
    }

    public static void clearAllData() {
        BOSS_DATA.clear();
        ALL_BRANCHES.clear();
        XenoPixelsMod.LOGGER.info("Boss challenge: All data cleared");
    }
}
