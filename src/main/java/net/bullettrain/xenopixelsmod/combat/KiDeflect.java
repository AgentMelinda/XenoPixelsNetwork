package net.bullettrain.xenopixelsmod.combat;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Punching an incoming ki blast knocks it back at whoever threw it.
 *
 * <p>Deflection is the answer to a ki blast that guarding does not give: guard costs stamina and
 * still takes chip damage, so a ranged attacker who can spam blasts wins by attrition against a
 * defender with no way to answer. A punch that returns the blast turns the exchange around and
 * makes reading the incoming shot worth something.
 *
 * <p>Hooked into the ordinary combo attack rather than a key of its own, and checked before the
 * combo resolves. The attack that deflects is consumed — you cannot deflect and punch in the
 * same swing — which is what stops it becoming a free extra action mashed alongside the combo.
 *
 * <p>The deflected blast has its owner reassigned to the deflector. That is what makes it able
 * to hurt the original caster at all: DMZ's {@code shouldDamage} refuses a projectile against
 * its own owner, so a blast merely turned around would sail through the person who fired it.
 */
public final class KiDeflect {

    private KiDeflect() {
    }

    /**
     * Deflect the best candidate in front of the player, if there is one.
     *
     * @return true when a blast was deflected and the attack should be consumed
     */
    public static boolean tryDeflect(ServerPlayer player) {
        if (!XenoServerConfig.kiDeflectEnabled) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;

        double reach = Math.max(0.5, XenoServerConfig.kiDeflectReach);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        AbstractKiProjectile best = null;
        double bestScore = -1.0;
        for (AbstractKiProjectile blast : level.getEntitiesOfClass(AbstractKiProjectile.class,
                new AABB(eye, eye).inflate(reach), KiDeflect::deflectable)) {
            // Never your own shot: otherwise a player could fire and immediately punch it for a
            // free speed boost, which is not a mechanic anyone asked for.
            if (blast.getOwner() == player) continue;

            Vec3 toBlast = blast.position().add(0.0, blast.getBbHeight() * 0.5, 0.0).subtract(eye);
            double distance = toBlast.length();
            if (distance > reach || distance < 1.0e-3) continue;

            // Must be in front. A blast passing behind the player was already dodged, and
            // letting it be punched would make deflection a panic button rather than a read.
            double facing = look.dot(toBlast.scale(1.0 / distance));
            if (facing < XenoServerConfig.kiDeflectAimDot) continue;

            // Prefer the one most directly ahead, then the nearest.
            double score = facing - distance * 0.05;
            if (score > bestScore) {
                bestScore = score;
                best = blast;
            }
        }
        if (best == null) return false;

        deflect(player, level, best);
        return true;
    }

    /** Skip anything already gone, and anything mid-clash — that is a different mechanic. */
    private static boolean deflectable(AbstractKiProjectile blast) {
        return blast.isAlive() && !blast.isClashLocked();
    }

    private static void deflect(ServerPlayer player, ServerLevel level, AbstractKiProjectile blast) {
        Entity caster = blast.getOwner();
        Vec3 blastCentre = blast.position().add(0.0, blast.getBbHeight() * 0.5, 0.0);

        // Aim where the deflector is looking, not simply back the way it came. Returning it at
        // the caster is the common case and happens naturally, but a player who leads their
        // punch can redirect the shot somewhere else, which is more interesting than a mirror.
        Vec3 aim = player.getLookAngle().normalize();
        double speed = Math.max(XenoServerConfig.kiDeflectMinSpeed,
                blast.getDeltaMovement().length() * XenoServerConfig.kiDeflectSpeedScale);
        blast.setDeltaMovement(aim.scale(speed));
        blast.hasImpulse = true;

        // Reassign ownership, or DMZ's shouldDamage will refuse it against the original caster.
        blast.setOwner(player);
        // Re-home onto the caster when it was a homing shot; harmless otherwise.
        if (caster != null) blast.setHomingTarget(caster.getId());

        level.playSound(null, blastCentre.x, blastCentre.y, blastCentre.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.35f);
        level.playSound(null, blastCentre.x, blastCentre.y, blastCentre.z,
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 1.5f);
        // Cyan ring facing the way it is being sent, matching guard's "absorbed" colour family.
        CombatFx.impact(level, blastCentre, aim, CombatFx.Weight.GUARD);
    }
}
