package net.bullettrain.xenopixelsmod.client.music;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The track list of the pause-menu music player; resource ids are validated before playback.
 *
 * <p>Tracks are sound-event ids, resolved by the client's sound manager at play time, so any
 * resource pack can add or replace entries through the client config. The default list is the
     * owner's seven locally converted Dragon Ball Z tracks, followed by DragonMineZ's
     * {@code menu_music_1} to {@code menu_music_39}; all entries stream from local resources.
 */
public final class XenoMusicTracks {
    /** DragonMineZ registers exactly this many menu tracks ({@code MainSounds.MENU_MUSIC_1..39}). */
    public static final int DMZ_MENU_TRACKS = 39;
    private static final String DMZ_PREFIX = "dragonminez:menu_music_";
    public static final String MAIN_MENU_TRACK = "xenopixelsmod:music.dbz_003";

    private XenoMusicTracks() {}

    public static List<String> defaults() {
        List<String> out = new ArrayList<>(DMZ_MENU_TRACKS + 7);
        for (int i : new int[]{3, 1, 7, 11, 19, 25, 37}) out.add("xenopixelsmod:music.dbz_%03d".formatted(i));
        for (int i = 1; i <= DMZ_MENU_TRACKS; i++) out.add(DMZ_PREFIX + i);
        return List.copyOf(out);
    }
    public record Selection(List<String> tracks, int index) {}
    /** Upgrade only the exact old stock playlist; custom lists retain their selected id. */
    public static Selection migrate(List<String> raw, Integer index) {
        if (raw == null || raw.isEmpty()) return new Selection(defaults(), 0);
        List<String> tracks = sanitize(raw);
        List<String> legacy = new ArrayList<>();
        for (int i = 1; i <= DMZ_MENU_TRACKS; i++) legacy.add(DMZ_PREFIX + i);
        int selected = index == null ? 0 : Math.floorMod(index, tracks.size());
        if (tracks.equals(legacy)) {
            // Stock first entry was the untouched default; other selections retain their id.
            return new Selection(defaults(), selected == 0 ? 0 : defaults().indexOf(tracks.get(selected)));
        }
        return new Selection(tracks, selected);
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
        if (id.matches("xenopixelsmod:music\\.dbz_[0-9]{3}"))
            return "Dragon Ball Z Soundtrack " + Integer.parseInt(id.substring(id.lastIndexOf('_') + 1));
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
