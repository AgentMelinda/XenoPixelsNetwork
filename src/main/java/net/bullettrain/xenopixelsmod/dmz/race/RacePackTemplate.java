package net.bullettrain.xenopixelsmod.dmz.race;

/**
 * Skeleton defaults for a new race pack. Mirrors DMZ
 * {@code ConfigManager#setupDefaultCharacter} (custom / non-default race path).
 */
public record RacePackTemplate(
        boolean hasGender,
        boolean useVanillaSkin,
        boolean isLayered,
        String racialSkill,
        String auraType,
        String defaultBodyColor,
        String defaultBodyColor2,
        String defaultBodyColor3,
        String defaultHairColor,
        String defaultEye1Color,
        String defaultEye2Color,
        String defaultAuraColor
) {
    /** Values from {@code ConfigManager#setupDefaultCharacter} (DMZ 2.1.3). */
    public static RacePackTemplate defaults() {
        return new RacePackTemplate(
                true,
                true,
                true,
                "human",
                "kakarot",
                "#FFD3C9",
                "#FFD3C9",
                "#FFD3C9",
                "#222629",
                "#222629",
                "#222629",
                "#7FFFFF"
        );
    }
}
