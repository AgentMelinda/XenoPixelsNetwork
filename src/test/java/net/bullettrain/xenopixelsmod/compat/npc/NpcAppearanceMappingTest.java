package net.bullettrain.xenopixelsmod.compat.npc;

import com.dragonminez.common.hair.CustomHair;
import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.api.dmz.DmzAppearance;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The look transfer itself: what gets copied, what must not, and whether a snapshot puts a player
 * back exactly as they were.
 *
 * <p>DragonMineZ's config is not loaded on the unit classpath, so race and class acceptance - which
 * reads that config - is checked in game. Everything here is the part that needs no server.
 */
class NpcAppearanceMappingTest {
    @Test void copiesTheLookAndNothingElse() {
        Character target = new Character();
        target.setRace("human");
        target.setActiveForm("superforms", "supersaiyan");
        target.setActiveStackForm("kaioken", "kaiokenx2");
        target.addInteractedMaster("master-1", "Old Master", "minecraft:overworld",
                new net.minecraft.core.BlockPos(1, 2, 3));

        NpcAppearanceMapping.apply(target, look());

        assertEquals("saiyan", target.getRace());
        assertEquals("female", target.getGender());
        assertEquals("martialartist", target.getCharacterClass());
        assertEquals(3, target.getBodyType());
        assertEquals(4, target.getEyesType());
        assertEquals(2, target.getNoseType());
        assertEquals(1, target.getMouthType());
        assertEquals(5, target.getTattooType());
        assertEquals(1.15f, target.getBoobScale());
        assertEquals("#112233", target.getBodyColor());
        assertEquals("#445566", target.getBodyColor2());
        assertEquals("#778899", target.getBodyColor3());
        assertEquals("#AABBCC", target.getEye1Color());
        assertEquals("#DDEEFF", target.getEye2Color());
        assertEquals("#FFE89E", target.getHairColor());
        assertEquals("#FF5500", target.getAuraColor());
        assertEquals("head_second", target.getActiveHeadBone());
        assertTrue(target.isHasSaiyanTail());
        assertFalse(target.isRenderHairBase());
        assertEquals(0, target.getHairId());
        assertFalse(target.getHairBase().isEmpty());
        // The rgb caches are what the renderer reads; only the setters refresh them, so a copy that
        // wrote the string fields directly would keep painting the player's old colours.
        assertEquals(1.0f, target.getRgbHairColor()[0], 0.001f);

        assertEquals("supersaiyan", target.getActiveForm());
        assertEquals("kaiokenx2", target.getActiveStackForm());
        assertEquals(1, target.getInteractedMasters().size());
    }

    @Test void borrowedLookIsRestoredExactly() {
        Character target = new Character();
        target.setRace("human");
        target.setBodyColor("#F5D5A6");
        target.setHairId(7);
        target.setActiveForm("superforms", "supersaiyan");
        Character before = new Character();
        before.copyFrom(target);

        NpcAppearanceMapping.apply(target, look());
        assertNotEquals(before.save(), target.save());

        assertTrue(DmzAppearance.restore(target, DmzAppearance.snapshot(before)));
        assertEquals(before.save(), target.save());
        assertEquals("human", target.getRace());
        assertEquals(7, target.getHairId());
        assertEquals("supersaiyan", target.getActiveForm());
        assertFalse(target.isHasSaiyanTail());
    }

    /**
     * DragonMineZ's {@code CustomHair.load} only writes the strands a tag contains, so handing it
     * the object that is already there - which is what {@code Character.load} does - leaves the
     * borrowed hair stuck on a player whose own hair was empty.
     */
    @Test void emptyHairedSnapshotDoesNotKeepBorrowedHair() {
        Character target = new Character();
        Character plain = new Character();

        NpcAppearanceMapping.apply(target, look());
        assertTrue(hasStrands(target.save().getCompound("HairBase")), "the borrowed look has hair");

        assertTrue(DmzAppearance.restore(target, DmzAppearance.snapshot(plain)));
        assertFalse(hasStrands(target.save().getCompound("HairBase")),
                "restoring a hairless look has to remove it");
        assertEquals(plain.save(), target.save());
    }

    @Test void partialSnapshotIsRefusedAndChangesNothing() {
        Character target = new Character();
        Character before = new Character();
        before.copyFrom(target);
        NpcAppearanceMapping.apply(target, look());

        CompoundTag missingColor = DmzAppearance.snapshot(before);
        missingColor.remove("BodyColor");
        assertFalse(DmzAppearance.isSnapshot(missingColor));
        assertFalse(DmzAppearance.restore(target, missingColor));
        assertEquals("#112233", target.getBodyColor(), "a refused restore must not write at all");

        CompoundTag missingHair = DmzAppearance.snapshot(before);
        missingHair.remove("HairBase");
        assertFalse(DmzAppearance.isSnapshot(missingHair));
        assertFalse(DmzAppearance.restore(target, missingHair));

        assertFalse(DmzAppearance.isSnapshot(new CompoundTag()));
        assertFalse(DmzAppearance.restore(target, null));
        assertNull(DmzAppearance.snapshot(null));
    }

    @Test void refusesAnUnreadableColorOrHairCode() {
        assertEquals(NpcAppearanceMapping.Result.OK, NpcAppearanceMapping.inspect(look()));
        assertEquals(NpcAppearanceMapping.Result.NO_SOURCE, NpcAppearanceMapping.inspect(null));
        assertEquals(NpcAppearanceMapping.Result.NO_SOURCE,
                NpcAppearanceMapping.validate(null, null));

        assertEquals(NpcAppearanceMapping.Result.INVALID_COLOR,
                NpcAppearanceMapping.inspect(withBodyColor("not-a-color")));
        assertEquals(NpcAppearanceMapping.Result.INVALID_HAIR,
                NpcAppearanceMapping.inspect(withHairCode("DMZ1:not-really-hair")));
    }

    @Test void blankLookNormalisesToDragonMineZDefaults() {
        NpcAppearanceSource bare = new NpcAppearanceSource(null, null, null, -4, -1, 9999, 0, 0,
                Float.NaN, null, null, null, null, null, null, null, "  ", false, true, -9, null, null);
        assertEquals("human", bare.race());
        assertEquals("male", bare.gender());
        assertEquals("warrior", bare.characterClass());
        assertEquals(0, bare.bodyType());
        assertEquals(0, bare.eyesType(), "a negative index cannot reach the texture atlas");
        assertEquals(1024, bare.noseType(), "an oversized index clamps instead of going wild");
        assertEquals(1.0f, bare.boobScale());
        assertEquals("#F4C7A1", bare.bodyColor());
        assertEquals("#FFFFFF", bare.hairColor(), "an unset tint is painted white, not left blank");
        assertEquals("#FFFFFF", bare.auraColor());
        assertEquals("", bare.activeHeadBone());
        assertEquals(0, bare.hairId());
        assertEquals("", bare.hairCode());
        assertNull(bare.hair());
    }

    /**
     * The renderer reads the aura and hair style out of the packet's visual-options copy of a
     * profile; the steal reads the profile itself. If the two disagreed, a borrowed look would not
     * be the look the player is standing next to.
     */
    @Test void auraAndHairStyleTravelTheVisualOptionsRoundTrip() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.raceId = "saiyan";
        source.auraColorHex = "#FF5500";
        source.auraColor = 0x123456;
        source.hairStyleId = 4;
        source.hairColor = "#FFE89E";

        NpcCombatProfile received = new NpcCombatProfile();
        received.applyVisualOptions(source.visualOptionsTag());

        assertEquals("#FF5500", NpcAppearanceSource.auraOf(received));
        assertEquals(source.hairStyleId, received.hairStyleId);
        // Race and the hair tint ride the packet's own fields rather than the options tag, so only
        // the two values above can be compared across the trip; these are what the record reads.
        assertEquals(NpcAppearanceSource.of(source).hairId(), NpcAppearanceSource.of(received).hairId());
        assertEquals(NpcAppearanceSource.auraOf(source), NpcAppearanceSource.of(received).auraColor());
    }

    @Test void auraFallsBackToTheStoredRgbWhenNoHexWasChosen() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.auraColor = 0x123456;
        assertEquals("#123456", NpcAppearanceSource.auraOf(profile));
        profile.auraColorHex = "#FF5500";
        assertEquals("#FF5500", NpcAppearanceSource.auraOf(profile), "the explicit choice wins");
        profile.auraColorHex = "";
        profile.auraColor = 0;
        assertEquals("#FFFFFF", NpcAppearanceSource.auraOf(profile));
    }

    /**
     * The read half of the borrow, which is what a script's {@code getPlayerAppearance} shows. If the
     * look a character wears did not read back the same, the script would be shown a face that is not
     * the one on screen - and could not tell what it was about to overwrite.
     */
    @Test void aWrittenLookReadsBackFromTheCharacterItself() {
        Character target = new Character();
        NpcAppearanceMapping.apply(target, look());

        NpcAppearanceSource reported = NpcAppearanceSource.ofCharacter(target);
        assertEquals("saiyan", reported.race());
        assertEquals("female", reported.gender());
        assertEquals("martialartist", reported.characterClass());
        assertEquals(3, reported.bodyType());
        assertEquals(1.15f, reported.boobScale());
        assertEquals("#112233", reported.bodyColor());
        assertEquals("#FF5500", reported.auraColor());
        assertEquals("head_second", reported.activeHeadBone());
        assertTrue(reported.saiyanTail());
        assertFalse(reported.renderHairBase());
        assertEquals(0, reported.hairId());
        assertFalse(reported.hairSlot(0).isEmpty(), "hairId 0 means the custom strands, which are here");
        assertEquals("", reported.hairCode(), "DragonMineZ keeps a player's hair as strands, not a code");

        Character copy = new Character();
        NpcAppearanceMapping.apply(copy, reported);
        assertEquals(target.save(), copy.save(), "reading a look and writing it back changes nothing");
    }

    private static boolean hasStrands(CompoundTag hair) {
        for (String face : new String[] {"F", "B", "L", "R", "T"}) {
            if (hair.contains(face)) return true;
        }
        return false;
    }

    @Test void copiesAndRestoresTheFifthHairStyleWithoutAliasing() {
        var slot = com.dragonminez.common.hair.HairStyleSlot.SSJ4;
        Character original = new Character();
        original.setHairId(0);
        CustomHair fifth = new CustomHair();
        fifth.setName("Own SSJ4");
        fifth.getStrands(CustomHair.HairFace.TOP)[0].makeVisibleWithDefaults();
        original.setHairStyle(slot, fifth);
        var snapshot = net.bullettrain.xenopixelsmod.api.dmz.DmzAppearance.snapshot(original);

        Character target = new Character();
        NpcAppearanceMapping.apply(target, NpcAppearanceSource.ofCharacter(original));
        assertEquals(fifth.save(), target.getOwnHairStyle(slot).save());
        assertNotSame(fifth, target.getOwnHairStyle(slot));
        target.setHairStyle(slot, new CustomHair());
        assertTrue(net.bullettrain.xenopixelsmod.api.dmz.DmzAppearance.restore(target, snapshot));
        assertEquals(fifth.save(), target.getOwnHairStyle(slot).save());
    }

    private static NpcAppearanceSource look() {
        CustomHair[] hair = new CustomHair[4];
        for (int i = 0; i < hair.length; i++) {
            hair[i] = new CustomHair();
            hair[i].getStrands(CustomHair.HairFace.FRONT)[0].makeVisibleWithDefaults();
        }
        return new NpcAppearanceSource("saiyan", "female", "martialartist",
                3, 4, 2, 1, 5, 1.15f,
                "#112233", "#445566", "#778899", "#AABBCC", "#DDEEFF",
                "#FFE89E", "#FF5500", "head_second", true, false, 0, "", hair);
    }

    private static NpcAppearanceSource withBodyColor(String bodyColor) {
        NpcAppearanceSource base = look();
        return new NpcAppearanceSource(base.race(), base.gender(), base.characterClass(),
                base.bodyType(), base.eyesType(), base.noseType(), base.mouthType(),
                base.tattooType(), base.boobScale(), bodyColor, base.bodyColor2(),
                base.bodyColor3(), base.eye1Color(), base.eye2Color(), base.hairColor(),
                base.auraColor(), base.activeHeadBone(), base.saiyanTail(),
                base.renderHairBase(), base.hairId(), base.hairCode(), base.hair());
    }

    private static NpcAppearanceSource withHairCode(String hairCode) {
        NpcAppearanceSource base = look();
        return new NpcAppearanceSource(base.race(), base.gender(), base.characterClass(),
                base.bodyType(), base.eyesType(), base.noseType(), base.mouthType(),
                base.tattooType(), base.boobScale(), base.bodyColor(), base.bodyColor2(),
                base.bodyColor3(), base.eye1Color(), base.eye2Color(), base.hairColor(),
                base.auraColor(), base.activeHeadBone(), base.saiyanTail(),
                base.renderHairBase(), base.hairId(), hairCode, base.hair());
    }
}
