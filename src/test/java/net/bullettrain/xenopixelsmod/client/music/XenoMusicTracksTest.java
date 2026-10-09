package net.bullettrain.xenopixelsmod.client.music;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class XenoMusicTracksTest {
    @Test void defaultsAreDragonMineZsOwnThirtyNineMenuTracks() {
        var defaults = XenoMusicTracks.defaults();
        assertEquals(39, defaults.size());
        assertEquals("dragonminez:menu_music_1", defaults.getFirst());
        assertEquals("dragonminez:menu_music_39", defaults.getLast());
        assertEquals("DMZ Menu 7", XenoMusicTracks.displayName("dragonminez:menu_music_7"));
        assertEquals("my_song", XenoMusicTracks.displayName("mypack:music/my_song"));
        assertEquals("Vegeta's Sacrifice", XenoMusicTracks.displayName("dragonminez:menu_music_33"));
    }

    @Test void everyDefaultResolvesToAStreamedAudioAssetInThePinnedDependency() throws Exception {
        var project = java.nio.file.Path.of(System.getProperty("xenopixels.projectDir"));
        try (var jar = new java.util.zip.ZipFile(project.resolve("libs/dragonminez-2.1.3.jar").toFile())) {
            var entry = jar.getEntry("assets/dragonminez/sounds.json");
            assertNotNull(entry);
            try (var reader = new java.io.InputStreamReader(jar.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
                var sounds = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                for (String id : XenoMusicTracks.defaults()) {
                    String event = id.substring(id.indexOf(':') + 1);
                    assertTrue(sounds.has(event), id);
                    var sound = sounds.getAsJsonObject(event).getAsJsonArray("sounds").get(0).getAsJsonObject();
                    assertTrue(sound.get("stream").getAsBoolean(), id);
                    String audio = sound.get("name").getAsString().substring("dragonminez:".length());
                    assertNotNull(jar.getEntry("assets/dragonminez/sounds/" + audio + ".ogg"), id);
                }
            }
        }
    }

    @Test void sanitizeDropsJunkAndDuplicatesAndFallsBackToDefaults() {
        assertEquals(XenoMusicTracks.defaults(), XenoMusicTracks.sanitize(null));
        assertEquals(XenoMusicTracks.defaults(), XenoMusicTracks.sanitize(List.of()));
        assertEquals(XenoMusicTracks.defaults(), XenoMusicTracks.sanitize(Arrays.asList("", "  ", "Bad Id!", null)));
        assertEquals(List.of("dragonminez:menu_music_3", "mypack:song"),
                XenoMusicTracks.sanitize(Arrays.asList(" dragonminez:menu_music_3 ", "MYPACK:SONG", "dragonminez:menu_music_3")));
    }

    @Test void steppingWrapsBothWays() {
        assertEquals(0, XenoMusicTracks.step(38, 1, 39));
        assertEquals(38, XenoMusicTracks.step(0, -1, 39));
        assertEquals(0, XenoMusicTracks.step(5, 1, 0));
    }

    @Test void pausePlateButtonsAreWhereTheChipsAreDrawn() {
        assertEquals(0, XenoMusicPauseWidget.hitIndex(7, 20), "previous");
        assertEquals(1, XenoMusicPauseWidget.hitIndex(30, 25), "play/pause");
        assertEquals(2, XenoMusicPauseWidget.hitIndex(69, 36), "next");
        assertEquals(3, XenoMusicPauseWidget.hitIndex(100, 30), "power, 32 wide");
        assertEquals(-1, XenoMusicPauseWidget.hitIndex(26, 25), "gap between chips");
        assertEquals(-1, XenoMusicPauseWidget.hitIndex(30, 10), "title line");
        assertEquals(-1, XenoMusicPauseWidget.hitIndex(120, 25), "empty plate area");
    }
}
