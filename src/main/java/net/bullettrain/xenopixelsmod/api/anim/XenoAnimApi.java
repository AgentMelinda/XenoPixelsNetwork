package net.bullettrain.xenopixelsmod.api.anim;

import net.bullettrain.xenopixelsmod.anim.XenoAnimPlayback;
import net.bullettrain.xenopixelsmod.anim.XenoClipLibrary;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAnim;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Play an animation clip on an entity.
 *
 * <p>Covers both the clips this mod ships ({@code combat.xeno_*}) and the ones authored in the Xeno
 * Anim Studio and published to the server's clip library. Call it with either the bare clip name
 * ({@code "my_jab"}) or the full animation name ({@code "combat.xeno_my_jab"}); both resolve.
 *
 * <p><b>Server side.</b> These are server-authoritative calls: the server names the clip and tells
 * every client in range to draw it. Calling from a client does nothing useful.
 *
 * <p><b>Who can be animated.</b> Full DragonMineZ NPCs, native Xeno NPCs using a GeckoLib
 * model, clones, and live players. GeckoLib models must use bone names compatible with the
 * requested clip; the shipped DragonMineZ Master Gohan geometry has the combat limb bones.
 * {@link #canPlay} checks the renderer path, while {@link #playClip} rejects unknown clips.
 *
 * <pre>{@code
 * if (XenoAnimApi.isClipAvailable("my_jab")) {
 *     XenoAnimApi.playClip(npc, "my_jab", 1.0f, 40, false);
 * }
 * }</pre>
 *
 * @see net.bullettrain.xenopixelsmod.api.XenoPixelsApi
 */
public final class XenoAnimApi {

    /** The prefix every clip this mod owns is registered under. */
    public static final String PREFIX = "combat.xeno_";

    public static final float MIN_SPEED = 0.15f;
    public static final float MAX_SPEED = 4.0f;
    public static final int MIN_DURATION = 1;
    public static final int MAX_DURATION = 6000;

    private XenoAnimApi() {}

    /** Whether {@code target} is drawn through a rig these clips can pose. */
    public static boolean canPlay(LivingEntity target) {
        return NpcDmzAnim.canBroadcast(target);
    }

    /**
     * Plays {@code clip} on {@code target} for everyone who can see it.
     *
     * @param speed playback multiplier; clamped to a sane range by the sender
     * @return false when the entity cannot show these clips, or the clip is not one the server knows
     */
    public static boolean playClip(LivingEntity target, String clip, float speed) {
        return playClip(target, clip, speed, 0, false);
    }

    /** As {@link #playClip(LivingEntity, String, float)} at the clip's authored speed. */
    public static boolean playClip(LivingEntity target, String clip) {
        return playClip(target, clip, 1.0f, 0, false);
    }

    /**
     * Plays {@code clip} and cuts it after {@code durationTicks}. {@code 0} means the authored
     * length when that is known.
     */
    public static boolean playClip(LivingEntity target, String clip, float speed, int durationTicks) {
        return playClip(target, clip, speed, durationTicks, false);
    }

    /**
     * Plays {@code clip} on {@code target}.
     *
     * <p>{@code hold} keeps the last authored pose until {@link #stopClip} or another play.
     * NPC scripts should call {@code XenoPixels.playClipHold(npc, name)} instead of this
     * 5-arg overload. A zero duration lets the one-shot attack controller finish the
     * authored clip naturally. A positive {@code durationTicks} schedules an explicit stop.
     * Speed is a
     * playback multiplier (0.15-4.0). Mid-clip freeze is not something DragonMineZ
     * exposes; hold is the last frame.
     */
    public static boolean playClip(LivingEntity target, String clip, float speed,
                                   int durationTicks, boolean hold) {
        String animation = resolve(clip);
        if (animation == null || !canPlay(target)) {
            return false;
        }
        float clamped = clampSpeed(speed);
        // One-shots use the melee packet. FLAG_HOLD is a KI play-and-hold; on Full NPC
        // proxies that pose often never starts, so a pushed studio clip looked like a no-op.
        boolean started = hold
                ? XenoAnimPlayback.playHold(target, animation, clamped)
                : XenoAnimPlayback.playOnce(target, animation, clamped);
        if (!started) {
            return false;
        }
        if (hold) {
            return true;
        }
        if (durationTicks > 0) {
            int scaled = Math.max(1, Math.round(clampDuration(durationTicks) / clamped));
            XenoAnimPlayback.scheduleStop(target, scaled);
        }
        return true;
    }

    /**
     * Stops a scripted clip on {@code target}, including one already handed to DragonMineZ's
     * KI controller.
     *
     * @return false when the entity cannot show these clips at all
     */
    public static boolean stopClip(LivingEntity target) {
        return XenoAnimPlayback.stop(target);
    }

    /**
     * Authored length of {@code clip} in ticks, or {@code -1} when the server does not know it.
     */
    public static int clipDuration(String clip) {
        String animation = resolve(clip);
        if (animation == null) {
            return -1;
        }
        int ticks = authoredDurationTicks(animation);
        return ticks > 0 ? ticks : -1;
    }

    public static float clampSpeed(float speed) {
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
    }

    public static int clampDuration(int ticks) {
        return Math.max(MIN_DURATION, Math.min(MAX_DURATION, ticks));
    }

    /** Every clip name that {@link #playClip} will accept, full animation names. */
    public static List<String> listClips() {
        return new ArrayList<>(Bt3AnimationCatalog.playableAnimationNames());
    }

    /** Just the clips published to the server library, as bare names. */
    public static List<String> listLibraryClips() {
        return new ArrayList<>(XenoClipLibrary.names());
    }

    public static boolean isClipAvailable(String clip) {
        return resolve(clip) != null;
    }

    /** Transform, punch, charged punch/kick, Hakai, and ki-charge slots. */
    public static String[] listStateSlots() {
        return net.bullettrain.xenopixelsmod.anim.CombatStateAnim.slotNames();
    }

    /**
     * Binds {@code clip} to a combat/transform state on {@code target}.
     *
     * <p>NPCs store it on their combat profile. Players keep it for the session. Empty clip
     * clears the override so the server-wide bind or the shipped default is used again.
     */
    public static boolean setStateClip(LivingEntity target, String slot, String clip) {
        return net.bullettrain.xenopixelsmod.anim.CombatStateAnim.set(target, slot, clip);
    }

    public static String getStateClip(LivingEntity target, String slot) {
        return net.bullettrain.xenopixelsmod.anim.CombatStateAnim.get(target, slot);
    }

    public static boolean clearStateClip(LivingEntity target, String slot) {
        return net.bullettrain.xenopixelsmod.anim.CombatStateAnim.clear(target, slot);
    }

    /** Plays whatever clip is currently bound to {@code slot} on {@code target}. */
    public static boolean playState(LivingEntity target, String slot) {
        return net.bullettrain.xenopixelsmod.anim.CombatStateAnim.play(target, slot);
    }

    /**
     * Server-wide bind for every player and NPC that has no per-entity override.
     * Empty {@code clip} restores the shipped default.
     */
    public static boolean bindStateSlot(String slot, String clip) {
        return net.bullettrain.xenopixelsmod.anim.CombatStateAnim.bindGlobal(slot, clip);
    }

    /** Bare name or full animation name to the full name, or null when nothing knows it. */
    public static String resolve(String clip) {
        if (clip == null || clip.isBlank()) return null;
        String trimmed = clip.trim();
        if (Bt3AnimationCatalog.isPlayable(trimmed)) return trimmed;
        String prefixed = PREFIX + trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (Bt3AnimationCatalog.isPlayable(prefixed)) {
            return prefixed;
        }
        String bare = XenoClipLibrary.sanitize(
                trimmed.startsWith(PREFIX) ? trimmed.substring(PREFIX.length()) : trimmed);
        return !bare.isEmpty() && XenoClipLibrary.names().contains(bare) ? PREFIX + bare : null;
    }

    static int authoredDurationTicks(String animation) {
        float seconds = Bt3AnimationCatalog.authoredSeconds(animation);
        if (seconds > 0.0f) {
            return Math.max(1, Math.round(seconds * 20.0f));
        }
        int library = XenoClipLibrary.durationTicks(animation);
        return library > 0 ? library : 0;
    }
}
