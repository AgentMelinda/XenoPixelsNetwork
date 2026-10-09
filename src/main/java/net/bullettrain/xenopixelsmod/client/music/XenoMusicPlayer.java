package net.bullettrain.xenopixelsmod.client.music;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side background music: one streamed track at a time from {@link XenoClientConfig#musicTracks},
 * advancing to the next when it ends, while vanilla's own music manager is kept quiet.
 *
 * <p>Playback continues with the pause menu closed; only the controls are limited to the pause
 * screen ({@link XenoMusicPauseWidget}). Every setting is client-only and saved in the client
 * config; the server has no say in it.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoMusicPlayer {
    private static SoundInstance current;
    private static String currentId;
    /** Ticks to wait after a track ends before the next starts, so tracks do not run together. */
    private static int gapTicks;
    private static final int GAP_TICKS = 60;
    private static String lastFailure;
    private static boolean paused;
    private static int failedTracks;

    private XenoMusicPlayer() {}

    public static boolean playing() {
        return current != null && Minecraft.getInstance().getSoundManager().isActive(current);
    }

    public static String currentTrackId() {
        var tracks = XenoClientConfig.musicTracks;
        if (tracks.isEmpty()) return null;
        return tracks.get(Math.floorMod(XenoClientConfig.musicTrack, tracks.size()));
    }

    public static String currentTrackName() {
        return XenoMusicTracks.displayName(currentTrackId());
    }

    /** Called every client tick; nothing happens without a world or with music disabled. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!XenoClientConfig.musicEnabled || mc.level == null || XenoClientConfig.musicTracks.isEmpty()) {
            stop();
            return;
        }
        if (paused) return;
        if (current != null) {
            if (mc.getSoundManager().isActive(current)) {
                // Vanilla would otherwise start its own biome music over ours.
                mc.getMusicManager().stopPlaying();
                return;
            }
            current = null;
            currentId = null;
            gapTicks = GAP_TICKS;
            XenoClientConfig.musicTrack = XenoMusicTracks.step(XenoClientConfig.musicTrack, 1,
                    XenoClientConfig.musicTracks.size());
            XenoClientConfig.save();
            return;
        }
        if (gapTicks > 0) { gapTicks--; return; }
        start();
    }

    private static void start() {
        String id = currentTrackId();
        if (id == null) return;
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) { skipBroken(id, "not a resource location"); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager().getSoundEvent(location) == null) {
            skipBroken(id, "no such sound event in the loaded resource packs");
            return;
        }
        mc.getMusicManager().stopPlaying();
        SoundInstance instance = new SimpleSoundInstance(location, SoundSource.MUSIC,
                Math.max(0f, Math.min(1f, XenoClientConfig.musicVolume)), 1.0f,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        mc.getSoundManager().play(instance);
        current = instance;
        currentId = id;
        lastFailure = null;
        failedTracks = 0;
    }

    private static void skipBroken(String id, String why) {
        if (!id.equals(lastFailure)) {
            XenoPixelsMod.LOGGER.warn("Music track {} skipped: {}", id, why);
            lastFailure = id;
        }
        int size = XenoClientConfig.musicTracks.size();
        XenoClientConfig.musicTrack = XenoMusicTracks.step(XenoClientConfig.musicTrack, 1, size);
        // Every track broken: stop trying every tick.
        failedTracks++;
        if (failedTracks >= size) {
            gapTicks = 20 * 30;
            failedTracks = 0;
        } else gapTicks = 5;
    }

    public static void stop() {
        if (current != null) {
            Minecraft.getInstance().getSoundManager().stop(current);
            current = null;
            currentId = null;
        }
    }

    /** Pause-menu / command controls. All of them persist the client config. */
    public static void setEnabled(boolean enabled) {
        XenoClientConfig.musicEnabled = enabled;
        paused = false;
        if (!enabled) stop();
        else gapTicks = 0;
        XenoClientConfig.save();
    }

    public static void toggle() { setEnabled(!XenoClientConfig.musicEnabled); }

    public static void next() { skip(1); }

    public static void previous() { skip(-1); }

    private static void skip(int delta) {
        stop();
        paused = false;
        XenoClientConfig.musicTrack = XenoMusicTracks.step(XenoClientConfig.musicTrack, delta,
                XenoClientConfig.musicTracks.size());
        gapTicks = 0;
        XenoClientConfig.save();
        if (XenoClientConfig.musicEnabled && Minecraft.getInstance().level != null) start();
    }

    public static boolean select(int index) {
        int size = XenoClientConfig.musicTracks.size();
        if (index < 0 || index >= size) return false;
        stop();
        paused = false;
        XenoClientConfig.musicTrack = index;
        gapTicks = 0;
        XenoClientConfig.save();
        if (XenoClientConfig.musicEnabled && Minecraft.getInstance().level != null) start();
        return true;
    }

    /** Restart the running track at the selected volume. */
    public static void setVolume(float volume) {
        if (!Float.isFinite(volume)) return;
        XenoClientConfig.musicVolume = Math.max(0f, Math.min(1f, volume));
        XenoClientConfig.save();
        if (current != null && XenoClientConfig.musicEnabled) {
            // Restart the same track at the new volume rather than leaving a stale level.
            stop();
            gapTicks = 0;
            if (Minecraft.getInstance().level != null) start();
        }
    }

    /** Pause/resume; pressing Play while powered off also enables playback. */
    public static void playPause() {
        if (playing()) { stop(); paused = true; }
        else {
            paused = false;
            if (!XenoClientConfig.musicEnabled) setEnabled(true);
            gapTicks = 0;
            if (Minecraft.getInstance().level != null) start();
        }
    }

    public static boolean pausedByUser() {
        return paused;
    }

    /** Leaving the world: let go of the sound handle; the config keeps the position. */
    public static void onLogout() {
        stop();
        paused = false;
        failedTracks = 0;
        gapTicks = 0;
    }
}
