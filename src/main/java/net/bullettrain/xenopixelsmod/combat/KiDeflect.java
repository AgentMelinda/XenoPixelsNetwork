package net.bullettrain.xenopixelsmod.combat;

import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.stats.character.Resources;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Punching an incoming ki blast knocks it back at whoever threw it.
 *
 * <p>Deflection is the answer to a ki blast that guarding does not give: guard costs stamina and
 * still takes chip damage, so a ranged attacker who can spam blasts wins by attrition against a
 * defender with no way to answer. A punch that returns the blast turns the exchange around and
 * makes reading the incoming shot worth something.
 *
 * <p>Hooked into the ordinary combo attack rather than a key of its own, and checked before the
 * combo resolves. The attack that deflects is consumed — you cannot deflect and punch in the same
 * swing.
 *
 * <p><b>Priced, not gated.</b> A consumed swing alone did not stop deflection being mashed, so it
 * also costs stamina and sits behind a very short per-player cooldown. The cost is the real limit:
 * a cooldown long enough to matter would make a rapid volley undeflectable by construction, where
 * a stamina price lets a player answer every shot in a burst for exactly as long as they can
 * afford to. The cooldown only exists so a held attack button cannot clear everything in reach on
 * consecutive ticks. Failing to pay leaves the swing intact and it lands as an ordinary punch.
 *
 * <p><b>Only genuinely incoming blasts count.</b> Being in front of you is not enough — a shot has
 * to be travelling toward you. Without that, a blast already sailing past, or one two other
 * players are trading across your view, is punchable, and deflection stops being a read and
 * becomes a vacuum for any ki in the general direction you are facing.
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
    public static boolean tryDeflect(ServerPlayer player, Resources res) {
        if (!XenoServerConfig.kiDeflectEnabled) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (onCooldown(player, level)) return false;

        double reach = Math.max(0.5, XenoServerConfig.kiDeflectReach);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        AbstractKiProjectile best = null;
        double bestScore = -1.0;
        for (AbstractKiProjectile blast : level.getEntitiesOfClass(AbstractKiProjectile.class,
                new AABB(eye, eye).inflate(reach), KiDeflect::deflectable)) {
            // Never your own shot: otherwise a player could fire and immediately punch it for a
            // free speed boost, which is not a mechanic anyone asked for. Identity by UUID, not by
            // reference — getOwner() resolves lazily and a null there would have let exactly that
            // through, and it is also what makes a blast unpunchable twice in a row.
            if (blast.isOwner(player)) continue;

            Vec3 toBlast = blast.position().add(0.0, blast.getBbHeight() * 0.5, 0.0).subtract(eye);
            double distance = toBlast.length();
            if (distance > reach || distance < 1.0e-3) continue;
            Vec3 toBlastDir = toBlast.scale(1.0 / distance);

            // Must be in front. A blast passing behind the player was already dodged, and
            // letting it be punched would make deflection a panic button rather than a read.
            double facing = look.dot(toBlastDir);
            if (facing < XenoServerConfig.kiDeflectAimDot) continue;

            // Must actually be coming at you. Without this, a shot already sailing past — or one
            // someone else fired across your view — counts as deflectable, which turns a read into
            // a vacuum that sweeps up any ki in front of the player.
            Vec3 motion = blast.getDeltaMovement();
            if (motion.lengthSqr() > 1.0e-6 && motion.normalize().dot(toBlastDir) > -0.1) continue;

            // Prefer the one most directly ahead, then the nearest.
            double score = facing - distance * 0.05;
            if (score > bestScore) {
                bestScore = score;
                best = blast;
            }
        }
        if (best == null) return false;

        // Charged last, so a failed search never bills the player. Not affording it leaves the
        // swing intact, so the punch resolves as an ordinary attack instead of vanishing.
        if (!spendStamina(res)) return false;

        markCooldown(player, level);
        deflect(player, level, best);
        return true;
    }

    /**
     * Per-player deflect gate.
     *
     * <p>Deliberately short. Its only job is to stop a held attack button from clearing every
     * blast in reach on consecutive ticks; the stamina cost is what actually prices the mechanic.
     */
    private static final Map<UUID, Integer> NEXT_ALLOWED_TICK = new HashMap<>();

    private static boolean onCooldown(ServerPlayer player, ServerLevel level) {
        if (XenoServerConfig.kiDeflectCooldownTicks <= 0) return false;
        Integer next = NEXT_ALLOWED_TICK.get(player.getUUID());
        return next != null && level.getServer().getTickCount() < next;
    }

    private static void markCooldown(ServerPlayer player, ServerLevel level) {
        if (XenoServerConfig.kiDeflectCooldownTicks <= 0) return;
        NEXT_ALLOWED_TICK.put(player.getUUID(),
                level.getServer().getTickCount() + XenoServerConfig.kiDeflectCooldownTicks);
    }

    /** Drop a player's gate on logout so the map cannot grow across a server's uptime. */
    public static void forget(UUID playerId) {
        NEXT_ALLOWED_TICK.remove(playerId);
    }

    private static boolean spendStamina(Resources res) {
        float cost = XenoServerConfig.kiDeflectStaminaCost;
        if (cost <= 0f) return true;
        if (res == null) return true; // no stats to bill; never block the mechanic on missing data
        if (res.getCurrentStamina() < cost) return false;
        res.removeStamina(cost);
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

        // Turning a shot around is worth more than surviving it, and it cost stamina to do.
        blast.setKiDamage(blast.getKiDamage() * XenoServerConfig.kiDeflectDamageScale);

        // Reassign ownership, or DMZ's shouldDamage will refuse it against the original caster.
        blast.setOwner(player);
        // Re-home onto the caster, or clear homing outright when there is no caster left to chase.
        // Leaving the old target in place would keep a homing shot locked on whoever it was
        // already tracking — which, for a blast thrown at the deflector, is the deflector.
        blast.setHomingTarget(caster != null ? caster.getId() : -1);

        level.playSound(null, blastCentre.x, blastCentre.y, blastCentre.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.35f);
        level.playSound(null, blastCentre.x, blastCentre.y, blastCentre.z,
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 1.5f);
        // Cyan ring facing the way it is being sent, matching guard's "absorbed" colour family.
        CombatFx.impact(level, blastCentre, aim, CombatFx.Weight.GUARD);

        // Both sides need to read what just happened. Without a cue on the caster's end a returned
        // blast looks like their own shot inexplicably killing them.
        player.displayClientMessage(Component.literal("§bDeflected!"), true);
        if (caster instanceof ServerPlayer casterPlayer) {
            casterPlayer.displayClientMessage(Component.literal(
                    "§c" + player.getName().getString() + " deflected your ki blast"), true);
        }
    }
}
