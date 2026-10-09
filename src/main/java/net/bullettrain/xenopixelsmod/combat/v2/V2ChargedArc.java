package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Collision-aware, server-owned flight. NoAI NPCs need their movement consumed explicitly. */
public final class V2ChargedArc {
    private record Flight(LivingEntity target, net.minecraft.world.level.Level level, Vec3 origin,
                          Vec3 direction, int start, boolean gravity, double distance, double apex) {}
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    private V2ChargedArc() {}

    public static void start(LivingEntity target, Vec3 direction, int now) {
        start(target, direction, now, 20, 6);
    }

    /** Explicit launch profile; distance is along the supplied direction, in blocks. */
    public static void start(LivingEntity target, Vec3 direction, int now, double distance, double apex) {
        if (!Double.isFinite(distance) || distance <= 0 || distance > 64
                || !Double.isFinite(apex) || apex < 0 || apex > 32 || direction.lengthSqr() < 1e-6) return;
        stop(target.getUUID());
        if (!CombatKnockback.canKnockBack(target)) return;
        FLIGHTS.put(target.getUUID(), new Flight(target, target.level(), target.position(), direction.normalize(), now,
                target.isNoGravity(), distance, apex));
        target.setNoGravity(true);
    }

    static void tick(int now) {
        var iterator = FLIGHTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Flight f = iterator.next().getValue();
            int tick = now - f.start;
            if (tick <= 0) continue;
            if (!f.target.isAlive() || f.target.isRemoved() || f.target.level() != f.level || tick > V2ChargeRules.ARC_TICKS
                    || !step(f.target, f.origin, f.direction, tick, f.distance, f.apex)) {
                restore(f);
                iterator.remove();
            }
        }
    }

    public static boolean step(LivingEntity target, Vec3 origin, Vec3 direction, int tick) {
        return step(target, origin, direction, tick, 20, 6);
    }

    private static boolean step(LivingEntity target, Vec3 origin, Vec3 direction, int tick, double distance, double apex) {
        if (!CombatKnockback.canKnockBack(target)) return false;
        double[] offset = V2ChargeRules.arcOffset(tick, distance, apex);
        Vec3 next = origin.add(direction.scale(offset[0])).add(0, offset[1], 0);
        Vec3 movement = next.subtract(target.position());
        if (!target.level().getWorldBorder().isWithinBounds(target.getBoundingBox().move(movement))
                || !target.level().noCollision(target, target.getBoundingBox().expandTowards(movement))) return false;
        CombatKnockback.set(target, movement);
        target.move(MoverType.SELF, movement);
        // This controller consumed the impulse; vanilla travel must not consume it again.
        CombatKnockback.set(target, Vec3.ZERO);
        if (target instanceof ServerPlayer player) {
            player.connection.teleport(target.getX(), target.getY(), target.getZ(),
                    target.getYRot(), target.getXRot());
        }
        target.resetFallDistance();
        return true;
    }

    static void stop(UUID id) {
        Flight flight = FLIGHTS.remove(id);
        if (flight != null) restore(flight);
    }

    /** Relinquishes a prior scripted launch before a different controller takes the victim. */
    public static void cancel(LivingEntity target) {
        if (target != null) stop(target.getUUID());
    }

    static void clear() {
        FLIGHTS.values().forEach(V2ChargedArc::restore);
        FLIGHTS.clear();
    }

    private static void restore(Flight f) {
        f.target.setNoGravity(f.gravity);
        CombatKnockback.set(f.target, Vec3.ZERO);
    }
}
