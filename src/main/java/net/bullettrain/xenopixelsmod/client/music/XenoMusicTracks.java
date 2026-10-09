package net.bullettrain.xenopixelsmod.client.music;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The track list of the pause-menu music player; resource ids are validated before playback.
 *
 * <p>Tracks are sound-event ids, resolved by the client's sound manager at play time, so any
 * resource pack can add or replace entries through the client config. The default list is the
 * music DragonMineZ 2.1.3 itself ships and plays on its title screen: {@code menu_music_1} to
 * {@code menu_music_39} (verified in the pinned jar's {@code assets/dragonminez/sounds.json},
 * each {@code stream: true}). No audio is invented, downloaded or extracted by this mod.
 */
public final class XenoMusicTracks {
    /** DragonMineZ registers exactly this many menu tracks ({@code MainSounds.MENU_MUSIC_1..39}). */
    public static final int DMZ_MENU_TRACKS = 39;
    private static final String DMZ_PREFIX = "dragonminez:menu_music_";

    private XenoMusicTracks() {}

    public static List<String> defaults() {
        List<String> out = new ArrayList<>(DMZ_MENU_TRACKS);
        for (int i = 1; i <= DMZ_MENU_TRACKS; i++) out.add(DMZ_PREFIX + i);
        return List.copyOf(out);
    }

    /** Drops blanks, duplicates and ids that are not valid resource locations; empty falls back to defaults. */
    public static List<String> sanitize(List<String> raw) {
        if (raw == null) return defaults();
        List<String> out = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null) continue;
            String id = entry.trim().toLowerCase(java.util.Locale.ROOT);
            if (id.isEmpty() || ResourceLocation.tryParse(id) == null || out.contains(id)) continue;
            out.add(id);
        }
        return out.isEmpty() ? defaults() : List.copyOf(out);
    }

    /** Short label for the HUD: DragonMineZ menu tracks read as "DMZ Menu 7", others as their path. */
    public static String displayName(String id) {
        if (id == null) return "";
        if (id.equals(DMZ_PREFIX + "31")) return "Call for a Miracle";
        if (id.equals(DMZ_PREFIX + "32")) return "Goku's Father-Son Victory";
        if (id.equals(DMZ_PREFIX + "33")) return "Vegeta's Sacrifice";
        if (id.startsWith(DMZ_PREFIX)) return "DMZ Menu " + id.substring(DMZ_PREFIX.length());
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /** Wraps in both directions; a non-positive size yields zero. */
    public static int step(int current, int delta, int size) {
        if (size <= 0) return 0;
        return Math.floorMod(current + delta, size);
    }
}
