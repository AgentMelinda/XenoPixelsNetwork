package net.bullettrain.xenopixelsmod.client.music;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
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
    private static FadingMusicSound current;
    private static FadingMusicSound cinematic;
    /** The title-screen track is fixed by the owner's request and independent of the world playlist. */
    private static FadingMusicSound menuTrack;
    private static java.util.UUID playingCast;
    private static final java.util.Set<java.util.UUID> startedCasts = new java.util.HashSet<>();
    private static String cinematicId;
    private static String currentId;
    /** Ticks to wait after a track ends before the next starts, so tracks do not run together. */
    private static int gapTicks;
    private static final int GAP_TICKS = 60;
    private static String lastFailure;
    private static boolean paused;
    private static int failedTracks;

    private XenoMusicPlayer() {}

    public static boolean playing() {
        return cinematic != null && Minecraft.getInstance().getSoundManager().isActive(cinematic)
                || current != null && Minecraft.getInstance().getSoundManager().isActive(current);
    }

    public static String currentTrackId() {
        var tracks = XenoClientConfig.musicTracks;
        if (tracks.isEmpty()) return null;
        return tracks.get(Math.floorMod(XenoClientConfig.musicTrack, tracks.size()));
    }

    public static String currentTrackName() {
        return XenoMusicTracks.displayName(cinematic != null ? cinematicId : currentTrackId());
    }

    /** Called every client tick; the fixed title-screen track is handled before world playlist settings. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TitleScreen) {
            stop();
            stopCinematic();
            mc.getMusicManager().stopPlaying();
            if (menuTrack != null && mc.getSoundManager().isActive(menuTrack)) return;
            menuTrack = null;
            ResourceLocation menuId = ResourceLocation.tryParse(XenoMusicTracks.MAIN_MENU_TRACK);
            if (menuId != null && mc.getSoundManager().getSoundEvent(menuId) != null) {
                menuTrack = new FadingMusicSound(menuId, false, true);
                mc.getSoundManager().play(menuTrack);
            }
            return;
        }
        stopMenuTrack();
        if (!XenoClientConfig.musicEnabled || mc.level == null || XenoClientConfig.musicTracks.isEmpty()) {
            stop();
            stopCinematic();
            return;
        }
        if (paused) return;
        updateCinematic();
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
        FadingMusicSound instance = new FadingMusicSound(location, false);
        instance.duck(cinematic != null);
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

    private static void stopMenuTrack() {
        if (menuTrack != null) Minecraft.getInstance().getSoundManager().stop(menuTrack);
        menuTrack = null;
    }
    private static void updateCinematic() {
        Minecraft mc = Minecraft.getInstance();
        if (!XenoClientConfig.cinematicMusicEnabled) {
            stopCinematic();
            if (current != null) current.duck(false);
            return;
        }
        var desired = net.bullettrain.xenopixelsmod.client.combat.v3.ClientCastPresentation.musicCast();
        if (cinematic != null) {
            if (desired == null || !desired.cast().equals(playingCast)) cinematic.fadeOut();
            if (!mc.getSoundManager().isActive(cinematic) || cinematic.isStopped()) stopCinematic();
        }
        if (cinematic == null && desired != null && startedCasts.add(desired.cast())) {
            ResourceLocation id = ResourceLocation.tryParse(desired.music());
            if (id != null && mc.getSoundManager().getSoundEvent(id) != null) {
                cinematic = new FadingMusicSound(id, true, true);
                playingCast = desired.cast(); cinematicId = desired.music();
                mc.getSoundManager().play(cinematic);
            }
        }
        if (current != null) current.duck(cinematic != null);
        if (cinematic != null) mc.getMusicManager().stopPlaying();
    }
    private static void stopCinematic() {
        if (cinematic != null) Minecraft.getInstance().getSoundManager().stop(cinematic);
        cinematic = null; cinematicId = null; playingCast = null;
    }

    /** Pause-menu / command controls. All of them persist the client config. */
    public static void setEnabled(boolean enabled) {
        XenoClientConfig.musicEnabled = enabled;
        paused = false;
        if (!enabled) { stop(); stopCinematic(); }
        else gapTicks = 0;
        XenoClientConfig.save();
    }

    public static void toggle() { setEnabled(!XenoClientConfig.musicEnabled); }

    public static void setCinematicEnabled(boolean enabled) {
        XenoClientConfig.cinematicMusicEnabled = enabled;
        if (!enabled) {
            stopCinematic();
            if (current != null) current.duck(false);
        }
        XenoClientConfig.save();
    }

    public static void toggleCinematic() { setCinematicEnabled(!XenoClientConfig.cinematicMusicEnabled); }

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
        // Both streamed channels read the new volume on their next sound tick.
    }

    /** Pause/resume; pressing Play while powered off also enables playback. */
    public static void playPause() {
        if (playing()) { stop(); stopCinematic(); paused = true; }
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
        stopCinematic(); playingCast = null; startedCasts.clear();
        net.bullettrain.xenopixelsmod.client.combat.v3.ClientCastPresentation.clear();
        paused = false;
        failedTracks = 0;
        gapTicks = 0;
    }
}
