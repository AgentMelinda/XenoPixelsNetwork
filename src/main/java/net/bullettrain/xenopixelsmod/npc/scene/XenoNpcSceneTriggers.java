package net.bullettrain.xenopixelsmod.npc.scene;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires an NPC's scene when its trigger says to.
 *
 * <p>One place asks the question, so the answer cannot differ between the four call sites that ask
 * it. Each site — the interaction, the hurt, the death, the tick — calls {@link #fire} with what
 * just happened, and this decides whether that is the NPC's trigger.
 *
 * <p><b>All of its state is transient.</b> "When did this NPC last play its scene" is only
 * meaningful inside one server run; a cooldown written to disk is the mistake
 * {@code XenoNpcRespawnData} exists to remember about tick counts.
 */
public final class XenoNpcSceneTriggers {

    /**
     * How long before the same NPC may fire the same trigger again.
     *
     * <p>Ten seconds. Without it an approach trigger fires every check for as long as somebody
     * stands nearby, and a damaged trigger fires on every hit of a combo — which is a scene
     * restarting from step one several times a second rather than playing.
     */
    public static final int COOLDOWN_TICKS = 200;

    /** How close a player comes before an approach trigger fires. Matches the greeting range. */
    public static final double APPROACH_RANGE = 6.0;

    /** How often approach and timer are checked. A player cannot cross the range in half a second. */
    public static final int CHECK_STRIDE = 10;

    /** How often a TIMER scene plays. Half a minute, so a round is a round rather than a loop. */
    public static final int TIMER_INTERVAL = 600;

    /** NPC id to the game time its scene may fire again. */
    private static final Map<UUID, Long> READY = new ConcurrentHashMap<>();

    private XenoNpcSceneTriggers() {
    }

    /**
     * Plays this NPC's scene if {@code cause} is what it is waiting for.
     *
     * <p>Returns quickly for the overwhelming majority of NPCs, which have no scene at all — the
     * scene id is checked before anything else is read.
     *
     * @return whether a scene was started
     */
    public static boolean fire(XenoNpcEntity npc, NpcCombatProfile profile,
                               XenoNpcSceneTrigger cause) {
        if (npc == null || profile == null || cause == null || npc.level().isClientSide()) {
            return false;
        }
        if (profile.sceneId.isEmpty() || profile.sceneTrigger != cause
                || cause == XenoNpcSceneTrigger.MANUAL) {
            // MANUAL never fires from here even when it matches: that is the command's job, and a
            // manual scene that also played itself would be impossible to test.
            return false;
        }
        // Already mid-scene. Restarting from step one on a second hit is the failure the cooldown
        // exists for, and this is the cheaper half of it.
        if (XenoNpcScenePlayback.playing(npc)) {
            return false;
        }
        long now = npc.level().getGameTime();
        Long ready = READY.get(npc.getUUID());
        if (ready != null && now < ready) {
            return false;
        }
        if (!XenoNpcScenePlayback.start(npc, profile.sceneId)) {
            return false;
        }
        READY.put(npc.getUUID(), now + COOLDOWN_TICKS);
        return true;
    }

    /**
     * The triggers that fire on their own rather than in response to something.
     *
     * <p>Called every tick from {@code aiStep}, and staggered by entity id so a row of NPCs does not
     * all check on the same tick — the same treatment the bard, the path walker and the social
     * gestures get.
     */
    public static void tick(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (npc == null || profile == null || profile.sceneId.isEmpty()) {
            return;
        }
        XenoNpcSceneTrigger trigger = profile.sceneTrigger;
        if (trigger != XenoNpcSceneTrigger.APPROACH && trigger != XenoNpcSceneTrigger.TIMER) {
            return;
        }
        if ((npc.tickCount + npc.getId()) % CHECK_STRIDE != 0) {
            return;
        }
        if (trigger == XenoNpcSceneTrigger.TIMER) {
            // On its own clock rather than on the cooldown, so a long scene and a short interval do
            // not silently become "as often as the cooldown allows".
            if ((npc.tickCount + npc.getId()) % TIMER_INTERVAL == 0) {
                fire(npc, profile, XenoNpcSceneTrigger.TIMER);
            }
            return;
        }
        Player nearby = npc.level().getNearestPlayer(npc, APPROACH_RANGE);
        if (nearby != null && nearby.isAlive()) {
            fire(npc, profile, XenoNpcSceneTrigger.APPROACH);
        }
    }

    /**
     * Drops an NPC's remembered cooldown.
     *
     * <p>The same housekeeping {@code XenoNpcSpeech.forget} and {@code NpcSocialBehaviour.forget}
     * do, so a long-running server does not accumulate an entry per NPC that ever existed.
     */
    public static void forget(UUID npcId) {
        if (npcId != null) {
            READY.remove(npcId);
        }
    }

    /** Clears everything. For a world unload, and for tests. */
    public static void clearAll() {
        READY.clear();
    }
}
