package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Chase: fly after a target this fighter has just launched on purpose.
 *
 * <p>The window is opened only by an explicit launch (the fourth charged kick's arc). Ordinary
 * hits never open it, and arriving does not hit anyone: what follows is the player's choice.
 */
public final class V3Chase {
    static final int WINDOW_TICKS = 40;
    static final double SPEED = 3.5;

    private V3Chase() {}

    static boolean allowed(V3Window window, int ticksLeft, UUID launched, UUID current, V3State state) {
        return window == V3Window.CHASE && ticksLeft > 0 && launched != null && launched.equals(current)
                && state == V3State.IDLE;
    }

    /**
     * Chase on request, as in v2: any time the fighter is free, the target is inside the Dragon
     * Dash range and not already within reach, and the shared dash cooldown has passed.
     */
    static boolean atWill(V3State state, double distanceSquared, double range, long now, long readyTick) {
        return state == V3State.IDLE && now >= readyTick && V3TargetingRules.inRange(distanceSquared, range)
                && distanceSquared > V3Dash.ARRIVE * V3Dash.ARRIVE;
    }

    /** Called by the move that launched {@code target}. */
    static void open(ServerPlayer player, LivingEntity target) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || target == null) return;
        fighter.window = V3Window.CHASE;
        fighter.windowTarget = target.getUUID();
        fighter.windowTicksLeft = fighter.windowTicksTotal = WINDOW_TICKS;
        V3CombatServer.syncState(player);
    }

    public static boolean start(ServerPlayer player, LivingEntity target, int now) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || target == null || V3Heavy.stunned(player)) return false;
        // After a launch the chase is free and immediate; otherwise it is the at-will chase.
        boolean homing = allowed(fighter.window, fighter.windowTicksLeft, fighter.windowTarget, target.getUUID(), fighter.state);
        if (!homing && !atWill(fighter.state, player.distanceToSqr(target), V3Config.get().dragonDashRange(),
                now, fighter.dashReadyTick)) return false;
        if (!V3Motion.acquire(player, V3Motion.Owner.APPROACH, fighter.session())) return false;
        V3DashCamera.stop(player);
        if (!homing) fighter.dashReadyTick = (long) now + V3Dash.COOLDOWN_TICKS;
        fighter.travelArrive = V3Dash.ARRIVE;
        if (!V3Travel.start(player, target, Vec3.ZERO, SPEED, now)) {
            V3Motion.release(player);
            return false;
        }
        fighter.state = V3State.TRAVEL;
        // A chase has no cross of its own.
        fighter.dashCrossed = true;
        fighter.dashTarget = null;
        fighter.window = V3Window.NONE;
        fighter.windowTarget = null;
        fighter.windowTicksLeft = fighter.windowTicksTotal = 0;
        CombatFx.cue(player.serverLevel(), player.position(), CombatFxKind.DASH_LAUNCH, 1.0f);
        return true;
    }

    /** Stops a chase or a dash in flight and hands movement back. */
    public static void stop(ServerPlayer player) {
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || fighter.travelTarget == null) return;
        V3DashCamera.stop(player);
        V3Travel.stop(player);
        V3Motion.release(player);
        if (fighter.state == V3State.TRAVEL) fighter.state = V3State.IDLE;
    }
}
