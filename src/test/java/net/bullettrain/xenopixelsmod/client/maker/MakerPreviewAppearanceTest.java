package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.dmz.form.MakerFormPreviewContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MakerPreviewAppearanceTest {
    @Test void temporaryRaceAndHairPreviewRestoresTransformedCharacter() {
        Character character = new Character();
        character.setRace("saiyan");
        character.setGender("female");
        character.setHairColor("#123456");
        character.setActiveForm("superforms", "supersaiyan");
        character.setActiveStackForm("kaioken", "kaiokenx2");
        boolean renderHair = character.isRenderHairBase();
        MakerPreviewAppearance preview = new MakerPreviewAppearance().race("namekian")
                .hairColor("#ffffff").activeForm(null, null);
        var snapshot = preview.snapshotCharacter(character);
        preview.applyCharacter(character);
        assertEquals("namekian", character.getRace());
        assertEquals("#ffffff", character.getHairColor());
        assertFalse(character.hasActiveForm());
        assertFalse(character.hasActiveStackForm());
        preview.restoreCharacter(character, snapshot);
        assertEquals("saiyan", character.getRace());
        assertEquals("female", character.getGender());
        assertEquals("#123456", character.getHairColor());
        assertEquals("supersaiyan", character.getActiveForm());
        assertEquals("kaiokenx2", character.getActiveStackForm());
        assertEquals(renderHair, character.isRenderHairBase());
    }

    @Test void formDraftIsScopedToOneCharacterAndRestoredAfterNestedFailure() {
        Character first = new Character();
        Character second = new Character();
        FormConfig.FormData outer = new FormConfig.FormData();
        FormConfig.FormData inner = new FormConfig.FormData();
        assertNull(MakerFormPreviewContext.get(first));
        MakerFormPreviewContext.draw(first, outer, () -> {
            assertSame(outer, MakerFormPreviewContext.get(first));
            assertNull(MakerFormPreviewContext.get(second));
            assertThrows(IllegalStateException.class, () -> MakerFormPreviewContext.draw(second, inner, () -> {
                assertNull(MakerFormPreviewContext.get(first));
                assertSame(inner, MakerFormPreviewContext.get(second));
                throw new IllegalStateException("renderer failed");
            }));
            assertSame(outer, MakerFormPreviewContext.get(first));
        });
        assertNull(MakerFormPreviewContext.get(first));
        assertNull(MakerFormPreviewContext.get(second));
    }
}
