package net.bullettrain.xenopixelsmod.client.music;

import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/** Stream volume is updated without restarting or seeking the track. */
final class FadingMusicSound extends SimpleSoundInstance implements TickableSoundInstance {
    private float gain;
    private final boolean cinematic;
    private boolean ending, stopped;
    private float backgroundGain = 1;
    FadingMusicSound(ResourceLocation id, boolean cinematic) {
        this(id, cinematic, false);
    }
    FadingMusicSound(ResourceLocation id, boolean cinematic, boolean loop) {
        super(id, SoundSource.MUSIC, cinematic ? 0 : XenoClientConfig.musicVolume, 1,
                SoundInstance.createUnseededRandom(), loop, 0, Attenuation.NONE, 0, 0, 0, true);
        this.cinematic = cinematic; this.gain = cinematic ? 0 : 1;
    }
    void fadeOut() { ending = true; }
    void duck(boolean active) { backgroundGain = active ? .15f : 1; }
    @Override public boolean canStartSilent() { return true; }
    @Override public boolean isStopped() { return stopped; }
    @Override public void tick() {
        if (cinematic) gain = Math.clamp(gain + (ending ? -1f/15 : 1f/5), 0f, 1f);
        volume = Math.clamp(XenoClientConfig.musicVolume, 0f, 1f) * gain * backgroundGain;
        if (ending && gain <= 0) stopped = true;
    }
}
