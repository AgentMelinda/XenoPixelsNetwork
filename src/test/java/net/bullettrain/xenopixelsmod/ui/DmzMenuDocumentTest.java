package net.bullettrain.xenopixelsmod.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzMenuDocumentTest {

    @Test
    void bundledStatsTemplateValidates() throws Exception {
        try (var in = DmzMenuDocumentTest.class.getResourceAsStream(
                "/assets/xenopixelsmod/ui/xeno_stats.json")) {
            assertTrue(in != null, "missing bundled xeno_stats.json");
            var loaded = UiDocumentIO.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(loaded.ok(), loaded.errors()::toString);
            assertEquals("dmz_menu", loaded.document().kind);
            assertEquals("stats", loaded.document().page);
        }
    }

    @Test
    void demoStatsMenuValidates() {
        UiDocument document = statsMenu();
        List<String> errors = UiDocumentValidator.validate(document);
        assertTrue(errors.isEmpty(), errors::toString);
    }

    @Test
    void unknownBindFails() {
        UiDocument document = statsMenu();
        document.root.children.getFirst().bind = "quest.title";
        List<String> errors = UiDocumentValidator.validate(document);
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown binding")));
    }

    @Test
    void missingPageAssignmentReturnsNull() {
        DmzMenuAssignments assignments = new DmzMenuAssignments();
        assertNull(assignments.documentId(DmzMenuPage.STATS));
        assertNull(assignments.documentId(DmzMenuPage.SKILLS));
    }

    @Test
    void assignmentLookupReturnsMappedDocument() {
        DmzMenuAssignments assignments = new DmzMenuAssignments();
        assignments.set(DmzMenuPage.STATS, "xeno_stats");
        assertEquals("xeno_stats", assignments.documentId(DmzMenuPage.STATS));
        assertNull(assignments.documentId(DmzMenuPage.SKILLS));
    }

    @Test
    void screenClassMapsToVPage() {
        assertEquals(DmzMenuPage.STATS, DmzMenuPage.fromDmzScreenClass(
                "com.dragonminez.client.gui.character.CharacterStatsScreen"));
        assertEquals(DmzMenuPage.SKILLS, DmzMenuPage.fromDmzScreenClass(
                "com.dragonminez.client.gui.character.SkillsMenuScreen"));
        assertNull(DmzMenuPage.fromDmzScreenClass("net.minecraft.client.gui.screens.TitleScreen"));
    }

    @Test
    void navTabFiresClickActionLikeButton() {
        assertTrue(UiNodeType.BUTTON.firesClickAction());
        assertTrue(UiNodeType.NAV_TAB.firesClickAction());
        assertFalse(UiNodeType.STAT_ROW.firesClickAction());
        assertFalse(UiNodeType.SKILL_SLOT.firesClickAction());
    }

    @Test
    void dmzPageAndCloseActionsAreLegal() {
        UiDocument document = statsMenu();
        UiNode tab = new UiNode();
        tab.id = "tab_skills";
        tab.type = "NAV_TAB";
        tab.action = "dmz_page:skills";
        tab.w = 40;
        tab.h = 16;
        document.root.children.add(tab);
        UiNode close = new UiNode();
        close.id = "close";
        close.type = "BUTTON";
        close.action = "close";
        close.w = 16;
        close.h = 16;
        document.root.children.add(close);
        assertTrue(UiDocumentValidator.validate(document).isEmpty());
    }

    static UiDocument statsMenu() {
        UiDocument document = new UiDocument();
        document.id = "xeno_stats";
        document.kind = "dmz_menu";
        document.page = "stats";
        document.root.id = "root";
        document.root.type = "ROOT";
        document.root.w = 320;
        document.root.h = 180;
        UiNode row = new UiNode();
        row.id = "str";
        row.type = "STAT_ROW";
        row.bind = "player.level";
        row.text = "STR";
        row.w = 120;
        row.h = 14;
        document.root.children.add(row);
        UiNode slot = new UiNode();
        slot.id = "slot0";
        slot.type = "SKILL_SLOT";
        slot.w = 24;
        slot.h = 24;
        document.root.children.add(slot);
        return document;
    }
}
