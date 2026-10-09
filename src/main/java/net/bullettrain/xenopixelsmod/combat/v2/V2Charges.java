package net.bullettrain.xenopixelsmod.combat.v2;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.TriggerAnimationS2C;
import net.bullettrain.xenopixelsmod.anim.CombatStateAnim;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboInput;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Holds and releases one dedicated charged strike using the server's clock. */
final class V2Charges {
    static final String KICK_COUNT = "xenopixelsmod.v2_charged_kick_count";
    static final String PUNCH_COUNT = "xenopixelsmod.v2_charged_punch_count";
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1f, 0.72f, 0.08f), 1.1f);
    private V2Charges() {}

    static void start(ServerPlayer player, V2Fighter f, LivingEntity target, boolean kick, int now) {
        if (f.state.committed() || now < f.stunUntilTick || f.chargeStartTick >= 0
                || V2Support.refusal(player, target) != null) return;
        V2Motion.stopTravel(player, f);
        f.clearCombo();
        f.chargeStartTick = now;
        f.chargeKick = kick;
        f.chargeTargetId = target.getId();
        f.state = V2State.CHARGE;
        String hold = CombatStateAnim.resolve(player, kick ? TechniqueAnimSlot.CHARGE_KICK : TechniqueAnimSlot.CHARGE_PUNCH);
        NetworkHandler.sendToTrackingEntityAndSelf(new TriggerAnimationS2C(player.getUUID(),
                TriggerAnimationS2C.AnimationType.KI_ANIMATION, 1, player.getId(), hold), player);
    }

    static void cancel(ServerPlayer player, V2Fighter f) {
        if (f.chargeStartTick < 0) return;
        f.chargeStartTick = -1;
        f.chargeTargetId = -1;
        if (f.state == V2State.CHARGE) f.state = V2State.NEUTRAL;
        NetworkHandler.sendToTrackingEntityAndSelf(new TriggerAnimationS2C(player.getUUID(),
                TriggerAnimationS2C.AnimationType.KI_ANIMATION_STOP, 0, player.getId(), ""), player);
    }

    static void tick(ServerPlayer player, V2Fighter f, int now) {
        if (f.chargeStartTick < 0) return;
        LivingEntity target = V2Support.living(player, f.chargeTargetId);
        if (now - f.chargeStartTick > V2ChargeRules.MAX_TICKS || f.state != V2State.CHARGE
                || V2Support.dmzStunned(player) || !V2Support.emptyHands(player)
                || net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.isGuarding(player)
                || V2Lock.refusal(player, target) != null || V2Support.refusal(player, target) != null) {
            cancel(player, f);
            return;
        }
        if ((now & 3) == 0) {
            float progress = V2ChargeRules.progress(now - f.chargeStartTick);
            player.serverLevel().sendParticles(GOLD, player.getX(), player.getY() + 0.9, player.getZ(),
                    3 + (int) (progress * 9), 0.4 + progress * 0.25, 0.7, 0.4 + progress * 0.25, 0);
        }
    }

    static void release(ServerPlayer player, V2Fighter f, LivingEntity target, boolean kick,
                        V2Direction direction, int now) {
        if (f.chargeStartTick < 0 || f.chargeKick != kick || target == null
                || target.getId() != f.chargeTargetId) {
            cancel(player, f);
            return;
        }
        float charge = V2ChargeRules.progress(now - f.chargeStartTick);
        cancel(player, f);
        if (f.state.committed() || now < f.stunUntilTick || V2Support.refusal(player, target) != null) return;
        boolean teleport = false;
        if (V2ChargeRules.eligible(charge, player.distanceTo(target)) && V2Targeting.sight(player, target)) {
            String key = kick ? KICK_COUNT : PUNCH_COUNT;
            int count = V2ChargeRules.nextCount(player.getPersistentData().getInt(key));
            player.getPersistentData().putInt(key, count);
            if (count == 0) {
                Vec3 dest = V2ChargedLanding.find(player, target, kick);
                if (dest != null) {
                    float yaw = (float) (Math.toDegrees(Math.atan2(target.getZ() - dest.z,
                            target.getX() - dest.x)) - 90);
                    player.connection.teleport(dest.x, dest.y, dest.z, yaw, 0);
                    player.setYRot(yaw);
                    player.setYHeadRot(yaw);
                    player.yBodyRot = yaw;
                    V2Moves.resetTeleportMotion(player);
                    teleport = true;
                }
            }
        }
        V2Strikes.charged(player, f, kick ? ComboInput.HEAVY : ComboInput.LIGHT, target, direction, charge, teleport, now);
    }

}
