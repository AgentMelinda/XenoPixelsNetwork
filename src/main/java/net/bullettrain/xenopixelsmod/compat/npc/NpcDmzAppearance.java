package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/**
 * Player-independent subset of DragonMineZ's Character appearance data.
 * Combat/form history deliberately stays in {@link NpcCombatProfile}.
 */
public final class NpcDmzAppearance {
    public enum Mode {
        OFF,
        OVERLAY,
        FULL;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Mode parse(String value) {
            if (value == null) return OFF;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return OFF;
            }
        }
    }

    private static final int SCHEMA = 4;
    /** DMZ 2.1.3's eyebrow layer 9 contains one stray body-atlas pixel. */
    public static final int BROKEN_EYEBROW_TYPE = 9;

    public Mode mode = Mode.OFF;
    public String gender = "male";
    public String characterClass = "warrior";
    public int bodyType;
    public int eyesType;
    /** Human/Saiyan eyebrow texture choice. Other races keep DMZ's linked eye behavior. */
    public int eyebrowsType;
    public int noseType;
    public int mouthType;
    public int tattooType;
    public float boobScale = 1.0f;
    public String bodyColor = "#F4C7A1";
    public String bodyColor2 = "#C98F68";
    public String bodyColor3 = "#FFFFFF";
    public String eye1Color = "#000000";
    public String eye2Color = "#000000";
    /** Empty inherits DMZ's race/body/form tail color for Saiyan, Bio-Android and Frost Demon. */
    public String tailColor = "";
    /**
     * Whether the tail takes the race's own colour instead of {@link #tailColor}.
     *
     * <p>Separate from the colour rather than encoded as a blank string, so switching to the race
     * colour and back does not throw the chosen colour away. Defaults true, which is what every
     * existing NPC was already doing.
     */
    public boolean tailUseRaceColor = true;
    public String activeHeadBone = "";
    public boolean saiyanTail;
    public boolean renderHairBase = true;

    public NpcDmzAppearance copy() {
        return fromTag(toTag());
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Schema", SCHEMA);
        tag.putString("Mode", mode.name());
        tag.putString("Gender", safe(gender, "male"));
        tag.putString("Class", safe(characterClass, "warrior"));
        tag.putInt("BodyType", nonNegative(bodyType));
        tag.putInt("EyesType", nonNegative(eyesType));
        tag.putInt("EyebrowsType", sanitizeEyebrowType(eyebrowsType, eyesType));
        tag.putInt("NoseType", nonNegative(noseType));
        tag.putInt("MouthType", nonNegative(mouthType));
        tag.putInt("TattooType", nonNegative(tattooType));
        tag.putFloat("BoobScale", clampBoobScale(boobScale));
        tag.putString("BodyColor", color(bodyColor, "#F4C7A1"));
        tag.putString("BodyColor2", color(bodyColor2, "#C98F68"));
        tag.putString("BodyColor3", color(bodyColor3, "#FFFFFF"));
        tag.putString("Eye1Color", color(eye1Color, "#000000"));
        tag.putString("Eye2Color", color(eye2Color, "#000000"));
        tag.putString("TailColor", optionalColor(tailColor));
        tag.putBoolean("TailUseRaceColor", tailUseRaceColor);
        tag.putString("ActiveHeadBone", activeHeadBone == null ? "" : activeHeadBone.trim());
        tag.putBoolean("SaiyanTail", saiyanTail);
        tag.putBoolean("RenderHairBase", renderHairBase);
        return tag;
    }

    public static NpcDmzAppearance fromTag(CompoundTag tag) {
        NpcDmzAppearance out = new NpcDmzAppearance();
        if (tag == null || tag.isEmpty()) return out;
        out.mode = Mode.parse(tag.getString("Mode"));
        out.gender = safe(tag.getString("Gender"), "male").toLowerCase(Locale.ROOT);
        out.characterClass = safe(tag.getString("Class"), "warrior").toLowerCase(Locale.ROOT);
        out.bodyType = nonNegative(tag.getInt("BodyType"));
        out.eyesType = nonNegative(tag.getInt("EyesType"));
        // Schema 1 used the eyes choice for both eye and eyebrow layers.
        out.eyebrowsType = tag.contains("EyebrowsType")
                ? nonNegative(tag.getInt("EyebrowsType"))
                : out.eyesType;
        out.eyebrowsType = sanitizeEyebrowType(out.eyebrowsType, out.eyesType);
        out.noseType = nonNegative(tag.getInt("NoseType"));
        out.mouthType = nonNegative(tag.getInt("MouthType"));
        out.tattooType = nonNegative(tag.getInt("TattooType"));
        out.boobScale = clampBoobScale(tag.contains("BoobScale") ? tag.getFloat("BoobScale") : 1.0f);
        out.bodyColor = color(tag.getString("BodyColor"), out.bodyColor);
        out.bodyColor2 = color(tag.getString("BodyColor2"), out.bodyColor2);
        out.bodyColor3 = color(tag.getString("BodyColor3"), out.bodyColor3);
        out.eye1Color = color(tag.getString("Eye1Color"), out.eye1Color);
        out.eye2Color = color(tag.getString("Eye2Color"), out.eye2Color);
        out.tailColor = optionalColor(tag.getString("TailColor"));
        // Absent means an NPC saved before the flag existed: inherit unless it had a colour, which
        // is exactly what the old blank-string rule meant.
        out.tailUseRaceColor = tag.contains("TailUseRaceColor")
                ? tag.getBoolean("TailUseRaceColor")
                : out.tailColor.isBlank();
        out.activeHeadBone = tag.getString("ActiveHeadBone").trim();
        out.saiyanTail = tag.getBoolean("SaiyanTail");
        out.renderHairBase = !tag.contains("RenderHairBase") || tag.getBoolean("RenderHairBase");
        return out;
    }

    public static float clampBoobScale(float value) {
        return Float.isFinite(value) ? Math.max(0.75f, Math.min(1.25f, value)) : 1.0f;
    }

    /** Replaces DMZ's corrupt eyebrow choice while leaving the valid eye choice untouched. */
    public static int sanitizeEyebrowType(int eyebrowType, int eyesType) {
        int eyebrow = nonNegative(eyebrowType);
        if (eyebrow != BROKEN_EYEBROW_TYPE) return eyebrow;
        int eyes = nonNegative(eyesType);
        return eyes != BROKEN_EYEBROW_TYPE ? eyes : BROKEN_EYEBROW_TYPE - 1;
    }

    /** Cycles the available texture range without ever selecting the corrupt layer 9. */
    public static int cycleEyebrowType(int value, int direction, int maximum) {
        int max = Math.max(0, maximum);
        int next = Math.max(0, Math.min(max, value));
        for (int attempts = 0; attempts <= max; attempts++) {
            next = Math.floorMod(next + direction, max + 1);
            if (next != BROKEN_EYEBROW_TYPE) return next;
        }
        return 0;
    }

    private static int nonNegative(int value) {
        return Math.max(0, Math.min(1024, value));
    }

    private static String color(String raw, String fallback) {
        String canonical = NpcCombatProfile.canonicalizeHairColor(raw);
        return canonical.isBlank() ? fallback : canonical;
    }

    private static String optionalColor(String raw) {
        return NpcCombatProfile.canonicalizeHairColor(raw);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
