package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Plays one of this mod's combat clips on an NPC or a player.
 *
 * <p>Full DragonMineZ NPCs use a synthetic player. Native Xeno NPCs with a GeckoLib model
 * consume the same packets in their own renderer, with the combat animation file as a fallback.
 * An external CustomNPCs Gecko model still uses {@link NpcGeckoAnim}. A {@link ServerPlayer}
 * already has the DragonMineZ rig.
 *
 * <p>Sent to every player who can see the target, following the same nearby-players broadcast
 * {@code NpcAuraFx} uses - these packets are cosmetic and keyed by UUID, so there is nothing to
 * reconcile if one is missed.
 */
public final class NpcDmzAnim {

    private record LastClip(String name, long tick) {}
    private static final java.util.Map<LivingEntity, LastClip> LAST_PLAY =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Matches the tracking distance DragonMineZ's own cosmetics are sent at. */
    public static final double BROADCAST_RANGE = 96.0;

    /** Play as DragonMineZ KI play-and-hold (scripted studio clips). */
    public static final int FLAG_HOLD = 1;

    /** Client should call {@code stopKiAnimation} rather than only drop a queued clip. */
    public static final int FLAG_STOP = 2;

    /** Floor charge ring: punch (orange). Unused bits stay ignored on older clients. */
    public static final int FLAG_CHARGE_PUNCH = 4;

    /** Floor charge ring: kick (magenta). */
    public static final int FLAG_CHARGE_KICK = 8;

    /** Packet animation name that only starts/stops the charge ring. */
    public static final String CHARGE_GLOW = "xeno:charge_glow";

    private NpcDmzAnim() {
    }

    /** True when a renderer for this NPC consumes Xeno combat clip packets. */
    public static boolean canAnimate(LivingEntity npc) {
        if (npc == null || !npc.isAlive()) return false;
        if (npc instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity) return true;
        if (!NpcCombatProfile.hasProfile(npc)) return false;
        NpcCombatProfile profile = NpcCombatProfile.readCached(npc);
        if (npc instanceof net.bullettrain.xenopixelsmod.npc.XenoNpcEntity) {
            String kind = NpcCombatProfile.normalizeModelKind(profile.modelKind);
            return NpcCombatProfile.MODEL_GECKOLIB.equals(kind)
                    || NpcCombatProfile.MODEL_VANILLA.equals(kind)
                    && profile.appearance.mode == NpcDmzAppearance.Mode.FULL;
        }
        return profile.appearance.mode == NpcDmzAppearance.Mode.FULL
                && !NpcGeckoAnim.canAnimate(npc);
    }

    /** NPCs with a clip-consuming renderer, clones, or a live server player. */
    public static boolean canBroadcast(LivingEntity target) {
        return target != null && target.isAlive()
                && (target instanceof ServerPlayer || canAnimate(target));
    }

    public static boolean play(LivingEntity npc, String animation) {
        return play(npc, animation, 1.0f);
    }

    /**
     * @return false when the NPC cannot show these clips, or the name is not one this mod ships -
     *         a script gets an answer either way rather than silence
     */
    public static boolean play(LivingEntity npc, String animation, float speed) {
        String name = net.bullettrain.xenopixelsmod.api.anim.XenoAnimApi.resolve(animation);
        if (name == null || npc == null || !canBroadcast(npc)) {
            return false;
        }
        // A script may call playAnimation immediately before meleeHit. The hit path then
        // invokes the same selected clip in this tick; a second packet would restart the pose.
        LastClip prior = LAST_PLAY.get(npc);
        long tick = npc.level().getGameTime();
        if (prior != null && prior.tick() == tick && prior.name().equals(name)) return true;
        // One-shot melee. Studio names are registered on the client resolver when the
        // library bakes; KI hold is only for playClip(..., hold=true) / transform holds.
        if (!broadcast(npc, name, speed, 0)) return false;
        LAST_PLAY.put(npc, new LastClip(name, tick));
        return true;
    }

    /**
     * Cancels a queued clip and stops a KI-hold that is already on the controller.
     *
     * @return false when this target cannot show these clips at all
     */
    public static boolean stop(LivingEntity npc) {
        return broadcast(npc, net.bullettrain.xenopixelsmod.client.compat.npc.NpcAnimationClient.STOP,
                1.0f, FLAG_STOP);
    }

    /**
     * Sends one clip packet to every player in range. {@code flags} is {@link #FLAG_HOLD},
     * {@link #FLAG_STOP}, or zero for the short melee path combo punches still use.
     */
    public static boolean broadcast(LivingEntity target, String animation, float speed, int flags) {
        if (!canBroadcast(target) || !(target.level() instanceof ServerLevel level)) {
            return false;
        }
        float playSpeed = CHARGE_GLOW.equals(animation)
                ? Math.max(1.0f, speed)
                : Math.max(0.15f, Math.min(4.0f, speed));
        NpcAnimationPacket packet = new NpcAnimationPacket(
                target.getUUID(), animation, playSpeed, flags);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(target) <= BROADCAST_RANGE * BROADCAST_RANGE) {
                ModNetwork.sendToPlayer(viewer, packet);
            }
        }
        return true;
    }

    /** Plays the beat {@code intent} names, in whichever animation generation is configured. */
    public static boolean play(LivingEntity npc, Bt3AnimationIntent intent) {
        Bt3AnimationCatalog.Clip clip =
                Bt3AnimationCatalog.clipFor(intent, XenoServerConfig.comboAnimGeneration);
        if (clip == null) {
            return false;
        }
        return play(npc, clip.name(), Bt3AnimationCatalog.speedOf(intent));
    }
}
