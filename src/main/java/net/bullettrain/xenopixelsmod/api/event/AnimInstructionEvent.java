package net.bullettrain.xenopixelsmod.api.event;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * Fired when a XenoPixels animation clip reaches an instruction keyframe.
 *
 * <p>Instruction keyframes are the {@code timeline} block of a GeckoLib 1.8 animation: free text
 * authored against a point in the clip. The Xeno Anim Studio writes them, and this event is how a
 * clip tells the rest of the game that it got somewhere - land a hitbox, start a screen shake, cue
 * a follow-up.
 *
 * <p>Posted on {@code NeoForge.EVENT_BUS}, on the <em>client</em>, by the playback paths this mod
 * owns ({@code /xenoanim play}, the studio preview, and NPC clip playback). A clip played through
 * DragonMineZ's own GeckoLib controller - which is what a bound combat move uses - does not post
 * this, because that controller registers no keyframe handlers of ours.
 *
 * <p>Not cancellable: the keyframe has already been reached by the time anyone hears about it.
 */
public class AnimInstructionEvent extends Event {

    private final LivingEntity entity;
    private final String clip;
    private final String instruction;
    private final double time;

    public AnimInstructionEvent(LivingEntity entity, String clip, String instruction, double time) {
        this.entity = entity;
        this.clip = clip;
        this.instruction = instruction;
        this.time = time;
    }

    /** Who the clip is playing on. Never null. */
    public LivingEntity getEntity() {
        return entity;
    }

    /** The clip name, without the {@code combat.xeno_} prefix. */
    public String getClip() {
        return clip;
    }

    /** The authored instruction text, verbatim. */
    public String getInstruction() {
        return instruction;
    }

    /** Where in the clip this keyframe sits, in seconds. */
    public double getTime() {
        return time;
    }
}
