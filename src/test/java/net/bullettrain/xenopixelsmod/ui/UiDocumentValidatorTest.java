package net.bullettrain.xenopixelsmod.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentValidatorTest {

    @Test
    void demoShapedTreeIsValid() {
        UiDocument document = demo();
        assertTrue(UiDocumentValidator.validate(document).isEmpty());
    }

    @Test
    void versionTypeIdAndBindingFail() {
        UiDocument document = demo();
        document.version = 99;
        document.root.children.getFirst().id = "root";
        document.root.children.get(1).type = "MINIMAP";
        document.root.children.get(2).bind = "quest.title";
        List<String> errors = UiDocumentValidator.validate(document);
        assertTrue(errors.stream().anyMatch(e -> e.contains("unsupported version")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("duplicate id")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown type")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown binding")));
    }

    @Test
    void cycleIsReported() {
        UiDocument document = demo();
        document.root.children.add(document.root);
        List<String> errors = UiDocumentValidator.validate(document);
        assertTrue(errors.stream().anyMatch(e -> e.contains("cyclic parent")));
    }

    @Test
    void emptyJsonIsRejected() {
        assertFalse(UiDocumentIO.fromJson("").ok());
        assertFalse(UiDocumentIO.fromJson("{").ok());
        assertFalse(UiDocumentIO.fromJson("{\"version\":2,\"id\":\"x\",\"kind\":\"hud\"}").ok());
    }

    @Test
    void bundledDemoDocumentsValidate() throws Exception {
        assertTrue(loadBundled("demo_hud.json").ok());
        assertTrue(loadBundled("demo_screen.json").ok());
        assertTrue(loadBundled("dmz_menu_blank.json").ok());
        assertEquals("dmz_menu", loadBundled("dmz_menu_blank.json").document().kind);
        assertTrue(loadBundled("dmz_hud_blank.json").ok());
        assertEquals("hud", loadBundled("dmz_hud_blank.json").document().kind);
        assertTrue("screen".equals(loadBundled("demo_screen.json").document().kind));
        assertTrue("open_document:demo_screen".equals(
                loadBundled("demo_hud.json").document().root.children.stream()
                        .filter(n -> "open".equals(n.id))
                        .findFirst()
                        .orElseThrow()
                        .action));
    }

    private static UiDocumentIO.UiLoadResult loadBundled(String name) throws Exception {
        try (var in = UiDocumentValidatorTest.class.getResourceAsStream(
                "/assets/xenopixelsmod/ui/" + name)) {
            assertTrue(in != null, "missing bundled " + name);
            return UiDocumentIO.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void section28TypesAndSnapshotKeysValidate() {
        UiDocument document = demo();
        UiNode face = child("face", "PORTRAIT", "");
        UiNode icon = child("icon", "ICON", "");
        UiNode scroll = child("scroll", "SCROLL", "");
        UiNode ki = child("ki", "PROGRESS_BAR", "player.kiPercent");
        UiNode food = child("food", "TEXT", "player.food");
        document.root.children.add(face);
        document.root.children.add(icon);
        document.root.children.add(scroll);
        document.root.children.add(ki);
        document.root.children.add(food);
        assertTrue(UiDocumentValidator.validate(document).isEmpty());
        assertTrue(UiBindings.isKnown("player.releasePercent"));
        assertTrue(UiBindings.isKnown("player.xpLevel"));
        assertTrue(UiBindings.isKnown("player.gamemode"));
        assertFalse(UiBindings.isKnown("quest.title"));
    }

    @Test
    void roundTripKeepsBind() {
        String json = UiDocumentIO.toJson(demo());
        UiDocumentIO.UiLoadResult loaded = UiDocumentIO.fromJson(json);
        assertTrue(loaded.ok());
        assertTrue("player.hpPercent".equals(loaded.document().root.children.get(2).bind));
    }

    static UiDocument demo() {
        UiDocument document = new UiDocument();
        document.id = "demo_hud";
        document.kind = "hud";
        document.root.id = "root";
        document.root.type = "ROOT";
        document.root.w = 280;
        document.root.h = 92;
        UiNode name = child("name", "TEXT", "player.name");
        UiNode level = child("level", "TEXT", "player.level");
        UiNode bar = child("hp", "PROGRESS_BAR", "player.hpPercent");
        document.root.children.add(name);
        document.root.children.add(level);
        document.root.children.add(bar);
        return document;
    }

    private static UiNode child(String id, String type, String bind) {
        UiNode node = new UiNode();
        node.id = id;
        node.type = type;
        node.bind = bind;
        node.w = 80;
        node.h = 12;
        return node;
    }
}
