package net.bullettrain.xenopixelsmod.client.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase M "schema complete" invariants: every persisted profile field either has a consumer or is
 * labelled honestly as unbuilt in the editor.
 *
 * <p>These are content tests, like the other wiring tests in this suite, because the thing being
 * protected is a sentence on screen: a row that claims an effect nothing reads is the bug, and a
 * future edit that quietly deletes the honest caveat re-opens it.
 */
class EditorSchemaHonestyTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void capeOverlayAndLayerRowsAreLiveBecauseTheRendererReadsThem() throws IOException {
        // These rows were honest placeholders until 2026-09-27. Each now has a key, a save-policy
        // entry and a renderer consumer; this pins all three so none can be dropped alone.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        for (String key : new String[] {"DisplayCape", "DisplayOverlay", "DisplayOverlayGlow",
                "DisplayOuterLayers"}) {
            assertTrue(editor.contains("\"" + key + "\""), "editor row writes " + key);
            assertTrue(source("network/packet/XenoNpcSavePolicy.java").contains("\"" + key + "\""),
                    "save policy accepts " + key);
        }
        assertFalse(editor.contains("disabledAction(\"Cape\")"), "Cape is no longer a placeholder");
        assertTrue(source("client/npc/NpcCapeLayer.java").contains("profile.displayCape"));
        assertTrue(source("client/npc/NpcOverlayLayer.java").contains("profile.displayOverlay"));
        assertTrue(source("client/npc/XenoNpcRenderer.java").contains("profile.displayOuterLayers"));
        String profile = source("compat/npc/NpcCombatProfile.java");
        assertTrue(profile.contains("tag.putString(TAG_DISPLAY_CAPE"),
                "the cape must travel in the visual options, or tracking clients never see it");
    }

    @Test
    void theCreatureTypeRowSaysOutLoudThatNothingReadsIt() throws IOException {
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("cycle(\"Creature Type\""),
                "the parity row stays live so authored data survives");
        assertTrue(editor.contains("behaviour layer reads it yet"),
                "but the page must not claim the value does something");
    }

    @Test
    void theBrainAimRowPointsAtTheAccuracyFieldThatActuallyFires() throws IOException {
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("Aim is stored but nothing reads it yet"),
                "the dead aimAccuracy row must say it is dead");
        assertTrue(editor.contains("profile.npcRangedAccuracy"),
                "and the live one (Ranged Props > Accuracy) must stay wired");

        String dispatcher = source("compat/npc/NpcKiAttackDispatcher.java");
        assertTrue(dispatcher.contains("npcRangedAccuracy"),
                "the ki-blast lead blends by the field the editor points at");
        assertFalse(dispatcher.contains(".aimAccuracy"),
                "and not by aimAccuracy, which has no consumer");

        for (String twin : new String[] {"client/compat/npc/gui/GuiNpcDmzBrain.java",
                "client/compat/npc/mynpcs/gui/GuiNpcDmzBrain.java"}) {
            assertTrue(source(twin).contains("\"Aim*\""),
                    twin + " must mark the dead Aim field the same way");
        }
    }

    @Test
    void theInventoryPageAdmitsGeckoGearIsInvisible() throws IOException {
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("entity and full-DMZ NPCs wear it without showing it"),
                "the armor/held-item gap on the GeckoLib path must stay stated");
    }

    @Test
    void everyBankEconomyFieldHasAConsumer() throws IOException {
        String service = source("npc/bank/NpcBankService.java");
        assertTrue(service.contains("withdrawFeePercent"), "the fee row is charged, not cosmetic");
        assertTrue(service.contains("startSlots"), "the tab start row sizes the vault");
        assertTrue(service.contains("upgradable"), "the upgrade row gates the purchase");
    }
}
