package net.bullettrain.xenopixelsmod.npc.job;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCustomSounds;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.bullettrain.xenopixelsmod.npc.XenoNpcJob;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The Bard job: plays a sound to players who come near.
 *
 * <p>The first job with anything behind it, and the shape the other eleven should follow — the
 * job's state lives on the profile, the work happens on the NPC's own {@code aiStep}, and the
 * whole thing is skipped in one field read when the NPC is not a Bard.
 *
 * <p><b>On and off distance are deliberately different numbers.</b> A single radius makes an NPC
 * at exactly that range stutter — in, out, in — as the player drifts a block back and forth. The
 * player has to get within the on distance to start it and leave the off distance to stop it,
 * which is hysteresis and is why the reference has two fields rather than one.
 */
public final class NpcBardJob {

    /**
     * How often a looping bard replays.
     *
     * <p>Sounds are fire-and-forget in vanilla: nothing reports a length and nothing can stop one
     * early. So a loop is a replay on a timer, and this is that timer — long enough that a short
     * sound has finished and a long one is not stacked on top of itself twenty times.
     */
    public static final int LOOP_TICKS = 200;

    /** How often the distance check runs. A player cannot cross the range inside a second. */
    public static final int CHECK_STRIDE = 20;

    private NpcBardJob() {
    }

    /**
     * One NPC's turn.
     *
     * <p>Called every tick from {@code aiStep} and returns immediately for the overwhelming
     * majority of NPCs, which have no job at all.
     */
    public static void tick(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (profile == null || XenoNpcJob.byId(profile.job) != XenoNpcJob.BARD
                || profile.bardSound.isEmpty()) {
            return;
        }
        if (!profile.jobEnabled) {
            npc.setBardPlaying(false);
            return;
        }
        // Staggered by entity id, like the battle-power refresh above it, so a row of bards does
        // not all scan for players on the same tick.
        if ((npc.tickCount + npc.getId()) % CHECK_STRIDE != 0) {
            return;
        }

        boolean playing = npc.bardPlaying();
        double radius = playing ? offDistance(profile) : profile.bardOnDistance;
        boolean near = nearestPlayerWithin(npc, radius);

        if (!near) {
            // Out of the off distance: stop, and re-arm so walking back in starts it again.
            npc.setBardPlaying(false);
            return;
        }
        if (!playing) {
            play(npc, profile);
            npc.setBardPlaying(true);
            npc.setBardNextPlayTick(npc.tickCount + LOOP_TICKS);
            return;
        }
        // Already playing. A one-shot bard stays quiet until the player leaves and returns; a
        // looping one replays on the timer.
        if (profile.bardLoops && npc.tickCount >= npc.bardNextPlayTick()) {
            play(npc, profile);
            npc.setBardNextPlayTick(npc.tickCount + LOOP_TICKS);
        }
    }

    /**
     * The radius a playing bard stops at.
     *
     * <p>Falls back to the on distance when the operator has not enabled a separate off distance,
     * and never returns less than the on distance — an off distance inside the on distance would
     * produce the stutter the two fields exist to prevent.
     */
    public static double offDistance(NpcCombatProfile profile) {
        if (!profile.bardHasOffDistance) {
            return profile.bardOnDistance;
        }
        return Math.max(profile.bardOnDistance, profile.bardOffDistance);
    }

    private static boolean nearestPlayerWithin(XenoNpcEntity npc, double radius) {
        if (radius <= 0.0) {
            return false;
        }
        Player player = npc.level().getNearestPlayer(npc, radius);
        return player != null && player.isAlive();
    }

    private static void play(XenoNpcEntity npc, NpcCombatProfile profile) {
        SoundEvent sound = NpcCustomSounds.resolve(profile.bardSound);
        if (sound == null) {
            // A typo in the editor is silent rather than a crash, the same way the other five
            // sound slots behave.
            return;
        }
        // RECORDS is the category a jukebox plays on, so a player who has turned music down gets
        // a bard turned down with it. AMBIENT otherwise, which is where an NPC's own noises live.
        npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), sound,
                profile.bardJukebox ? SoundSource.RECORDS : SoundSource.AMBIENT,
                1.0f, 1.0f);
    }
}
