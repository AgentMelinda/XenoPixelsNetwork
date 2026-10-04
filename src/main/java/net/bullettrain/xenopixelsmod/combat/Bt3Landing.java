package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where a vanish, a chase or a kick lands - pure geometry shared by the player's BT3 moves and the
 * NPC brain's (2026-09-30).
 *
 * <p>These lived in {@code network.Bt3CombatPacket}, so the NPC moves (NpcCombatMoves, NpcChargeMoves)
 * reached into a network packet for their math. The XenoNPCs release ships that packet only as a
 * stub, and there NPC vanish, chase and kick-launch quietly did nothing. Bt3CombatPacket keeps its
 * old methods as forwards to these, so the player code is unchanged.
 */
public final class Bt3Landing {

    private Bt3Landing() {
    }

    /** Hit reach for charged kicks; S-hold extends range. */
    public static double kickHitRange(float charge, int verticalBias) {
        double base = Math.max(5.5, XenoServerConfig.chargeAttackRange + 0.75);
        if (verticalBias < 0) {
            base += XenoServerConfig.kickDownRangeBonus * (0.75 + 0.35 * charge);
        }
        return base;
    }

    /**
     * +1 W = launch target upward, -1 S = smash downward, 0 = default arc.
     */
    public static Vec3 kickTargetLaunch(Vec3 awayFlat, float charge, int verticalBias) {
        double horiz = (0.9 + charge) * (verticalBias == 0 ? 1.0 : 0.55)
                * Math.max(0.1, XenoServerConfig.kickKnockbackScale);
        double up;
        if (verticalBias > 0) {
            double scale = Math.max(0.1, XenoServerConfig.kickKnockbackScale);
            double[] d = net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.launcherDelta(
                    awayFlat.x, awayFlat.z,
                    XenoServerConfig.comboLauncherHoriz * scale,
                    XenoServerConfig.comboLauncherUp);
            return new Vec3(d[0], d[1], d[2]);
        } else if (verticalBias < 0) {
            up = -XenoServerConfig.kickDownLaunch * (0.7 + charge * 0.8);
            horiz *= 0.65;
        } else {
            return ballArcLaunch(awayFlat, charge);
        }
        return awayFlat.normalize().scale(horiz).add(0, up, 0);
    }

    /** Ballistic hang-time arc for kicks (mash + charged). */
    public static Vec3 ballArcLaunch(Vec3 awayFlat, float charge) {
        float c = Math.max(0.25f, Math.min(1.0f, charge));
        double scale = Math.max(0.1, XenoServerConfig.kickKnockbackScale);
        double horiz = Math.max(0.7, XenoServerConfig.comboLauncherHoriz * 1.5) * scale * (0.75 + 0.4 * c);
        double up = Math.max(1.5, XenoServerConfig.comboLauncherUp * 0.95) * (0.8 + 0.35 * c);
        double[] d = net.bullettrain.xenopixelsmod.combat.Bt3ComboChoreography.launcherDelta(
                awayFlat.x, awayFlat.z, horiz, up);
        return new Vec3(d[0], d[1], d[2]);
    }

    /**
     * BT3 vanish: land just past the target (their back relative to you),
     * offset left (A) or right (D). Always at the target's height so you don't
     * stay sky-high and "fly away".
     *
     * @param side -1 left, +1 right, 0 center-behind
     */
    public static Vec3 vanishBehind(Entity player, LivingEntity target) {
        return vanishBehind(player, target, 0);
    }

    public static Vec3 vanishBehind(Entity player, LivingEntity target, int side) {
        return vanishPoint(
                player.getX(), player.getZ(),
                target.getX(), target.getY(), target.getZ(),
                target.yBodyRot, side,
                Math.max(0.0, XenoServerConfig.vanishGap),
                Math.max(0.0, XenoServerConfig.vanishSide),
                Math.max(0.0, XenoServerConfig.vanishNearField));
    }

    /**
     * The vanish landing point, as pure geometry.
     *
     * <p>At range the landing direction is the line you came in on, so you end up past the target
     * on the far side from where you stood — the original behaviour, reproduced exactly once
     * {@code sep >= nearField}.
     *
     * <p>Up close that line stops meaning anything. It used to be trusted down to a hundredth of a
     * block, and a vector that short is noise: it flips between frames, and the client — which
     * predicts this same landing from positions roughly a hundred milliseconds behind the
     * server's — could compute a direction opposite to the one the server picked and get yanked
     * across the target. Hovering directly above or below someone made it worse still, because the
     * horizontal separation there is near zero while the fight is very much close range. So the
     * closer you are, the more the direction comes from the target's own body facing, which is
     * both the spot a vanish is supposed to land on and a value that is synchronised between
     * client and server in a way a sub-block position delta is not. The two blend, so there is no
     * snap as you cross the boundary, and {@code nearField} of zero disables the near-field
     * behaviour entirely.
     *
     * @param targetBodyYawDeg the target's {@code yBodyRot}
     * @param side             -1 left, +1 right, 0 centre-behind
     */
    public static Vec3 vanishPoint(double px, double pz, double tx, double ty, double tz,
                                   float targetBodyYawDeg, int side,
                                   double gap, double sideOff, double nearField) {
        double yaw = Math.toRadians(targetBodyYawDeg);
        // Minecraft yaw: facing = (-sin, cos), so the target's back is the negation of that.
        double backX = Math.sin(yaw);
        double backZ = -Math.cos(yaw);

        double ax = tx - px;
        double az = tz - pz;
        double sep = Math.sqrt(ax * ax + az * az);

        double dirX;
        double dirZ;
        if (sep < 1.0e-6) {
            dirX = backX;
            dirZ = backZ;
        } else {
            double t = nearField <= 0.0 ? 1.0 : Math.min(1.0, sep / nearField);
            double bx = ax / sep * t + backX * (1.0 - t);
            double bz = az / sep * t + backZ * (1.0 - t);
            double len = Math.sqrt(bx * bx + bz * bz);
            if (len < 1.0e-6) {
                // Approach and back point opposite ways and cancelled: you are already standing at
                // their back while they face you, and their back is the answer anyway.
                dirX = backX;
                dirZ = backZ;
            } else {
                dirX = bx / len;
                dirZ = bz / len;
            }
        }

        double rightX = -dirZ;
        double rightZ = dirX;
        double s = side < 0 ? -sideOff : (side > 0 ? sideOff : 0.0);
        return new Vec3(
                tx + dirX * gap + rightX * s,
                ty,
                tz + dirZ * gap + rightZ * s);
    }

    /**
     * {@link #vanishBehind} moved off anything solid.
     *
     * <p>Nothing on the vanish path used to check this, so a vanish aimed into terrain teleported
     * the fighter into the terrain. The candidates are a fixed list rather than a search, because
     * the attacking client runs this too in order to predict its own landing and any difference in
     * the order the two sides try spots shows up as a rubber-band. A vanish with nowhere to go
     * leaves the fighter standing where they were, which is a wasted move rather than a
     * suffocation.
     */
    public static Vec3 vanishLanding(Entity player, LivingEntity target, int side) {
        Vec3 preferred = vanishBehind(player, target, side);
        if (!XenoServerConfig.vanishOpenSpotSearch || isSpotOpen(player, preferred)) {
            return preferred;
        }
        Vec3 centre = vanishBehind(player, target, 0);
        double dirX = centre.x - target.getX();
        double dirZ = centre.z - target.getZ();
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len > 1.0e-6) {
            double rightX = -dirZ / len;
            double rightZ = dirX / len;
            for (double lateral : new double[]{0.75, -0.75, 1.5, -1.5}) {
                Vec3 candidate = new Vec3(
                        preferred.x + rightX * lateral, preferred.y, preferred.z + rightZ * lateral);
                if (isSpotOpen(player, candidate)) {
                    return candidate;
                }
            }
            for (double shrink : new double[]{0.75, 0.5}) {
                Vec3 candidate = new Vec3(
                        target.getX() + (preferred.x - target.getX()) * shrink,
                        preferred.y,
                        target.getZ() + (preferred.z - target.getZ()) * shrink);
                if (isSpotOpen(player, candidate)) {
                    return candidate;
                }
            }
        }
        if (side != 0) {
            Vec3 mirrored = vanishBehind(player, target, -side);
            if (isSpotOpen(player, mirrored)) {
                return mirrored;
            }
        }
        return player.position();
    }

    /** Land on the target (or {@link XenoServerConfig#chaseStopGap} short), at their height. */
    public static Vec3 chaseLanding(Entity player, LivingEntity target) {
        Vec3 flat = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
        if (flat.lengthSqr() < 1.0e-4) {
            flat = new Vec3(0, 0, 1);
        } else {
            flat = flat.normalize();
        }
        double gap = Math.max(0.0, XenoServerConfig.chaseStopGap);
        return new Vec3(
                target.getX() - flat.x * gap,
                target.getY(),
                target.getZ() - flat.z * gap);
    }

    public static boolean isSpotOpen(Entity player, Vec3 pos) {
        if (player == null || pos == null || player.level() == null) {
            return false;
        }
        var box = player.getBoundingBox().move(
                pos.x - player.getX(),
                pos.y - player.getY(),
                pos.z - player.getZ());
        return player.level().noCollision(player, box);
    }
}
