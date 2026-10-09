package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.ChaseFlightStatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The single owner of a V3 fighter's server-driven movement.
 *
 * <p>Only the lease holder writes velocity. Taking the lease suspends gravity and tells the client
 * to stand DragonMineZ's own flight movement down (the existing chase flight-state packet, which
 * both client flight mixins already honour); releasing it puts the entry state back exactly once.
 */
public final class V3Motion {
    private static final int LANDING_GRACE_TICKS = 40;

    private V3Motion() {}

    public enum Owner { APPROACH, CROSS, STRIKE, GRAB, THROW, CINEMATIC }

    /** Pure lease state: who owns movement and what gravity was on entry. */
    public static final class Lease {
        private Owner owner;
        private UUID session;
        private boolean noGravityBefore;

        public boolean acquire(Owner wanted, UUID wantedSession, boolean noGravityNow) {
            if (wanted == null || wantedSession == null) return false;
            if (owner != null) return owner == wanted && wantedSession.equals(session);
            owner = wanted;
            session = wantedSession;
            noGravityBefore = noGravityNow;
            return true;
        }

        public Owner owner() { return owner; }

        public boolean heldBy(Owner wanted, UUID wantedSession) {
            return owner != null && owner == wanted && wantedSession != null && wantedSession.equals(session);
        }

        /** @return the gravity flag to restore, or null when nothing was held */
        public Boolean release() {
            if (owner == null) return null;
            owner = null;
            session = null;
            return noGravityBefore;
        }
    }

    public static boolean acquire(ServerPlayer player, Owner owner, UUID session) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return false;
        boolean fresh = fighter.motion.owner() == null;
        if (!fighter.motion.acquire(owner, session, player.isNoGravity())) return false;
        if (fresh) {
            player.setNoGravity(true);
            ModNetwork.sendToPlayer(player, new ChaseFlightStatePacket(true));
            if (owner == Owner.CINEMATIC) push(player, Vec3.ZERO);
        }
        return true;
    }

    public static boolean owns(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        return fighter != null && fighter.motion.owner() != null;
    }

    /** Cinematic ticks may hold the caster only while the exact cast token owns its motion. */
    public static boolean holdCinematic(ServerPlayer player, UUID castToken) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || !fighter.motion.heldBy(Owner.CINEMATIC, castToken)) return false;
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0f;
        return true;
    }

    /** Cancellation must not release a newer action's lease. */
    public static void releaseCinematic(ServerPlayer player, UUID castToken) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter != null && fighter.motion.heldBy(Owner.CINEMATIC, castToken)) release(player);
    }

    /** Safe to call at any time and any number of times. */
    public static void release(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null) return;
        Boolean noGravityBefore = fighter.motion.release();
        if (noGravityBefore == null) return;
        player.setNoGravity(noGravityBefore);
        push(player, Vec3.ZERO);
        // A travel often ends high above the ground; the landing must not be what kills.
        player.resetFallDistance();
        Bt3CombatEvents.grantFallGrace(player, LANDING_GRACE_TICKS);
        ModNetwork.sendToPlayer(player, new ChaseFlightStatePacket(false));
    }

    static void push(ServerPlayer player, Vec3 velocity) {
        player.setDeltaMovement(velocity);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.fallDistance = 0f;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
