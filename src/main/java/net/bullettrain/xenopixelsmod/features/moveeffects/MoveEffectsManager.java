package net.bullettrain.xenopixelsmod.features.moveeffects;

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
 * Feature 9: Move Animations & Effects
 */
public final class MoveEffectsManager {

    private MoveEffectsManager() {
    }

    public enum EnergyType {
        ENERGY("Energy", "#00FFFF"),
        FIRE("Fire", "#FF4500"),
        ICE("Ice", "#00BFFF"),
        LIGHTNING("Lightning", "#FFFF00"),
        PSI("Psi", "#8A2BE2"),
        DRAGON_RAY("Dragon Ray", "#FFD700");

        private final String displayName;
        private final String colorCode;

        EnergyType(String name, String color) {
            this.displayName = name;
            this.colorCode = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorCode() {
            return colorCode;
        }
    }

    public enum ParticleType {
        ENERGY_SPIKE("Energy Spike", "Spherical energy burst"),
        FLAME_TRAIL("Flame Trail", "Continuous flame particles"),
        ICE_FLAKE("Ice Flake", "Snowflake-like ice particles"),
        LIGHTNING_ZAP("Lightning Zap", "Electricity arc effect"),
        DRAGON_BEAM("Dragon Beam", "Cylindrical beam trail"),
        KAMEHAMEHA("Kamehameha", "Energy blast projectile");

        private final String displayName;
        private final String description;

        ParticleType(String name, String description) {
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

    public enum FinisherType {
        KAMEHAMEHA("Kamehameha", "Classic energy blast"),
        GENODAMAGERAY("God Damage Ray", "Intense energy beam"),
        METEOR_SMASH("Meteor Smash", "Falling meteor attack"),
        SUPER_KNUCKLE("Super Knuckle", "Energy-enhanced punch"),
        DIVINE_KAMEHAMEHA("Divine Kamehameha", "Omnidirectional energy blast");

        private final String displayName;
        private final String description;

        FinisherType(String name, String description) {
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

    public enum IntensityLevel {
        SUBTLE(0.5f, "Minimal VFX - clean and fast"),
        MODERATE(1.0f, "Balanced VFX - standard gameplay"),
        FULL(2.0f, "Full VFX - maximum effects");

        private final float intensity;
        private final String description;

        IntensityLevel(float intensity, String description) {
            this.intensity = intensity;
            this.description = description;
        }

        public float getIntensity() {
            return intensity;
        }

        public String getDescription() {
            return description;
        }
    }

    public static final class AnimationEffect {
        private final String moveId;
        private final String animationName;
        private final EnergyType energyType;
        private final ParticleType particleType;
        private final boolean finisher;

        public AnimationEffect(
                String moveId, String animationName, EnergyType energyType, ParticleType particleType) {
            this(moveId, animationName, energyType, particleType, false);
        }

        private AnimationEffect(
                String moveId,
                String animationName,
                EnergyType energyType,
                ParticleType particleType,
                boolean finisher) {
            this.moveId = moveId;
            this.animationName = animationName;
            this.energyType = energyType;
            this.particleType = particleType;
            this.finisher = finisher;
        }

        public static AnimationEffect createFinisher(String moveId, String animationName) {
            return new AnimationEffect(
                    moveId, animationName, EnergyType.ENERGY, ParticleType.DRAGON_BEAM, true);
        }

        public boolean isFinisher() {
            return finisher;
        }

        public String getMoveId() {
            return moveId;
        }

        public String getAnimationName() {
            return animationName;
        }

        public EnergyType getEnergyType() {
            return energyType;
        }

        public ParticleType getParticleType() {
            return particleType;
        }
    }

    private static final Map<String, AnimationEffect> ANIMATION_EFFECTS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> UNLOCKED_FINISHERS = new ConcurrentHashMap<>();
    private static IntensityLevel globalIntensity = IntensityLevel.MODERATE;

    public static void registerAnimationEffect(
            String moveId, String animationName, EnergyType energyType, ParticleType particleType) {
        if (moveId == null || ANIMATION_EFFECTS.containsKey(moveId)) {
            return;
        }
        ANIMATION_EFFECTS.put(moveId, new AnimationEffect(moveId, animationName, energyType, particleType));
    }

    public static Optional<AnimationEffect> getAnimationEffect(String moveId) {
        return Optional.ofNullable(ANIMATION_EFFECTS.get(moveId));
    }

    public static List<AnimationEffect> getAllEffects() {
        return new ArrayList<>(ANIMATION_EFFECTS.values());
    }

    public static boolean isFinisherUnlocked(String playerId, String finisherId) {
        if (finisherId != null
                && (finisherId.contains("Kamehameha") || finisherId.equals("SUPER_KNUCKLE"))) {
            return true;
        }
        Set<String> finishers = UNLOCKED_FINISHERS.getOrDefault(playerId, Collections.emptySet());
        return finishers.contains(finisherId);
    }

    public static void unlockFinisher(String playerId, String finisherId) {
        if (playerId == null || finisherId == null) {
            return;
        }
        UNLOCKED_FINISHERS.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet()).add(finisherId);
    }

    public static Set<String> getUnlockedFinishers(String playerId) {
        Set<String> finishers = UNLOCKED_FINISHERS.get(playerId);
        return finishers != null ? new HashSet<>(finishers) : Collections.emptySet();
    }

    public static void setIntensity(IntensityLevel level) {
        if (level != null && globalIntensity != level) {
            globalIntensity = level;
            XenoPixelsMod.LOGGER.debug("Move Effects: Global intensity set to {}", level.getDescription());
        }
    }

    public static IntensityLevel getIntensity() {
        return globalIntensity;
    }

    public static void clearAllEffects() {
        ANIMATION_EFFECTS.clear();
        UNLOCKED_FINISHERS.clear();
        XenoPixelsMod.LOGGER.info("Move Effects: All effects cleared");
    }

    public static void resetIntensity() {
        setIntensity(IntensityLevel.MODERATE);
    }
}
