package net.bullettrain.xenopixelsmod.client.anim;

import net.bullettrain.xenopixelsmod.client.anim.studio.AnimTimeline;
import net.bullettrain.xenopixelsmod.client.anim.studio.StudioPoseBuffer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays a saved studio clip on the local player through {@link StudioPoseBuffer}
 * (the same GeckoLib bone override the editor uses).
 *
 * <p>Playback ramps the pose buffer's weight up over the clip's {@code blendInTicks} and down over
 * its {@code blendOutTicks}, and stopping ramps out rather than cutting, so bones ease into the
 * first key and back to whatever DragonMineZ was doing instead of snapping at both ends.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID)
public final class XenoAnimPlayer {
    private static final double TICK = 1.0 / XenoAnimClip.TICKS_PER_SECOND;

    private static final AnimTimeline TIMELINE = new AnimTimeline();
    private static boolean playing;
    private static boolean loop;
    private static int fadeOutTicksLeft;
    private static int fadeOutTicksTotal;

    private XenoAnimPlayer() {}

    public static boolean playing() {
        return playing || fadeOutTicksLeft > 0;
    }

    public static String clipName() {
        return TIMELINE.clip() == null ? "" : TIMELINE.clip().name;
    }

    public static void play(XenoAnimClip clip, boolean loopPlayback) {
        if (clip == null || clip.isEmpty()) {
            stopNow();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) StudioPoseBuffer.setSubject(mc.player.getUUID());
        StudioPoseBuffer.setActive(true);
        StudioPoseBuffer.setWeight(clip.blendInTicks > 0 ? 0f : 1f);
        TIMELINE.bind(clip);
        TIMELINE.snap(false);
        TIMELINE.seek(0.0);
        loop = loopPlayback || clip.loop;
        playing = true;
        fadeOutTicksLeft = 0;
        fadeOutTicksTotal = 0;
        StudioPoseBuffer.putAll(TIMELINE.poseAt(0.0));
    }

    /** Eases the pose back out over the clip's blend-out, then releases the bones. */
    public static void stop() {
        XenoAnimClip clip = TIMELINE.clip();
        int blendOut = clip == null ? 0 : clip.blendOutTicks;
        if (!playing || blendOut <= 0) {
            stopNow();
            return;
        }
        playing = false;
        fadeOutTicksTotal = blendOut;
        fadeOutTicksLeft = blendOut;
    }

    /** Drops the pose immediately, with no ease. */
    public static void stopNow() {
        playing = false;
        fadeOutTicksLeft = 0;
        fadeOutTicksTotal = 0;
        TIMELINE.seek(0.0);
        StudioPoseBuffer.setActive(false);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (playing || fadeOutTicksLeft > 0) stopNow();
            return;
        }
        // Studio screen drives its own playhead; don't double-step.
        if (mc.screen instanceof net.bullettrain.xenopixelsmod.client.screen.XenoAnimStudioScreen) {
            return;
        }
        if (fadeOutTicksLeft > 0) {
            fadeOutTicksLeft--;
            StudioPoseBuffer.setWeight(fadeOutTicksTotal <= 0
                    ? 0f : fadeOutTicksLeft / (float) fadeOutTicksTotal);
            if (fadeOutTicksLeft <= 0) stopNow();
            return;
        }
        if (!playing) return;

        XenoAnimClip clip = TIMELINE.clip();
        double from = TIMELINE.playhead();
        double next = from + TICK;
        boolean wrapped = false;
        if (next > TIMELINE.durationSeconds() + 1.0e-6) {
            if (loop && TIMELINE.lengthTicks() > 0) {
                next = 0.0;
                wrapped = true;
            } else {
                AnimEventPlayer.fire(mc.player, clip, from, TIMELINE.durationSeconds());
                stop();
                return;
            }
        }
        if (wrapped) {
            // Finish the old pass, then start the new one, so no keyframe is skipped at the seam.
            AnimEventPlayer.fire(mc.player, clip, from, TIMELINE.durationSeconds());
            AnimEventPlayer.fire(mc.player, clip, -1.0, 0.0);
        } else {
            AnimEventPlayer.fire(mc.player, clip, from, next);
        }
        TIMELINE.seek(next);
        StudioPoseBuffer.setWeight(weightAt(clip, TIMELINE.playhead()));
        StudioPoseBuffer.putAll(TIMELINE.poseAt(TIMELINE.playhead()));
    }

    /** Ramps in over {@code blendInTicks} and out over {@code blendOutTicks} of a non-looping clip. */
    static float weightAt(XenoAnimClip clip, double seconds) {
        if (clip == null) return 1f;
        float weight = 1f;
        if (clip.blendInTicks > 0) {
            weight = Math.min(weight, (float) (seconds / (clip.blendInTicks * TICK)));
        }
        if (clip.blendOutTicks > 0 && !clip.loop) {
            double remaining = clip.sceneTicks() * TICK - seconds;
            weight = Math.min(weight, (float) (remaining / (clip.blendOutTicks * TICK)));
        }
        return Math.max(0f, Math.min(1f, weight));
    }
}
