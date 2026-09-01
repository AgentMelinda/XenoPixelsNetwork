package net.bullettrain.xenopixelsmod.combat.fx;

import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.CombatFxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side impact effects: the shockwave discs, hit sparks and camera cues that give a blow
 * weight it does not otherwise have.
 *
 * <p>Combat effects used to be written inline at each call site in {@code Bt3CombatPacket} as a
 * handful of {@code sendParticles} calls, which meant every move invented its own look and no
 * two hits read alike. Everything visual now goes through {@link #impact} so that a heavy is a
 * heavy wherever it is thrown from.
 *
 * <p><b>The disc is the important part.</b> A burst of particles at a point reads as "something
 * happened here"; a flat ring expanding in the plane the blow struck reads as a direction and a
 * force, which is what the fighting games this is modelled on actually draw. The ring is built
 * perpendicular to the blow — so a punch to the chest throws a disc facing the puncher — rather
 * than as a horizontal ring on the ground.
 *
 * <p>Everything here is server-authoritative and broadcast to nearby clients; nothing is
 * predicted. Effects are budgeted per hit ({@link Weight#ringPoints}) because a combo lands
 * several times a second and an unbudgeted ring is a frame-rate problem, not a flourish.
 */
public final class CombatFx {

    /** Beyond this, an impact is not worth a packet: it is inaudible and off-screen anyway. */
    private static final double BROADCAST_RADIUS = 48.0;
    private static final double BROADCAST_RADIUS_SQ = BROADCAST_RADIUS * BROADCAST_RADIUS;

    private static final float[] RING_WHITE = {0.92f, 0.95f, 1.0f};
    private static final float[] RING_GOLD = {1.0f, 0.83f, 0.35f};
    private static final float[] RING_CYAN = {0.35f, 0.82f, 1.0f};
    private static final float[] CORE_WHITE = {1.0f, 0.98f, 0.86f};

    private CombatFx() {
    }

    /**
     * How hard a blow reads. Each weight fixes the whole response together — ring size, particle
     * budget, and the shake the client applies — so the tiers stay distinguishable instead of
     * drifting into one another as call sites are added.
     */
    public enum Weight {
        /** Combo jab. Fires several times a second, so it stays cheap and quiet. */
        LIGHT(CombatFxKind.IMPACT_LIGHT, 0.55, 10, 0.45f, RING_WHITE),
        /** Finisher, charged attack, slam. */
        HEAVY(CombatFxKind.IMPACT_HEAVY, 1.15, 20, 1.0f, RING_WHITE),
        /** Ultimate. Nothing else is allowed to reach this. */
        ULTIMATE(CombatFxKind.IMPACT_ULTIMATE, 2.10, 34, 1.8f, RING_GOLD),
        /** Absorbed by guard: a wide, shallow, unmistakably cyan push-back. */
        GUARD(CombatFxKind.GUARD_BLOCK, 0.95, 18, 0.6f, RING_CYAN);

        private final CombatFxKind kind;
        /** Ring radius in blocks. */
        private final double radius;
        /** Particles spawned around the ring. The per-hit budget. */
        private final int ringPoints;
        /** Client shake/flash scale; see CombatFxClient. */
        private final float intensity;
        private final float[] ring;

        Weight(CombatFxKind kind, double radius, int ringPoints, float intensity,
               float[] ring) {
            this.kind = kind;
            this.radius = radius;
            this.ringPoints = ringPoints;
            this.intensity = intensity;
            this.ring = ring;
        }
    }

    /**
     * Full impact at a point: shockwave disc, hit sparks, and the camera cue to nearby clients.
     *
     * @param normal direction the blow travelled, used as the disc's normal. Zero-length falls
     *               back to a horizontal disc rather than producing a degenerate basis.
     */
    public static void impact(ServerLevel level, Vec3 pos, Vec3 normal, Weight weight) {
        Vec3 dir = safeNormal(normal);
        shockwave(level, pos, dir, weight.radius, weight.ringPoints, weight.ring);
        sparks(level, pos, dir, weight);
        broadcast(level, pos, dir, weight.kind, weight.intensity);
    }

    /** Impact centred on an entity's mid-height, which is where a blow reads as landing. */
    public static void impact(ServerLevel level, Entity target, Vec3 normal, Weight weight) {
        impact(level, target.position().add(0.0, target.getBbHeight() * 0.55, 0.0), normal, weight);
    }

    /**
     * A cue with no geometry: the counter reversal and the vanish clap, which are about the
     * instant rather than about a point in the world taking force.
     */
    public static void cue(ServerLevel level, Vec3 pos, CombatFxKind kind, float intensity) {
        broadcast(level, pos, new Vec3(0.0, 1.0, 0.0), kind, intensity);
    }

    /**
     * Flat disc in the plane perpendicular to {@code dir}, stamped with DMZ sparks.
     * Punch particles take RGB in the speed slots, so these sit on the ring rather
     * than flying out as vanilla dust.
     */
    private static void shockwave(ServerLevel level, Vec3 pos, Vec3 dir, double radius,
                                  int points, float[] rgb) {
        Vec3 right = orthogonal(dir);
        Vec3 up = dir.cross(right).normalize();
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0) * i / points;
            Vec3 at = pos.add(right.scale(Math.cos(angle) * radius * 0.55)
                    .add(up.scale(Math.sin(angle) * radius * 0.55)));
            DmzHitParticles.spark(level, at.x, at.y, at.z, rgb[0], rgb[1], rgb[2]);
        }
    }

    /** Contact stamp plus a short spray of DMZ sparks back along the blow. */
    private static void sparks(ServerLevel level, Vec3 pos, Vec3 dir, Weight weight) {
        if (weight == Weight.GUARD) {
            DmzHitParticles.guard(level, pos.x, pos.y, pos.z,
                    weight.ring[0], weight.ring[1], weight.ring[2]);
        } else {
            DmzHitParticles.punch(level, pos.x, pos.y, pos.z,
                    CORE_WHITE[0], CORE_WHITE[1], CORE_WHITE[2]);
        }
        int count = Math.max(2, weight.ringPoints / 5);
        Vec3 back = dir.reverse().scale(0.35);
        for (int i = 0; i < count; i++) {
            DmzHitParticles.spark(level,
                    pos.x + back.x * i / count,
                    pos.y + back.y * i / count,
                    pos.z + back.z * i / count,
                    weight.ring[0], weight.ring[1], weight.ring[2]);
        }
    }

    /**
     * Send the camera cue to every player close enough to see it.
     *
     * <p>Filtered here rather than client-side so a busy combo does not put a packet per hit on
     * every connection in the dimension. The client scales the response by distance again, so
     * the edge of this radius is a fade rather than a cliff.
     */
    private static void broadcast(ServerLevel level, Vec3 pos, Vec3 dir,
                                  CombatFxKind kind, float intensity) {
        CombatFxPacket packet = new CombatFxPacket(kind, pos, dir, intensity);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.position().distanceToSqr(pos) > BROADCAST_RADIUS_SQ) continue;
            ModNetwork.sendToPlayer(viewer, packet);
        }
    }

    /** Normalised blow direction, or world up when the caller had none to give. */
    private static Vec3 safeNormal(Vec3 normal) {
        if (normal == null) return new Vec3(0.0, 1.0, 0.0);
        double length = normal.length();
        if (!Double.isFinite(length) || length < 1.0e-4) return new Vec3(0.0, 1.0, 0.0);
        return normal.scale(1.0 / length);
    }

    /**
     * Any unit vector perpendicular to {@code dir}.
     *
     * <p>Crossing with world up is degenerate for a vertical blow — an uppercut or a slam, both
     * of which this system fires on — so that case crosses with world east instead.
     */
    private static Vec3 orthogonal(Vec3 dir) {
        Vec3 reference = Math.abs(dir.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        return dir.cross(reference).normalize();
    }
}
