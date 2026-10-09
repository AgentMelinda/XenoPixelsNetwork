package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.Bt3Landing;
import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerMode;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * V3 defence: the directional vanish and the counter it becomes when timed against a hit.
 *
 * <p>Guard itself is the controller-independent guard the server already runs (stamina per
 * blocked hit, guard break); V3 only reads the same key. There is no Step in V3.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class V3Defense {
    static final int VANISH_COOLDOWN_TICKS = 30;
    static final int COUNTER_WINDOW_TICKS = 12;

    private V3Defense() {}

    static boolean vanishAllowed(long now, long readyTick, boolean counterOpen) {
        return counterOpen || now >= readyTick;
    }

    static int side(V3Direction direction) {
        return direction == V3Direction.LEFT ? -1 : direction == V3Direction.RIGHT ? 1 : 0;
    }

    /** A real hit from the fighter's own locked target, taken while free to answer it. */
    static boolean opensCounter(UUID lockedTarget, UUID attacker, float damage, V3State state) {
        return lockedTarget != null && lockedTarget.equals(attacker) && damage > 0f && Float.isFinite(damage)
                && state == V3State.IDLE;
    }

    public static boolean handle(ServerPlayer player, V3Input input, V3Direction direction, int now) {
        if (input != V3Input.VANISH && input != V3Input.COUNTER) return false;
        V3Fighter fighter = V3FighterStore.peek(player);
        if (fighter == null || V3Heavy.stunned(player)
                || (fighter.state != V3State.IDLE && fighter.state != V3State.TRAVEL)) return false;
        LivingEntity target = V3Targeting.resolve(player);
        if (target == null) return false;
        boolean counter = fighter.window == V3Window.COUNTER && fighter.windowTicksLeft > 0
                && target.getUUID().equals(fighter.windowTarget);
        if (input == V3Input.COUNTER && !counter) return false;
        if (!vanishAllowed(now, fighter.vanishReadyTick, counter)) return false;
        // A fixed candidate list behind the target; nowhere to stand means no vanish and no cost.
        Vec3 landing = Bt3Landing.vanishLanding(player, target, side(direction));
        if (landing == null || !V3ChunkWindow.update(player, landing)) return false;

        V3Chase.stop(player);
        Vec3 from = player.position();
        float yaw = (float) (Math.toDegrees(Math.atan2(target.getZ() - landing.z, target.getX() - landing.x)) - 90);
        player.connection.teleport(landing.x, landing.y, landing.z, yaw, 0);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.fallDistance = 0f;
        fighter.vanishReadyTick = (long) now + VANISH_COOLDOWN_TICKS;
        if (counter) {
            // The reward for the read: the heavy is ready the moment the fighter reappears.
            fighter.heavyReadyTick = now;
            fighter.window = V3Window.NONE;
            fighter.windowTarget = null;
            fighter.windowTicksLeft = fighter.windowTicksTotal = 0;
        }
        CombatFx.cue(player.serverLevel(), from, CombatFxKind.DASH_LAUNCH, 0.6f);
        V3CombatServer.syncState(player);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer defender)
                || XenoServerConfig.controllerMode() != CombatControllerMode.V3) return;
        V3Fighter fighter = V3FighterStore.peek(defender);
        if (fighter == null || !(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (!opensCounter(fighter.approvedTarget, attacker.getUUID(), event.getNewDamage(), fighter.state)) return;
        fighter.window = V3Window.COUNTER;
        fighter.windowTarget = attacker.getUUID();
        fighter.windowTicksLeft = fighter.windowTicksTotal = COUNTER_WINDOW_TICKS;
        V3CombatServer.syncState(defender);
    }
}
