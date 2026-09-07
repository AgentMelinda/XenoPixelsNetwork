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
 * Plays one of this mod's combat clips on an NPC.
 *
 * <p>Only NPCs drawn in Full DragonMineZ appearance can show these: that mode renders through a
 * synthetic player, which is the only thing DragonMineZ's animation system will pose. A humanoid or
 * Gecko custom-model NPC keeps using {@link NpcGeckoAnim}, and this returns false for it rather
 * than pretending to have done something.
 *
 * <p>Sent to every player who can see the NPC, following the same nearby-players broadcast
 * {@code NpcAuraFx} uses - these packets are cosmetic and keyed by UUID, so there is nothing to
 * reconcile if one is missed.
 */
public final class NpcDmzAnim {

    /** Matches the tracking distance DragonMineZ's own cosmetics are sent at. */
    private static final double BROADCAST_RANGE = 96.0;

    private NpcDmzAnim() {
    }

    /** True when this NPC is drawn through the Full DragonMineZ path and can show these clips. */
    public static boolean canAnimate(LivingEntity npc) {
        return npc != null && npc.isAlive()
                && (npc instanceof net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity
                || NpcCombatProfile.hasProfile(npc)
                && NpcCombatProfile.read(npc).appearance.mode == NpcDmzAppearance.Mode.FULL);
    }

    public static boolean play(LivingEntity npc, String animation) {
        return play(npc, animation, 1.0f);
    }

    /**
     * @return false when the NPC cannot show these clips, or the name is not one this mod ships -
     *         a script gets an answer either way rather than silence
     */
    public static boolean play(LivingEntity npc, String animation, float speed) {
        if (!canAnimate(npc) || !(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        String name = animation == null ? "" : animation.trim();
        if (!Bt3AnimationCatalog.isPlayable(name)) {
            return false;
        }
        NpcAnimationPacket packet = new NpcAnimationPacket(
                npc.getUUID(), name, Math.max(0.15f, Math.min(4.0f, speed)));
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(npc) <= BROADCAST_RANGE * BROADCAST_RANGE) {
                ModNetwork.sendToPlayer(viewer, packet);
            }
        }
        // Nobody in range is still a success: the move happened, there was simply no one to show
        // it to. Only an unplayable name or an NPC that cannot show these clips is a failure.
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
