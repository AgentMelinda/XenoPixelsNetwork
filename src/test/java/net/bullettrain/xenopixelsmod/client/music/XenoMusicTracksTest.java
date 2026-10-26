package net.bullettrain.xenopixelsmod.client.music;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class XenoMusicTracksTest {
    @Test void ownerSoundtrackThreeIsDefaultAndDmzTracksRemainSelectable() {
        var defaults = XenoMusicTracks.defaults();
        assertEquals(46, defaults.size());
        assertEquals("xenopixelsmod:music.dbz_003", defaults.getFirst());
        assertTrue(defaults.contains("dragonminez:menu_music_1"));
        assertEquals("dragonminez:menu_music_39", defaults.getLast());
        assertEquals("DMZ Menu 7", XenoMusicTracks.displayName("dragonminez:menu_music_7"));
        assertEquals("my_song", XenoMusicTracks.displayName("mypack:music/my_song"));
        assertEquals("Vegeta's Sacrifice", XenoMusicTracks.displayName("dragonminez:menu_music_33"));
    }
    @Test void customPlaylistsAndLegacyManualSelectionsSurviveMigration() {
        var custom = List.of("pack:a", "pack:b");
        var result = XenoMusicTracks.migrate(custom, 1);
        assertEquals(custom, result.tracks()); assertEquals(1, result.index());
        var legacy = java.util.stream.IntStream.rangeClosed(1, 39).mapToObj(i -> "dragonminez:menu_music_" + i).toList();
        result = XenoMusicTracks.migrate(legacy, 15);
        assertEquals("dragonminez:menu_music_16", result.tracks().get(result.index()));
        assertEquals(0, XenoMusicTracks.migrate(legacy, 0).index());
    }

    @Test void everyDefaultResolvesToAStreamedAudioAssetInThePinnedDependency() throws Exception {
        var project = java.nio.file.Path.of(System.getProperty("xenopixels.projectDir"));
        try (var jar = new java.util.zip.ZipFile(project.resolve("libs/dragonminez-2.2.jar").toFile())) {
            var entry = jar.getEntry("assets/dragonminez/sounds.json");
            assertNotNull(entry);
            try (var reader = new java.io.InputStreamReader(jar.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
                var sounds = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                for (String id : XenoMusicTracks.defaults()) {
                    if (!id.startsWith("dragonminez:")) continue;
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
    @Test void localDramaticTracksResolveToStreamedVorbisAssets() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("xenopixels.projectDir"))
                .resolve("src/main/resources/assets/xenopixelsmod");
        var sounds = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("sounds.json"))).getAsJsonObject();
        for (String id : XenoMusicTracks.defaults()) {
            if (!id.startsWith("xenopixelsmod:")) continue;
            var sound = sounds.getAsJsonObject(id.substring(id.indexOf(':') + 1)).getAsJsonArray("sounds").get(0).getAsJsonObject();
            assertTrue(sound.get("stream").getAsBoolean(), id);
            var file = root.resolve("sounds/" + sound.get("name").getAsString().substring("xenopixelsmod:".length()) + ".ogg");
            var bytes = java.nio.file.Files.readAllBytes(file);
            assertEquals("OggS", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII), id);
            assertTrue(bytes.length > 10000, id);
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
        assertEquals(4, XenoMusicPauseWidget.hitIndex(120, 25), "independent cinematic toggle");
        assertEquals(-1, XenoMusicPauseWidget.hitIndex(110, 25), "gap before cinematic toggle");
        assertEquals(-1, XenoMusicPauseWidget.hitIndex(144, 25), "right plate margin");
    }
}
