package net.bullettrain.xenopixelsmod.npc.job;

import net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The gestures that make an NPC read as alive rather than as scenery.
 *
 * <p>A player walking up gets waved at; left alone, an NPC shifts its weight and occasionally
 * nods. None of it is scripted — a scene can call the same clips deliberately, but this is what
 * happens when nobody wrote a scene at all, which is most NPCs most of the time.
 *
 * <p>Clips go out through {@link XenoAnimApi#playClip}, which is server-side and broadcasts to
 * everyone in range. There is no second animation path.
 *
 * <p><b>All of its state is transient.</b> "Have I greeted this player recently" is only
 * meaningful inside one server run, and a cooldown written to disk is the mistake
 * {@code XenoNpcRespawnData} exists to remember.
 */
public final class NpcSocialBehaviour {

    /** How close a player comes before an NPC greets them. Comfortably inside speech range. */
    public static final double GREET_RANGE = 6.0;

    /** How long before the same NPC greets the same player again. Ninety seconds. */
    public static final int GREET_COOLDOWN = 1800;

    /** How often the check runs. A player cannot cross the greet range inside half a second. */
    public static final int CHECK_STRIDE = 10;

    /** Shortest and longest gap between idle gestures, in ticks. */
    public static final int IDLE_MIN = 600;
    public static final int IDLE_MAX = 1800;

    /** Greeting clip, and the two an idle NPC picks between. */
    public static final String CLIP_GREET = "hi_wave";
    public static final String CLIP_NOD = "nod";
    public static final String CLIP_IDLE = "idle_shift";

    /** NPC UUID then player UUID, to the game time that pair may be greeted again. */
    private static final Map<UUID, Map<UUID, Long>> GREETED = new ConcurrentHashMap<>();

    /** NPC UUID to the game time it may make an idle gesture again. */
    private static final Map<UUID, Long> IDLE_READY = new ConcurrentHashMap<>();

    private NpcSocialBehaviour() {
    }

    /**
     * One NPC's turn.
     *
     * <p>Called every tick from {@code aiStep}. Returns on the first field read for an NPC with
     * gestures switched off, which is every NPC that existed before this shipped.
     */
    public static void tick(XenoNpcEntity npc, NpcCombatProfile profile) {
        if (npc == null || profile == null || !profile.socialGestures) {
            return;
        }
        // Not while fighting. An NPC that waved at the player it was trading blows with would be
        // funny once and broken every time after.
        if (npc.getTarget() != null) {
            return;
        }
        // Staggered by entity id, like the bard and the path walker, so a row of NPCs does not all
        // scan on the same tick.
        if ((npc.tickCount + npc.getId()) % CHECK_STRIDE != 0) {
            return;
        }

        long now = npc.level().getGameTime();
        Player nearby = npc.level().getNearestPlayer(npc, GREET_RANGE);
        if (nearby != null && nearby.isAlive()) {
            if (greet(npc, nearby, now)) {
                return;
            }
        }
        idle(npc, now);
    }

    /**
     * Waves at a player who has just arrived.
     *
     * @return true when it greeted, so the idle gesture does not also fire this tick
     */
    private static boolean greet(XenoNpcEntity npc, Player player, long now) {
        Map<UUID, Long> seen = GREETED.computeIfAbsent(npc.getUUID(),
                id -> new ConcurrentHashMap<>());
        Long ready = seen.get(player.getUUID());
        if (ready != null && now < ready) {
            return false;
        }
        seen.put(player.getUUID(), now + GREET_COOLDOWN);
        // The idle clock is pushed out too: an NPC that waves and then immediately shifts its
        // weight looks twitchy rather than welcoming.
        IDLE_READY.put(npc.getUUID(), now + IDLE_MIN);
        return XenoAnimApi.playClip(npc, CLIP_GREET);
    }

    /** A small gesture now and then, so a standing NPC is not a statue. */
    private static void idle(XenoNpcEntity npc, long now) {
        Long ready = IDLE_READY.get(npc.getUUID());
        if (ready == null) {
            // First sighting: stagger the first gesture rather than having every NPC in a village
            // move at once the moment the chunk loads.
            IDLE_READY.put(npc.getUUID(), now + npc.getRandom().nextInt(IDLE_MIN, IDLE_MAX));
            return;
        }
        if (now < ready) {
            return;
        }
        IDLE_READY.put(npc.getUUID(), now + npc.getRandom().nextInt(IDLE_MIN, IDLE_MAX));
        XenoAnimApi.playClip(npc, npc.getRandom().nextInt(4) == 0 ? CLIP_NOD : CLIP_IDLE);
    }

    /**
     * Drops an NPC's remembered cooldowns.
     *
     * <p>Called when one is removed, so a long-running server does not accumulate an entry per NPC
     * that ever existed - the same housekeeping {@code XenoNpcSpeech.forget} does for lines.
     */
    public static void forget(UUID npcId) {
        if (npcId != null) {
            GREETED.remove(npcId);
            IDLE_READY.remove(npcId);
        }
    }

    /** Clears everything. For a world unload, and for tests. */
    public static void clearAll() {
        GREETED.clear();
        IDLE_READY.clear();
    }
}
