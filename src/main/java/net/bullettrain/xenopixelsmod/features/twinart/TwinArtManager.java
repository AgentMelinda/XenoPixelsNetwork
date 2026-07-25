package net.bullettrain.xenopixelsmod.features.twinart;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 7: Twin Art / Dual-Wield System
 */
public final class TwinArtManager {

    private TwinArtManager() {
    }

    public enum WeaponType {
        KNIFE("Knife"),
        DAGGER("Dagger"),
        KATANA("Katana/Sword"),
        SHURIKEN("Shuriken/Throwing star"),
        BLASTER("Energy Blaster");

        private final String displayName;

        WeaponType(String name) {
            this.displayName = name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum FireMode {
        SINGLE("Single"),
        ALTERNATING("Alternating"),
        SIMULTANEOUS("Simultaneous");

        private final String displayName;

        FireMode(String name) {
            this.displayName = name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum SynergyLevel {
        NONE(0, "No synergy - incompatible types"),
        BASIC(1, "Basic synergy - minor bonuses"),
        MODERATE(2, "Moderate synergy - enhanced attacks"),
        ADVANCED(3, "Advanced synergy - special combos"),
        PERFECT(4, "Perfect synergy - ultimate abilities");

        private final int level;
        private final String description;

        SynergyLevel(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public String getDescription() {
            return description;
        }

        public static SynergyLevel fromLevel(int level) {
            for (SynergyLevel value : values()) {
                if (value.level == level) {
                    return value;
                }
            }
            if (level <= 0) {
                return NONE;
            }
            if (level >= PERFECT.level) {
                return PERFECT;
            }
            return BASIC;
        }
    }

    public static final class TwinArtPairing {
        private final String playerId;
        private final WeaponType primaryType;
        private final WeaponType secondaryType;
        private FireMode fireMode;
        private int synergyLevel;

        public TwinArtPairing(
                String playerId, WeaponType primaryType, WeaponType secondaryType, FireMode fireMode) {
            this.playerId = playerId;
            this.primaryType = primaryType;
            this.secondaryType = secondaryType;
            this.fireMode = fireMode != null ? fireMode : FireMode.SINGLE;
            this.synergyLevel = calculateInitialSynergy(primaryType, secondaryType);
        }

        private static int calculateInitialSynergy(WeaponType primary, WeaponType secondary) {
            if (primary == null || secondary == null) {
                return 0;
            }
            if (primary == WeaponType.KNIFE && secondary == WeaponType.DAGGER) {
                return 2;
            }
            if (primary == WeaponType.DAGGER && secondary == WeaponType.KNIFE) {
                return 2;
            }
            if (primary == WeaponType.KATANA
                    && (secondary == WeaponType.SHURIKEN || secondary == WeaponType.BLASTER)) {
                return 2;
            }
            if (primary == WeaponType.SHURIKEN && secondary == WeaponType.KATANA) {
                return 2;
            }
            if (primary == WeaponType.BLASTER && secondary == WeaponType.BLASTER) {
                return 3;
            }
            if (primary == secondary) {
                return 1;
            }
            return 0;
        }

        public void incrementSynergyLevel() {
            if (synergyLevel < SynergyLevel.ADVANCED.getLevel()) {
                synergyLevel++;
            }
        }

        public boolean isActiveFireMode(FireMode mode) {
            return this.fireMode == mode;
        }

        public FireMode toggleFireMode() {
            List<FireMode> modes = Arrays.asList(FireMode.values());
            int currentIndex = modes.indexOf(this.fireMode);
            int nextIndex = (currentIndex + 1) % modes.size();
            this.fireMode = modes.get(nextIndex);
            return fireMode;
        }

        public WeaponType getPrimaryType() {
            return primaryType;
        }

        public WeaponType getSecondaryType() {
            return secondaryType;
        }

        public FireMode getFireMode() {
            return fireMode;
        }

        public int getSynergyLevel() {
            return synergyLevel;
        }

        public String getPlayerId() {
            return playerId;
        }

        public String getSynergyDescription() {
            return SynergyLevel.fromLevel(synergyLevel).getDescription();
        }
    }

    private static final Map<String, TwinArtPairing> TWIN_ART_PAIRINGS = new ConcurrentHashMap<>();

    private static String key(String playerId) {
        return playerId + "_twin_art";
    }

    public static TwinArtPairing registerPairing(
            String playerId, WeaponType primaryType, WeaponType secondaryType, FireMode fireMode) {
        String mapKey = key(playerId);
        TwinArtPairing existing = TWIN_ART_PAIRINGS.get(mapKey);
        if (existing != null) {
            return existing;
        }
        TwinArtPairing pairing = new TwinArtPairing(playerId, primaryType, secondaryType, fireMode);
        TWIN_ART_PAIRINGS.put(mapKey, pairing);
        XenoPixelsMod.LOGGER.debug(
                "Twin Art: Registered '{}' with {} + {} (fire: {}, synergy: {})",
                playerId,
                primaryType.getDisplayName(),
                secondaryType.getDisplayName(),
                fireMode,
                pairing.getSynergyLevel());
        return pairing;
    }

    public static TwinArtPairing getOrCreatePairing(
            String playerId, WeaponType primaryType, WeaponType secondaryType, FireMode fireMode) {
        return TWIN_ART_PAIRINGS.computeIfAbsent(
                key(playerId),
                k -> new TwinArtPairing(playerId, primaryType, secondaryType, fireMode));
    }

    public static TwinArtPairing getPairing(String playerId) {
        return TWIN_ART_PAIRINGS.get(key(playerId));
    }

    public static FireMode toggleFireMode(String playerId) {
        TwinArtPairing pairing = getPairing(playerId);
        return pairing != null ? pairing.toggleFireMode() : null;
    }

    public static void incrementSynergyLevel(String playerId) {
        TwinArtPairing pairing = getPairing(playerId);
        if (pairing != null) {
            pairing.incrementSynergyLevel();
        }
    }

    public static int getSynergyLevel(String playerId) {
        TwinArtPairing pairing = getPairing(playerId);
        return pairing != null ? pairing.getSynergyLevel() : 0;
    }

    public static boolean isDualWielding(String playerId) {
        TwinArtPairing pairing = getPairing(playerId);
        return pairing != null && pairing.getPrimaryType() != pairing.getSecondaryType();
    }

    public static WeaponType getPrimaryType(String playerId) {
        TwinArtPairing pairing = getPairing(playerId);
        return pairing != null ? pairing.getPrimaryType() : null;
    }

    public static WeaponType getSecondaryType(String playerId) {
        TwinArtPairing pairing = getPairing(playerId);
        return pairing != null ? pairing.getSecondaryType() : null;
    }

    public static FireMode getFireMode(String playerId) {
        TwinArtPairing pairing = getPairing(playerId);
        return pairing != null ? pairing.getFireMode() : null;
    }

    public static String getSynergyDescription(String playerId) {
        TwinArtPairing pairing = getPairing(playerId);
        return pairing != null ? pairing.getSynergyDescription() : "No twin art active";
    }

    public static void clearAllPairings() {
        TWIN_ART_PAIRINGS.clear();
        XenoPixelsMod.LOGGER.info("Twin Art: All pairings cleared");
    }
}
