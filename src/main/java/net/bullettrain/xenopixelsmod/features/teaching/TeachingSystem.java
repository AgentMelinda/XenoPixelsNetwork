package net.bullettrain.xenopixelsmod.features.teaching;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 5: Teaching System
 */
public final class TeachingSystem {

    private TeachingSystem() {
    }

    public enum TeachingMode {
        NONE(0, 0.0f),
        OBSERVING(1, 1.2f),
        SYNERGY(2, 1.5f),
        MASTERY(3, 2.0f);

        private final int level;
        private final float xpMultiplier;

        TeachingMode(int level, float multiplier) {
            this.level = level;
            this.xpMultiplier = multiplier;
        }

        public int getLevel() {
            return level;
        }

        public float getXpMultiplier() {
            return xpMultiplier;
        }
    }

    public static final class TeachingRelationship {
        private final String teacherId;
        private final String studentId;
        private int synergyLevel;
        private long xpSharedCount;
        private final Set<String> taughtMoves = ConcurrentHashMap.newKeySet();

        public TeachingRelationship(String teacherId, String studentId) {
            this.teacherId = teacherId;
            this.studentId = studentId;
            this.synergyLevel = 0;
            this.xpSharedCount = 0L;
        }

        public void recordXPGain(float xpGained, TeachingContext context) {
            xpSharedCount++;
            if (context != null && context.isSynergyActive()) {
                float studentXp = xpGained;
                float teacherXp = xpGained * 0.5f;
                XenoPixelsMod.LOGGER.debug(
                        "Teaching: XP student={} teacher={} synergy={}",
                        formatXp(studentXp), formatXp(teacherXp), synergyLevel);
            } else {
                XenoPixelsMod.LOGGER.debug("Teaching: XP student only={}", formatXp(xpGained));
            }
        }

        public boolean isSynergyActive() {
            return synergyLevel >= 1;
        }

        public void incrementSynergyLevel() {
            if (synergyLevel < 3) {
                synergyLevel++;
                unlockMoveAtLevel(synergyLevel);
            }
        }

        private void unlockMoveAtLevel(int level) {
            taughtMoves.add("teaching_combo_" + level);
        }

        public void addTaughtMove(String moveId) {
            if (moveId != null) {
                taughtMoves.add(moveId);
            }
        }

        public boolean isMoveTaught(String moveId) {
            return taughtMoves.contains(moveId);
        }

        public Set<String> getTaughtMoves() {
            return new HashSet<>(taughtMoves);
        }

        public int getSynergyLevel() {
            return synergyLevel;
        }

        public long getXpSharedCount() {
            return xpSharedCount;
        }

        public String getTeacherId() {
            return teacherId;
        }

        public String getStudentId() {
            return studentId;
        }

        public static final class TeachingContext {
            private final boolean synergyActive;

            public TeachingContext(boolean synergy) {
                this.synergyActive = synergy;
            }

            public boolean isSynergyActive() {
                return synergyActive;
            }
        }
    }

    private static final Map<String, TeachingRelationship> TEACHING_RELATIONSHIPS = new ConcurrentHashMap<>();

    private static String key(String teacherId, String studentId) {
        return teacherId + "_" + studentId;
    }

    public static TeachingRelationship getOrCreateRelationship(String teacherId, String studentId) {
        return TEACHING_RELATIONSHIPS.computeIfAbsent(
                key(teacherId, studentId),
                k -> new TeachingRelationship(teacherId, studentId));
    }

    public static void recordXPGain(String teacherId, String studentId, float xpGained) {
        TeachingRelationship relationship = getOrCreateRelationship(teacherId, studentId);
        relationship.recordXPGain(xpGained, new TeachingRelationship.TeachingContext(true));
    }

    public static void incrementSynergyLevel(String teacherId, String studentId) {
        TeachingRelationship relationship = TEACHING_RELATIONSHIPS.get(key(teacherId, studentId));
        if (relationship != null) {
            relationship.incrementSynergyLevel();
        }
    }

    public static void addTaughtMove(String teacherId, String studentId, String moveId) {
        TeachingRelationship relationship = TEACHING_RELATIONSHIPS.get(key(teacherId, studentId));
        if (relationship != null) {
            relationship.addTaughtMove(moveId);
        }
    }

    public static Set<String> getTaughtMoves(String teacherId, String studentId) {
        TeachingRelationship relationship = TEACHING_RELATIONSHIPS.get(key(teacherId, studentId));
        return relationship != null ? relationship.getTaughtMoves() : Collections.emptySet();
    }

    public static int getSynergyLevel(String teacherId, String studentId) {
        TeachingRelationship relationship = TEACHING_RELATIONSHIPS.get(key(teacherId, studentId));
        return relationship != null ? relationship.getSynergyLevel() : 0;
    }

    public static boolean isSynergyActive(String teacherId, String studentId) {
        TeachingRelationship relationship = TEACHING_RELATIONSHIPS.get(key(teacherId, studentId));
        return relationship != null && relationship.isSynergyActive();
    }

    public static long getXpSharedCount(String teacherId, String studentId) {
        TeachingRelationship relationship = TEACHING_RELATIONSHIPS.get(key(teacherId, studentId));
        return relationship != null ? relationship.getXpSharedCount() : 0L;
    }

    private static String formatXp(float xp) {
        if (xp >= 1_000_000f) {
            return String.format("%.2fM", xp / 1_000_000f);
        }
        if (xp >= 10_000f) {
            return String.format("%.2fK", xp / 1_000f);
        }
        return String.valueOf(Math.round(xp));
    }

    public static void clearAllRelationships() {
        TEACHING_RELATIONSHIPS.clear();
        XenoPixelsMod.LOGGER.info("Teaching: All relationships cleared");
    }
}
