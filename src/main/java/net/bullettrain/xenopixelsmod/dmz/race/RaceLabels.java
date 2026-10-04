package net.bullettrain.xenopixelsmod.dmz.race;

/**
 * Literal picker name and description for a custom race pack.
 *
 * <p>DragonMineZ {@code RaceSelectionScreen} looks up {@code race.dragonminez.<id>} and
 * {@code race.dragonminez.<id>.desc}. Custom folder ids are not in {@code en_us.json}, so
 * these strings are stored beside {@code character.json} and injected through the language
 * mixin.
 */
public record RaceLabels(String raceId, String displayName, String description) {
    public static RaceLabels blank(String raceId) {
        return new RaceLabels(raceId == null ? "" : raceId, "", "");
    }

    public boolean hasLiteral() {
        return notBlank(displayName) || notBlank(description);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
