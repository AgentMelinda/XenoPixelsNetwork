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
 * <p>NPCs must be drawn in Full DragonMineZ appearance: that mode renders through a synthetic
 * player, which is the only thing DragonMineZ's animation system will pose. A humanoid or Gecko
 * custom-model NPC keeps using {@link NpcGeckoAnim}, and this returns false for it rather than
 * pretending to have done something. A {@link ServerPlayer} already has that rig.
 *
 * <p>Sent to every player who can see the target, following the same nearby-players broadcast
 * {@code NpcAuraFx} uses - these packets are cosmetic and keyed by UUID, so there is nothing to
 * reconcile if one is missed.
 */
public final class NpcDmzAnim {

    /** Matches the tracking distance DragonMineZ's own cosmetics are sent at. */
    public static final double BROADCAST_RANGE = 96.0;

    /** Play as DragonMineZ KI play-and-hold (scripted studio clips). */
    public static final int FLAG_HOLD = 1;

    /** Client should call {@code stopKiAnimation} rather than only drop a queued clip. */
    public static final int FLAG_STOP = 2;

    private NpcDmzAnim() {
    }

    /** True when this NPC is drawn through the Full DragonMineZ path and can show these clips. */
    public static boolean canAnimate(LivingEntity npc) {
        return npc != null && npc.isAlive()
                && (npc instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity
                || NpcCombatProfile.hasProfile(npc)
                && NpcCombatProfile.read(npc).appearance.mode == NpcDmzAppearance.Mode.FULL);
    }

    /** NPCs in Full mode, clones, or a live server player. */
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
        String name = animation == null ? "" : animation.trim();
        if (!Bt3AnimationCatalog.isPlayable(name)) {
            return false;
        }
        return broadcast(npc, name, speed, 0);
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
        NpcAnimationPacket packet = new NpcAnimationPacket(
                target.getUUID(), animation, Math.max(0.15f, Math.min(4.0f, speed)), flags);
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
