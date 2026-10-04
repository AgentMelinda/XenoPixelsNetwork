package net.bullettrain.xenopixelsmod.npc;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatBrainVersion;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoNpcDataTest {
    @Test
    void everyInitialRoleHasStableId() {
        assertEquals(8, XenoNpcRole.values().length);
        assertEquals(XenoNpcRole.HUMANOID, XenoNpcRole.byId("humanoid"));
        assertEquals(XenoNpcRole.CREATURE, XenoNpcRole.byId("creature"));
        assertEquals(XenoNpcRole.TRADER, XenoNpcRole.byId("trader"));
        assertEquals(XenoNpcRole.GUARD, XenoNpcRole.byId("guard"));
        assertEquals(XenoNpcRole.COMPANION, XenoNpcRole.byId("companion"));
        assertEquals(XenoNpcRole.QUEST, XenoNpcRole.byId("quest"));
        assertEquals(XenoNpcRole.TRANSPORTER, XenoNpcRole.byId("transporter"));
        assertEquals(XenoNpcRole.BANK, XenoNpcRole.byId("bank"));
    }

    @Test
    void nativeDataRoundTripsV5AndImportProvenance() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.GUARD);
        data.setDisplayName("Nail");
        data.setOwner(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
        data.setFaction("namek");
        data.setImportSource("mynpcs", UUID.fromString("00000000-0000-0000-0000-0000000000bb"));
        XenoNpcData loaded = XenoNpcData.fromTag(data.toTag(), XenoNpcRole.HUMANOID);
        assertEquals(XenoNpcRole.GUARD, loaded.role());
        assertEquals(NpcCombatBrainVersion.V5, loaded.brainVersion());
        assertEquals("Nail", loaded.displayName());
        assertEquals("mynpcs", loaded.sourceMod());
        assertTrue(loaded.revision() > 0);
    }

    @Test
    void missingStoredRoleKeepsEntityTypeFallback() {
        XenoNpcData loaded = XenoNpcData.fromTag(new CompoundTag(), XenoNpcRole.TRADER);
        assertEquals(XenoNpcRole.TRADER, loaded.role());
    }

    @Test
    void editorIdentityMutationBumpsTheRevisionExactlyOnce() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        int before = data.revision();

        assertTrue(data.applyEditorIdentity("Bulma", "Engineer", "capsule_corp"));
        data.markEdited();

        assertEquals(before + 1, data.revision());
        assertEquals("Bulma", data.displayName());
        assertEquals("Engineer", data.title());
        assertEquals("capsule_corp", data.faction());
    }

    @Test
    void unchangedEditorIdentityDoesNotPretendToMutate() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.applyEditorIdentity("Xeno NPC", "", "");
        assertFalse(data.applyEditorIdentity("Xeno NPC", "", ""));
    }

    @Test
    void renamingToTheSameNameChangesNothing() {
        // No change is not an edit. The caller only bumps the revision when this returns true, so
        // a save that changed nothing must not invalidate an editor that is still open and correct.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.applyEditorIdentity("Shopkeeper", "", "");
        int before = data.revision();
        assertFalse(data.applyEditorIdentity("Shopkeeper", "", ""));
        assertEquals(before, data.revision());
    }


    @Test
    void everyRoleHasItsOwnNameForAFreshlyPlacedNpc() {
        // The wand named every NPC it placed the literal "Xeno NPC", whatever role was selected,
        // so a row of them could not be told apart in the world without opening each one. It now
        // uses role.label(), which is what the shift-cycle message already prints - this holds the
        // labels distinct so that fix keeps meaning something.
        java.util.Set<String> labels = new java.util.HashSet<>();
        for (XenoNpcRole role : XenoNpcRole.values()) {
            assertFalse(role.label().isBlank(), role + " has no label");
            assertTrue(labels.add(role.label()), role + " shares a label with another role");
        }
        assertEquals(XenoNpcRole.values().length, labels.size());
    }

    @Test
    void aBlankNameFallsBackRatherThanLeavingTheNpcNameless() {
        // An operator who clears the field gets the default back, not an invisible nameplate.
        XenoNpcData data = new XenoNpcData(XenoNpcRole.TRADER);
        data.applyEditorIdentity("   ", "", "");
        assertEquals("Xeno NPC", data.displayName());
    }

    /** 2026-09-28: colour codes live in the stored text; chat, bubbles and lists get plain text. */
    @Test
    void colouredNamesAndTitlesKeepTheirCodesButReadPlain() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        assertTrue(data.applyEditorIdentity("&6Goku", "&#FF8800Earth's Hero", ""));
        assertEquals("Goku", data.displayName());
        assertEquals("Earth's Hero", data.title());
        assertEquals("&6Goku", data.rawDisplayName());
        assertEquals("&#FF8800Earth's Hero", data.rawTitle());
        assertFalse(data.applyEditorIdentity("&6Goku", "&#FF8800Earth's Hero", ""), "same codes: unchanged");
        XenoNpcData loaded = XenoNpcData.fromTag(data.toTag(), XenoNpcRole.HUMANOID);
        assertEquals("&6Goku", loaded.rawDisplayName());
        assertEquals("&#FF8800Earth's Hero", loaded.rawTitle());
    }

    @Test
    void aNameThatIsOnlyCodesStillReadsAsSomething() {
        XenoNpcData data = new XenoNpcData(XenoNpcRole.HUMANOID);
        data.setDisplayName("&6&l");
        assertEquals("Xeno NPC", data.displayName());
    }
}
