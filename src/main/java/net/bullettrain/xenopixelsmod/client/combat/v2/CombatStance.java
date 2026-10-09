package net.bullettrain.xenopixelsmod.client.combat.v2;

import com.dragonminez.client.events.LockOnEvent;
import com.dragonminez.common.combat.logic.player.PlayerAttackHelper;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.combat.FistInputPolicy;
import net.bullettrain.xenopixelsmod.combat.v2.V2State;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Whether this client is in a v2 fight. One rule: <b>it is locked on</b>.
 *
 * <p>XenoCombat v2 only exists while the fighter has a DragonMineZ lock on a target. With no lock
 * nothing of v2 runs: left click is DragonMineZ's ordinary punch, right click uses and places,
 * E opens the inventory, Q drops, the vanish and dash keys do nothing, a double-tap does not
 * vanish, and no prompt is drawn. With a lock, those keys become the v2 moves and every one of
 * them is made at the locked target.
 *
 * <p>That makes the lock the one deliberate act that turns combat on. Nothing a fighter did not
 * choose can do it: not a mob wandering under the crosshair, not a stray hit, not having thrown a
 * punch a moment ago.
 *
 * <p>The stance is the lock plus empty hands, because the v2 moves are all bare-handed. It is
 * evaluated once per client tick by {@link V2InputLayer} and cached, so the input events that ask
 * in between all get the same answer. The server holds every input to the same rule.
 *
 * <p>The single exception is not a move the fighter starts: someone caught in another fighter's
 * grab may break out of it with no lock of their own.
 */
public final class CombatStance {

    private static boolean active;

    private CombatStance() {}

    /** The stance as of this client tick: v2 on, locked on, hands empty. */
    public static boolean active() {
        return active;
    }

    /** The DragonMineZ lock-on target while it is alive, else null. Read live, not cached. */
    public static LivingEntity lockedTarget() {
        LivingEntity target = LockOnEvent.getLockedTarget();
        return target != null && target.isAlive() ? target : null;
    }

    /**
     * Whether v2 takes left click from DragonMineZ's own punch right now: when locked on, and
     * when held in someone's grab, where it is the way out. Read live, because the attack hooks
     * that ask run between ticks.
     */
    public static boolean claimsFists() {
        return lockedTarget() != null || V2ClientState.state() == V2State.GRABBED;
    }

    static void reset() {
        active = false;
    }

    /** Stands the stance down for the ticks the input pass does not run (a screen is open). */
    static void suspend() {
        active = false;
    }

    static boolean update(Minecraft mc) {
        LocalPlayer player = mc.player;
        active = player != null && mc.level != null && mc.screen == null
                && player.isAlive() && !player.isSpectator()
                && XenoClientConfig.bt3CombatClient && XenoServerClientState.v2Controller()
                && !(player.getVehicle() instanceof net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity)
                && handsFree(player)
                && lockedTarget() != null;
        return active;
    }

    static boolean handsFree(LocalPlayer player) {
        return FistInputPolicy.emptyHands(player.getMainHandItem().isEmpty(),
                player.getOffhandItem().isEmpty(), PlayerAttackHelper.isKiWeaponActive(player));
    }
}
