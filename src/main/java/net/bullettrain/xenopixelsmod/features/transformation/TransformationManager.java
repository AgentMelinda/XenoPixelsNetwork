package net.bullettrain.xenopixelsmod.features.transformation;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 10: Transformation System — XenoPixels form catalog + player state.
 */
public final class TransformationManager {

    private TransformationManager() {
    }

    public enum TransformationType {
        SUPER_SAIYAN("Super Saiyan", "Golden hair, increased power"),
        SUPER_SAIYAN_2("Super Saiyan 2", "Spiky hair, more energy control"),
        SUPER_SAIYAN_3("Super Saiyan 3", "Long hair, maximum power but weaker defense"),
        ULTRA_INSTINCT("Ultra Instinct", "Silver hair, perfect reaction times"),
        GOD_SAIYAN("God Ki", "Master of god-level ki manipulation"),
        GOKU_BLACK("Goku Black", "Heart of Ki, reality-warping abilities"),
        TURLES_FORM("Turles Form", "Science-enhanced fighting style"),
        VEGETA_TREE("VEGETA TREE", "Prince heritage fighting style");

        private final String displayName;
        private final String description;

        TransformationType(String name, String description) {
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

    /**
     * Combat stat multipliers for a form (config-scaled values).
     */
    public record FormStats(
            float str,
            float pwr,
            float def,
            float stm,
            float vit,
            float ene,
            float speed,
            float skp) {
        public static FormStats defaultsFor(TransformationType type) {
            return switch (type) {
                case SUPER_SAIYAN -> new FormStats(1.5f, 1.5f, 1.3f, 1.05f, 1.05f, 1.1f, 1.1f, 1.5f);
                case SUPER_SAIYAN_2 -> new FormStats(1.75f, 1.75f, 1.2f, 1.05f, 1.05f, 1.15f, 1.15f, 1.75f);
                case SUPER_SAIYAN_3 -> new FormStats(2.0f, 2.0f, 1.1f, 1.05f, 1.0f, 1.2f, 1.2f, 2.0f);
                case ULTRA_INSTINCT -> new FormStats(2.5f, 2.5f, 1.8f, 1.1f, 1.1f, 1.4f, 1.4f, 2.5f);
                case GOD_SAIYAN -> new FormStats(2.2f, 2.2f, 1.6f, 1.08f, 1.08f, 1.3f, 1.25f, 2.2f);
                case GOKU_BLACK -> new FormStats(2.3f, 2.3f, 1.4f, 1.08f, 1.05f, 1.25f, 1.2f, 2.3f);
                case TURLES_FORM -> new FormStats(2.0f, 2.0f, 1.5f, 1.08f, 1.08f, 1.15f, 1.15f, 2.0f);
                case VEGETA_TREE -> new FormStats(2.1f, 2.1f, 1.7f, 1.1f, 1.1f, 1.2f, 1.18f, 2.1f);
            };
        }
    }

    public static final class TransformationForm {
        private final String transformationId;
        private final String displayName;
        private final TransformationType type;
        private final String groupId;
        private final String shortFormId;
        private final FormStats stats;
        private final Set<String> unlockedMoves = ConcurrentHashMap.newKeySet();
        private final Map<String, Integer> moveLevels = new ConcurrentHashMap<>();

        public TransformationForm(
                String transformationId,
                String displayName,
                TransformationType type,
                String groupId,
                String shortFormId,
                FormStats stats) {
            this.transformationId = transformationId;
            this.displayName = displayName;
            this.type = type;
            this.groupId = groupId != null ? groupId : "";
            this.shortFormId = shortFormId != null ? shortFormId : transformationId;
            this.stats = stats != null ? stats : FormStats.defaultsFor(type);
        }

        public void unlockMove(String moveId) {
            if (moveId != null && unlockedMoves.add(moveId)) {
                moveLevels.putIfAbsent(moveId, 1);
            }
        }

        public void incrementMoveLevel(String moveId) {
            if (moveId == null) {
                return;
            }
            int currentLevel = moveLevels.getOrDefault(moveId, 0);
            if (currentLevel < 10) {
                moveLevels.put(moveId, currentLevel + 1);
            }
        }

        public Set<String> getUnlockedMoves() {
            return new HashSet<>(unlockedMoves);
        }

        public boolean isMoveUnlocked(String moveId) {
            return unlockedMoves.contains(moveId);
        }

        public int getMoveLevel(String moveId) {
            return moveLevels.getOrDefault(moveId, 0);
        }

        /** Legacy int power score (approx). */
        public int getPowerMultiplier() {
            return Math.round(stats.pwr() * 20f);
        }

        public int getDefenseMultiplier() {
            return Math.round(stats.def() * 20f);
        }

        public FormStats getStats() {
            return stats;
        }

        public String getTransformationId() {
            return transformationId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public TransformationType getType() {
            return type;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getShortFormId() {
            return shortFormId;
        }
    }

    public static final class PlayerTransformationState {
        private final String playerId;
        private final Map<String, TransformationForm> availableForms = new ConcurrentHashMap<>();

        public PlayerTransformationState(String playerId) {
            this.playerId = playerId;
        }

        public void registerForm(String transformationId, TransformationForm form) {
            if (transformationId != null && form != null) {
                availableForms.putIfAbsent(transformationId, form);
            }
        }

        public Set<String> getAvailableForms() {
            return new HashSet<>(availableForms.keySet());
        }

        public boolean hasForm(String transformationId) {
            return availableForms.containsKey(transformationId);
        }

        public Optional<TransformationForm> getForm(String transformationId) {
            return Optional.ofNullable(availableForms.get(transformationId));
        }

        public String getPlayerId() {
            return playerId;
        }
    }

    private static final Map<String, Set<String>> ACTIVE_TRANSFORMATIONS = new ConcurrentHashMap<>();
    private static final Map<String, TransformationForm> ALL_FORMS = new ConcurrentHashMap<>();
    private static final Map<String, PlayerTransformationState> PLAYER_STATES = new ConcurrentHashMap<>();
    private static final Map<String, Long> TRANSFORM_COOLDOWNS = new ConcurrentHashMap<>();

    public static void registerForm(String transformationId, String displayName, TransformationType type) {
        registerForm(transformationId, displayName, type, "", transformationId, FormStats.defaultsFor(type));
    }

    public static void registerForm(
            String transformationId,
            String displayName,
            TransformationType type,
            String groupId,
            String shortFormId,
            FormStats stats) {
        if (transformationId == null) {
            return;
        }
        ALL_FORMS.put(
                transformationId,
                new TransformationForm(transformationId, displayName, type, groupId, shortFormId, stats));
    }

    public static void clearRegisteredForms() {
        ALL_FORMS.clear();
    }

    public static Optional<TransformationForm> getForm(String transformationId) {
        return Optional.ofNullable(ALL_FORMS.get(transformationId));
    }

    public static PlayerTransformationState getState(String playerId) {
        return PLAYER_STATES.computeIfAbsent(playerId, PlayerTransformationState::new);
    }

    public static void registerFormForPlayer(String playerId, String transformationId) {
        TransformationForm form = ALL_FORMS.get(transformationId);
        if (form != null) {
            getState(playerId).registerForm(transformationId, form);
        }
    }

    public static void activateTransformation(String playerId, String transformationId) {
        PlayerTransformationState state = getState(playerId);
        if (!state.hasForm(transformationId) && !ALL_FORMS.containsKey(transformationId)) {
            return;
        }
        if (!state.hasForm(transformationId)) {
            state.registerForm(transformationId, ALL_FORMS.get(transformationId));
        }
        Set<String> active = ACTIVE_TRANSFORMATIONS.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        if (active.add(transformationId)) {
            TRANSFORM_COOLDOWNS.put(playerId, System.currentTimeMillis() + 15_000L);
        }
    }

    public static void deactivateTransformation(String playerId, String transformationId) {
        Set<String> active = ACTIVE_TRANSFORMATIONS.get(playerId);
        if (active != null) {
            active.remove(transformationId);
        }
    }

    public static boolean isActive(String playerId, String transformationId) {
        Set<String> active = ACTIVE_TRANSFORMATIONS.get(playerId);
        return active != null && active.contains(transformationId);
    }

    public static boolean isCooldownExpired(String playerId) {
        Long cooldownEnd = TRANSFORM_COOLDOWNS.get(playerId);
        return cooldownEnd == null || System.currentTimeMillis() >= cooldownEnd;
    }

    public static long getRemainingCooldown(String playerId) {
        Long cooldownEnd = TRANSFORM_COOLDOWNS.get(playerId);
        if (cooldownEnd == null) {
            return 0L;
        }
        return Math.max(0L, cooldownEnd - System.currentTimeMillis());
    }

    public static void unlockMoveForPlayer(String playerId, String transformationId, String moveId) {
        getState(playerId).getForm(transformationId).ifPresent(form -> form.unlockMove(moveId));
        TransformationForm global = ALL_FORMS.get(transformationId);
        if (global != null) {
            global.unlockMove(moveId);
        }
    }

    public static void incrementMoveLevel(String playerId, String transformationId, String moveId) {
        getState(playerId).getForm(transformationId).ifPresent(form -> form.incrementMoveLevel(moveId));
        TransformationForm global = ALL_FORMS.get(transformationId);
        if (global != null) {
            global.incrementMoveLevel(moveId);
        }
    }

    public static void clearAllStates() {
        ACTIVE_TRANSFORMATIONS.clear();
        TRANSFORM_COOLDOWNS.clear();
        XenoPixelsMod.LOGGER.info("Transformation: All states and cooldowns cleared");
    }

    public static List<TransformationForm> getAllForms() {
        return new ArrayList<>(ALL_FORMS.values());
    }
}
