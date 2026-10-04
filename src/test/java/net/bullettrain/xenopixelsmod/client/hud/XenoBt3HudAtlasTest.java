package net.bullettrain.xenopixelsmod.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the generated BT3 atlas constants still describe the atlas that is on disk.
 *
 * <p>{@code tools/gen_bt3_hud_atlas.py} writes the PNG, the manifest and
 * {@link XenoBt3HudAtlas} from one pass, so they agree when they are generated. What this guards is
 * the gap afterwards: the Java is an ordinary source file, and a hand-edited rectangle does not
 * fail — it renders a neighbouring sprite, which is a bug you have to notice by eye. The manifest
 * is the independent copy to check it against.
 */
class XenoBt3HudAtlasTest {

    private static final Path MANIFEST = RepoRoot.of("tools/generated/xeno_bt3_hud_atlas.json");
    private static final Path ATLAS_PNG = RepoRoot.of(
            "src/main/resources/assets/xenopixelsmod/textures/gui/xeno_bt3_hud_atlas.png");

    private static JsonObject manifest() throws IOException {
        assertTrue(Files.exists(MANIFEST),
                "the BT3 atlas manifest is missing; re-run tools/gen_bt3_hud_atlas.py");
        return JsonParser.parseString(Files.readString(MANIFEST)).getAsJsonObject();
    }

    /** The sprite constants, by the name the manifest uses for them. */
    private static Map<String, XenoBt3HudAtlas.Sprite> javaSprites() {
        Map<String, XenoBt3HudAtlas.Sprite> out = new java.util.HashMap<>();
        for (var field : XenoBt3HudAtlas.class.getDeclaredFields()) {
            if (field.getType() != XenoBt3HudAtlas.Sprite.class) continue;
            try {
                out.put(field.getName(), (XenoBt3HudAtlas.Sprite) field.get(null));
            } catch (IllegalAccessException e) {
                throw new AssertionError("could not read " + field.getName(), e);
            }
        }
        return out;
    }

    @Test
    void theRuntimeTextureExists() {
        assertTrue(Files.exists(ATLAS_PNG),
                "the atlas the renderer blits is not in resources; re-run the generator");
    }

    @Test
    void everySpriteInTheManifestHasTheSameRectangleInJava() throws IOException {
        JsonObject sprites = manifest().getAsJsonObject("sprites");
        Map<String, XenoBt3HudAtlas.Sprite> java = javaSprites();
        assertEquals(sprites.size(), java.size(),
                "the manifest and the generated Java disagree about how many sprites there are");
        for (String name : sprites.keySet()) {
            JsonObject entry = sprites.getAsJsonObject(name);
            XenoBt3HudAtlas.Sprite sprite = java.get(name);
            assertNotNull(sprite, name + " is in the manifest but not in XenoBt3HudAtlas");
            assertEquals(entry.get("x").getAsInt(), sprite.u(), name + " u");
            assertEquals(entry.get("y").getAsInt(), sprite.v(), name + " v");
            assertEquals(entry.get("width").getAsInt(), sprite.width(), name + " width");
            assertEquals(entry.get("height").getAsInt(), sprite.height(), name + " height");
        }
    }

    @Test
    void noSpriteIsEmptyOrRunsOffTheAtlas() {
        for (var entry : javaSprites().entrySet()) {
            XenoBt3HudAtlas.Sprite s = entry.getValue();
            assertTrue(s.width() > 0 && s.height() > 0, entry.getKey() + " is empty");
            assertTrue(s.u() >= 0 && s.v() >= 0, entry.getKey() + " starts outside the atlas");
            assertTrue(s.u() + s.width() <= XenoBt3HudAtlas.ATLAS_WIDTH,
                    entry.getKey() + " runs off the right edge");
            assertTrue(s.v() + s.height() <= XenoBt3HudAtlas.ATLAS_HEIGHT,
                    entry.getKey() + " runs off the bottom edge");
        }
    }

    @Test
    void noTwoSpritesOverlap() {
        List<Map.Entry<String, XenoBt3HudAtlas.Sprite>> all =
                new ArrayList<>(javaSprites().entrySet());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                XenoBt3HudAtlas.Sprite a = all.get(i).getValue();
                XenoBt3HudAtlas.Sprite b = all.get(j).getValue();
                boolean overlap = a.u() < b.u() + b.width() && b.u() < a.u() + a.width()
                        && a.v() < b.v() + b.height() && b.v() < a.v() + a.height();
                assertFalse(overlap,
                        all.get(i).getKey() + " overlaps " + all.get(j).getKey()
                                + "; one of them would render the other's pixels");
            }
        }
    }

    @Test
    void theMeasuredGeometryMatchesTheManifestToo() throws IOException {
        JsonObject geometry = manifest().getAsJsonObject("geometry");
        assertNotNull(geometry, "the manifest carries no geometry; re-run the generator");

        JsonObject portrait = geometry.getAsJsonObject("PORTRAIT_WELL");
        assertEquals(portrait.get("x").getAsInt(), XenoBt3HudAtlas.PORTRAIT_WELL.x());
        assertEquals(portrait.get("width").getAsInt(), XenoBt3HudAtlas.PORTRAIT_WELL.width());

        JsonObject nameplate = geometry.getAsJsonObject("NAMEPLATE_WELL");
        assertEquals(nameplate.get("y").getAsInt(), XenoBt3HudAtlas.NAMEPLATE_WELL.y());
        assertEquals(nameplate.get("height").getAsInt(), XenoBt3HudAtlas.NAMEPLATE_WELL.height());

        JsonObject hp = geometry.getAsJsonObject("HP_TRACK");
        assertEquals(hp.get("x").getAsInt(), XenoBt3HudAtlas.HP_TRACK_X);
        assertEquals(hp.get("width").getAsInt(), XenoBt3HudAtlas.HP_TRACK_WIDTH);

        assertTrackMatches(geometry.getAsJsonObject("KI_TRACK"),
                XenoBt3HudAtlas.kiSegmentCount(), XenoBt3HudAtlas::kiSegmentStart,
                XenoBt3HudAtlas.KI_SEGMENT_WIDTH, "ki");
        assertTrackMatches(geometry.getAsJsonObject("STAMINA_TRACK"),
                XenoBt3HudAtlas.staminaSegmentCount(), XenoBt3HudAtlas::staminaSegmentStart,
                XenoBt3HudAtlas.STAMINA_SEGMENT_WIDTH, "stamina");
    }

    private static void assertTrackMatches(JsonObject track, int count,
                                           java.util.function.IntUnaryOperator start,
                                           int width, String what) {
        var starts = track.getAsJsonArray("starts");
        assertEquals(starts.size(), count, what + " segment count");
        for (int i = 0; i < count; i++) {
            assertEquals(starts.get(i).getAsInt(), start.applyAsInt(i),
                    what + " segment " + i + " start");
        }
        assertEquals(track.get("width").getAsInt(), width, what + " segment width");
    }
}
