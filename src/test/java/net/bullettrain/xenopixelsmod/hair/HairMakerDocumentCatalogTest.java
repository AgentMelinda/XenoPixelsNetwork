package net.bullettrain.xenopixelsmod.hair;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HairMakerDocumentCatalogTest {

    @Test
    void catalogJsonRoundTripsSelectedStrand() {
        HairMakerDocument original = HairMakerDocument.oneStrandDemo();
        original.name("Catalog Spike");
        original.globalColor("#123456");
        JsonObject json = original.toCatalogJson();
        assertEquals("Catalog Spike", json.get("name").getAsString());
        HairMakerDocument loaded = HairMakerDocument.fromCatalogJson(json);
        assertEquals("Catalog Spike", loaded.name());
        assertEquals("#123456", loaded.globalColor());
        assertEquals(4, loaded.selected().length());
        assertEquals("#ffaa00", loaded.selected().color());
        assertEquals(1.25f, loaded.selected().lengthScale(), 1e-5f);
    }

    @Test
    void fromCatalogJsonIgnoresConnectedFieldIfPresent() {
        HairMakerDocument original = HairMakerDocument.oneStrandDemo();
        JsonObject json = original.toCatalogJson();
        json.addProperty("connected", true);
        HairMakerDocument loaded = HairMakerDocument.fromCatalogJson(json);
        assertTrue(loaded.visibleCount() >= 1);
        assertEquals(4, loaded.selected().length());
    }

    @Test
    void projectJsonRoundTripKeepsStyle() {
        HairMakerDocument original = HairMakerDocument.oneStrandDemo();
        JsonObject project = JsonParser.parseString(original.toExportJson("2026-10-04"))
                .getAsJsonObject()
                .getAsJsonObject("project");
        HairMakerDocument loaded = HairMakerDocument.fromProjectJson(project);
        assertEquals("Base", loaded.style());
        assertEquals(4, loaded.selected().length());
    }
}
