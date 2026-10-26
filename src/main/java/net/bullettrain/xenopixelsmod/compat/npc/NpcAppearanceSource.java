package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.stats.character.Character;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/**
 * A look, resolved from whatever owns it - a saved NPC profile, or the renderer's cached server
 * state - in exactly the shape DragonMineZ's {@code Character} wants.
 *
 * <p>Holds only the appearance fields. Race history, forms, masteries, skills, stats and quest
 * progress deliberately are not here: a look can be lent to a fighter, its progression cannot.
 *
 * @param hairCode the stored hair code behind {@code hairId} 0, kept so a truncated or unreadable
 *                 code can be refused instead of silently leaving the target bald
 * @param hair four slots (base, SSJ, SSJ2, SSJ3), or null when the source has no custom hair
 */
public record NpcAppearanceSource(String race, String gender, String characterClass,
                                  int bodyType, int eyesType, int noseType, int mouthType,
                                  int tattooType, float boobScale,
                                  String bodyColor, String bodyColor2, String bodyColor3,
                                  String eye1Color, String eye2Color,
                                  String hairColor, String auraColor, String activeHeadBone,
                                  boolean saiyanTail, boolean renderHairBase, int hairId,
                                  String hairCode, CustomHair[] hair) {

    public NpcAppearanceSource {
        race = blank(race) ? "human" : race.trim().toLowerCase(Locale.ROOT);
        gender = blank(gender) ? "male" : gender.trim().toLowerCase(Locale.ROOT);
        characterClass = blank(characterClass) ? "warrior" : characterClass.trim().toLowerCase(Locale.ROOT);
        bodyType = NpcDmzAppearance.nonNegative(bodyType);
        eyesType = NpcDmzAppearance.nonNegative(eyesType);
        noseType = NpcDmzAppearance.nonNegative(noseType);
        mouthType = NpcDmzAppearance.nonNegative(mouthType);
        tattooType = NpcDmzAppearance.nonNegative(tattooType);
        boobScale = NpcDmzAppearance.clampBoobScale(boobScale);
        bodyColor = orDefault(bodyColor, "#F4C7A1");
        bodyColor2 = orDefault(bodyColor2, "#C98F68");
        bodyColor3 = orDefault(bodyColor3, "#FFFFFF");
        eye1Color = orDefault(eye1Color, "#000000");
        eye2Color = orDefault(eye2Color, "#000000");
        // DMZ paints an unset hair or aura white, so an unset source is passed through as white
        // rather than as a blank string the wire cap would then have to reject.
        hairColor = orDefault(hairColor, "#FFFFFF");
        auraColor = orDefault(auraColor, "#FFFFFF");
        activeHeadBone = blank(activeHeadBone) ? "" : headBone(activeHeadBone);
        hairId = Math.max(0, hairId);
        hairCode = blank(hairCode) ? "" : hairCode.trim();
        hair = hair == null ? null : hair.clone();
    }

    /**
     * The look a saved NPC is wearing right now, or null for an entity that has no NPC profile.
     *
     * <p>Reads the profile, not the client's copy of it, so the answer is the same one the editor
     * shows and the appearance packet sends.
     */
    public static NpcAppearanceSource from(Entity npc) {
        if (npc == null || !NpcCombatProfile.hasProfile(npc)) {
            return null;
        }
        NpcCombatProfile profile = NpcCombatProfile.read(npc);
        // A CustomNPCs Gecko model can hold a longer code than the profile copy managed to save.
        NpcHairBridge.fillProfile(npc, profile);
        return of(profile);
    }

    /** The same resolution from a profile already in hand. */
    public static NpcAppearanceSource of(NpcCombatProfile profile) {
        if (profile == null) {
            return null;
        }
        NpcDmzAppearance a = profile.appearance == null ? new NpcDmzAppearance() : profile.appearance;
        CustomHair[] hair = NpcHairSet.resolve(profile.hairCode, profile.hairColor, profile.hairStyleId);
        return new NpcAppearanceSource(profile.raceId, a.gender, profile.characterClass(),
                a.bodyType, a.eyesType, a.noseType, a.mouthType, a.tattooType, a.boobScale,
                a.bodyColor, a.bodyColor2, a.bodyColor3, a.eye1Color, a.eye2Color,
                profile.hairColor, auraOf(profile), a.activeHeadBone, a.saiyanTail, a.renderHairBase,
                profile.hairStyleId, profile.hairCode, hair);
    }

    /**
     * The look a DragonMineZ character is wearing right now, read back in the same shape an NPC's
     * look arrives in. Read-only: nothing here writes, and a player's own {@code hairCode} is not a
     * thing DragonMineZ stores, so it reports the custom strands it can see instead.
     */
    public static NpcAppearanceSource ofCharacter(Character character) {
        if (character == null) {
            return null;
        }
        return new NpcAppearanceSource(character.getRace(), character.getGender(),
                character.getCharacterClass(), character.getBodyType(), character.getEyesType(),
                character.getNoseType(), character.getMouthType(), character.getTattooType(),
                character.getBoobScale(), character.getBodyColor(), character.getBodyColor2(),
                character.getBodyColor3(), character.getEye1Color(), character.getEye2Color(),
                character.getHairColor(), character.getAuraColor(), character.getActiveHeadBone(),
                character.isHasSaiyanTail(), character.isRenderHairBase(), character.getHairId(), "",
                new CustomHair[]{character.getHairBase(), character.getHairStyle(com.dragonminez.common.hair.HairStyleSlot.SSJ),
                        character.getHairStyle(com.dragonminez.common.hair.HairStyleSlot.SSJ2), character.getHairStyle(com.dragonminez.common.hair.HairStyleSlot.SSJ3),
                        character.getHairStyle(com.dragonminez.common.hair.HairStyleSlot.SSJ4)});
    }

    /** Slot {@code index} of the resolved hair, always present; an empty slot clears the target's. */
    public CustomHair hairSlot(int index) {
        return NpcHairSet.slot(hair, index);
    }

    /** A defensive copy of the hair array for callers that hand it to another owner. */
    public CustomHair[] hairCopy() {
        return hair == null ? null : hair.clone();
    }

    /** The profile's chosen aura: the explicit hex when set, otherwise the stored rgb int. */
    public static String auraOf(NpcCombatProfile profile) {
        if (profile == null) {
            return "#FFFFFF";
        }
        if (!blank(profile.auraColorHex)) {
            return profile.auraColorHex;
        }
        return profile.auraColor == 0 ? "#FFFFFF" : NpcCombatProfile.formatHex(profile.auraColor);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** Bone names are capped at 64 characters by DMZ's own appearance packet. */
    private static String headBone(String value) {
        String trimmed = value.trim();
        return trimmed.length() <= 64 ? trimmed : trimmed.substring(0, 64);
    }

    private static String orDefault(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }
}
