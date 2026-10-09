package net.bullettrain.xenopixelsmod.combat.v3.ki;

import com.dragonminez.common.init.MainSounds;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.bullettrain.xenopixelsmod.combat.v3.V3CombatServer;
import net.bullettrain.xenopixelsmod.combat.v3.V3Direction;
import net.bullettrain.xenopixelsmod.combat.v3.V3LoadedVisibility;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3KiVisualPacket.Phase;
import net.bullettrain.xenopixelsmod.fx.effek.XenoEffects;
import net.bullettrain.xenopixelsmod.fx.ki.KiLook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Combat V3's own ki: waves, lasers, orbs, volleys and discs, simulated on the server and drawn
 * with the mod's HD Effekseer effects ({@link KiLook}; {@code effeks/ki}, built by
 * {@code tools/effekseer}).
 *
 * <p>A shot is not an entity. It flies at the one target its cast was approved for, hurts only
 * that target, stops at a wall and never breaks a block. It moves only through loaded chunks and
 * loads none. Each shot makes one sound when it leaves and one when it lands.
 */
public final class V3KiShots {
    private static final int HIT_EVERY_TICKS = 4;
    private static final int FADE_TICKS = 10;
    private static final Set<Shot> SHOTS = new LinkedHashSet<>();
    private record Charge(UUID owner, UUID cast, ServerLevel level, V3KiStyle style, Vec3 pos) {}
    private static final java.util.Map<UUID, Charge> CHARGES = new java.util.LinkedHashMap<>();

    private V3KiShots() {}

    private static final class Shot {
        final UUID visual = UUID.randomUUID();
        final UUID owner;
        final UUID target;
        final UUID cast;
        final UUID session;
        final ServerLevel level;
        final V3KiStyle style;
        final long launch;
        final float damage;
        final boolean last;
        final int side;
        boolean launched;
        Vec3 origin;
        Vec3 pos;
        double reach;
        int drawn;
        boolean arrived;
        int hitsLeft;
        long nextHit;
        long endsAt = Long.MAX_VALUE;

        Shot(ServerPlayer owner, LivingEntity target, UUID cast, UUID session, V3KiStyle style, long launch,
             float damage, boolean last, int side) {
            this.owner = owner.getUUID();
            this.target = target.getUUID();
            this.cast = cast;
            this.session = session;
            this.level = owner.serverLevel();
            this.style = style;
            this.launch = launch;
            this.damage = damage;
            this.last = last;
            this.side = side;
        }
    }

    /** The energy gathering in the caster's hands before the release. */
    public static void charge(ServerPlayer player, LivingEntity target, V3KiStyle style) {
        charge(player, target, style, null);
    }

    public static void charge(ServerPlayer player, LivingEntity target, V3KiStyle style, UUID cast) {
        if (!XenoServerConfig.kiAttackVisual().usesOwnedHdVisuals()) {
            Vec3 at = muzzle(player, centre(target), 0);
            CHARGES.put(player.getUUID(), new Charge(player.getUUID(), cast, player.serverLevel(), style, at));
            V3KiVisuals.send(player.serverLevel(), player.getUUID(), player.getUUID(), Phase.CHARGE,
                    style, at, centre(target).subtract(at).normalize(), 0);
            return;
        }
        draw(player.serverLevel(), style.asset(KiLook.Part.CHARGE), muzzle(player, centre(target), 0), null,
                Math.max(0.8f, style.size()));
    }

    public static void fire(ServerPlayer player, LivingEntity target, UUID cast, UUID session,
                            V3KiStyle style, long now) {
        if (cast == null || session == null) return;
        removeCharge(player.getUUID(), null);
        int shots = Math.max(1, style.shots());
        float each = V3KiPath.share(style.damage(), style.beam() ? style.hits() : shots);
        for (int i = 0; i < shots; i++) {
            SHOTS.add(new Shot(player, target, cast, session, style, now + V3KiPath.volleyDelay(i), each, i == shots - 1,
                    shots > 1 ? V3KiPath.volleySide(i) : 0));
        }
    }

    /** Once per server tick. */
    public static void tick(MinecraftServer server, long now) {
        tickPending(SHOTS, shot -> {
            boolean finished = step(server, shot, now);
            if (finished) removeVisual(shot);
            return finished;
        });
    }

    /** Damage hooks may cancel shots or queue another cast while a shot is stepping. */
    static <T> void tickPending(Collection<T> pending, Predicate<T> step) {
        for (T shot : List.copyOf(pending)) {
            if (pending.contains(shot) && step.test(shot)) pending.remove(shot);
        }
    }

    /** Server stopping or the controller leaving V3: nothing stays in flight. */
    public static void clear() {
        for (Shot shot : SHOTS) removeVisual(shot);
        for (Charge charge : List.copyOf(CHARGES.values())) removeCharge(charge.owner, null);
        SHOTS.clear();
    }

    public static void cancelCast(UUID owner, UUID cast) {
        if (owner == null || cast == null) return;
        removeCharge(owner, cast);
        SHOTS.removeIf(shot -> {
            if (!ownedBy(shot.owner, shot.cast, owner, cast)) return false;
            removeVisual(shot); return true;
        });
    }

    public static void cancelOwner(UUID owner) {
        if (owner != null) removeCharge(owner, null);
        if (owner != null) SHOTS.removeIf(shot -> {
            if (!owner.equals(shot.owner)) return false;
            removeVisual(shot); return true;
        });
    }

    private static void removeVisual(Shot shot) {
        if (XenoServerConfig.kiAttackVisual().usesOwnedHdVisuals()) return;
        V3KiVisuals.send(shot.level, shot.visual, shot.owner, Phase.REMOVE, shot.style,
                shot.pos == null ? Vec3.ZERO : shot.pos, null, 0);
    }

    private static void removeCharge(UUID owner, UUID cast) {
        Charge charge = CHARGES.get(owner);
        if (charge == null || cast != null && !cast.equals(charge.cast)) return;
        CHARGES.remove(owner);
        V3KiVisuals.send(charge.level, owner, owner, Phase.REMOVE, charge.style, charge.pos, null, 0);
    }

    static boolean ownedBy(UUID shotOwner, UUID shotCast, UUID owner, UUID cast) {
        return owner != null && cast != null && owner.equals(shotOwner) && cast.equals(shotCast);
    }

    public static int live() {
        return SHOTS.size();
    }

    /** @return true when the shot is finished */
    private static boolean step(MinecraftServer server, Shot shot, long now) {
        ServerPlayer owner = server.getPlayerList().getPlayer(shot.owner);
        if (owner == null || owner.serverLevel() != shot.level || !owner.isAlive()) return true;
        if (!V3CombatServer.canContinueTechnique(owner, shot.session, shot.target)) return true;
        if (now < shot.launch) return false;
        if (V3TechniqueRuntime.kiExpired(shot.launch, now)) return true;
        if (!(shot.level.getEntity(shot.target) instanceof LivingEntity target) || !target.isAlive()) return true;
        Vec3 aim = centre(target);
        if (!shot.launched) {
            shot.launched = true;
            shot.origin = shot.pos = muzzle(owner, aim, shot.side);
            sound(shot.level, shot.origin, launchSound(shot.style.kind()));
        }
        return shot.style.beam() ? beam(owner, target, shot, aim, now) : projectile(owner, target, shot, aim, now);
    }

    private static boolean projectile(ServerPlayer owner, LivingEntity target, Shot shot, Vec3 aim, long now) {
        V3KiStyle style = shot.style;
        double radius = style.radius() + 0.5 * target.getBbWidth();
        Vec3 next = V3KiPath.advance(shot.pos, aim, style.speed());
        if (!loaded(shot.level, next)) return true;
        boolean reaches = V3KiPath.reaches(shot.pos, aim, style.speed(), radius);
        Vec3 endpoint = reaches ? aim : next;
        if (!V3LoadedVisibility.clear(owner, shot.pos, endpoint)) {
            burst(shot, endpoint);
            return true;
        }
        if (reaches) {
            land(owner, target, shot, aim);
            return true;
        }
        shot.pos = next;
        if (!XenoServerConfig.kiAttackVisual().usesOwnedHdVisuals()) {
            V3KiVisuals.send(shot.level, shot.visual, shot.owner, Phase.FLIGHT, style, next,
                    aim.subtract(next).normalize(), 0);
            return false;
        }
        // A giant ball's pulses last longer, so it is sent less often.
        if (style.kind() != KiLook.Kind.GIANT_BALL || (now - shot.launch) % KiLook.GIANT_EVERY_TICKS == 0) {
            draw(shot.level, style.asset(KiLook.flight(style.kind())), next, null, style.size());
        }
        return false;
    }

    private static boolean beam(ServerPlayer owner, LivingEntity target, Shot shot, Vec3 aim, long now) {
        V3KiStyle style = shot.style;
        Vec3 gap = aim.subtract(shot.origin);
        double distance = Math.max(1.0e-3, gap.length());
        Vec3 direction = gap.scale(1.0 / distance);
        if (distance > V3KiPath.BEAM_RANGE + 0.5 * target.getBbWidth()) {
            burst(shot, shot.origin.add(direction.scale(Math.min(shot.reach, V3KiPath.BEAM_RANGE))));
            return true;
        }
        if (shot.arrived && !V3LoadedVisibility.clear(owner, shot.origin, aim)) {
            burst(shot, aim);
            return true;
        }
        if (!shot.arrived && shot.endsAt == Long.MAX_VALUE) {
            double reach = V3KiPath.beamReach(shot.reach, distance);
            Vec3 head = shot.origin.add(direction.scale(reach));
            if (!loaded(shot.level, head)) return true;
            // Recheck the whole occupied beam because a moving target can rotate the beam across
            // an obstruction even when the newly extended tip itself is clear.
            if (!V3LoadedVisibility.clear(owner, shot.origin, head)) {
                burst(shot, head);
                return true;
            } else {
                shot.reach = reach;
                if (reach >= distance - 0.5 * target.getBbWidth() - 0.01) {
                    shot.arrived = true;
                    shot.hitsLeft = Math.max(1, style.hits());
                    shot.nextHit = now;
                } else if (reach >= V3KiPath.BEAM_RANGE) {
                    shot.endsAt = now + FADE_TICKS;
                }
            }
        }
        if (!XenoServerConfig.kiAttackVisual().usesOwnedHdVisuals()) {
            V3KiVisuals.send(shot.level, shot.visual, shot.owner, Phase.FLIGHT, style,
                    shot.origin, direction, shot.reach);
        }
        double spacing = V3KiPath.SEGMENT * style.size();
        int lengths = V3KiPath.lengths(shot.reach, spacing);
        // New lengths appear at once; the whole beam is refreshed together before it would fade.
        long age = now - shot.launch;
        boolean spiral = style.kind() == KiLook.Kind.BEAM && KiLook.twoTone(style.core(), style.edge());
        for (int i = KiLook.firstLength(age, shot.drawn, lengths); XenoServerConfig.kiAttackVisual().usesOwnedHdVisuals() && i < lengths; i++) {
            Vec3 at = V3KiPath.lengthStart(shot.origin, direction, i, spacing);
            draw(shot.level, style.asset(KiLook.flight(style.kind())), at, direction, style.size());
            if (spiral) draw(shot.level, KiLook.asset(KiLook.Part.SPIRAL, style.edge()), at, direction, style.size());
        }
        shot.drawn = lengths;
        if (XenoServerConfig.kiAttackVisual().usesOwnedHdVisuals() && age % KiLook.MUZZLE_EVERY_TICKS == 0) {
            draw(shot.level, style.asset(KiLook.Part.WAVE_MUZZLE), shot.origin, direction,
                    style.kind() == KiLook.Kind.WAVE ? style.size() : 0.35f * style.size());
        }
        if (XenoServerConfig.kiAttackVisual().usesOwnedHdVisuals() && style.kind() == KiLook.Kind.WAVE && shot.endsAt == Long.MAX_VALUE) {
            draw(shot.level, style.asset(KiLook.Part.WAVE_HEAD), shot.origin.add(direction.scale(shot.reach)), direction,
                    style.size());
        }
        if (shot.arrived && now >= shot.nextHit) {
            double radius = 0.5 * target.getBbWidth();
            if (!V3KiPath.beamContact(shot.reach, distance, radius)) {
                burst(shot, shot.origin.add(direction.scale(Math.min(shot.reach, V3KiPath.BEAM_RANGE))));
                return true;
            }
            shot.hitsLeft--;
            shot.nextHit = now + HIT_EVERY_TICKS;
            boolean finished = shot.hitsLeft <= 0;
            hit(owner, target, shot, aim, finished);
            return finished;
        }
        return now >= shot.endsAt;
    }

    private static void land(ServerPlayer owner, LivingEntity target, Shot shot, Vec3 aim) {
        hit(owner, target, shot, aim, shot.last);
    }

    private static void hit(ServerPlayer owner, LivingEntity target, Shot shot, Vec3 at, boolean finalHit) {
        float dealt = V3CombatServer.techniqueHit(owner, target, shot.damage);
        // Damage callbacks can revoke this cast, change its session/lock, or tear V3 down entirely.
        // Recheck before publishing the impact or applying its final movement reaction.
        if (!SHOTS.contains(shot) || owner.serverLevel() != shot.level
                || shot.level.getEntity(shot.target) != target
                || !V3CombatServer.canContinueTechnique(owner, shot.session, shot.target)) return;
        burst(shot, at);
        // Only the hit that ends the attack throws the target.
        if (finalHit && dealt > 0f) V3CombatServer.techniqueShove(owner, target, V3Direction.FORWARD);
    }

    private static void burst(Shot shot, Vec3 at) {
        V3KiStyle style = shot.style;
        KiLook.Part part = KiLook.landing(style.kind());
        if (!XenoServerConfig.kiAttackVisual().usesOwnedHdVisuals()) {
            V3KiVisuals.send(shot.level, UUID.randomUUID(), shot.owner, Phase.IMPACT, style, at, null, 0);
        } else {
            draw(shot.level, style.asset(part), at, null,
                    part == KiLook.Part.EXPLOSION ? 0.8f * style.size() : 0.4f + 0.5f * style.size());
        }
        sound(shot.level, at, MainSounds.KI_EXPLOSION_IMPACT.get());
    }

    private static void draw(ServerLevel level, String asset, Vec3 pos, Vec3 forward, float scale) {
        if (!XenoEffects.playKi(level, asset, pos, forward, scale)) {
            // Effekseer is off or unavailable: something must still show.
            level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.01);
        }
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 0.7f, 1.0f);
    }

    private static SoundEvent launchSound(KiLook.Kind kind) {
        return switch (kind) {
            case WAVE, LASER, BEAM -> MainSounds.KI_KAME_FIRE.get();
            case DISK -> MainSounds.KI_DISK_FIRE.get();
            default -> MainSounds.KIBLAST_ATTACK.get();
        };
    }

    private static boolean loaded(ServerLevel level, Vec3 pos) {
        return level.isLoaded(BlockPos.containing(pos));
    }

    private static Vec3 centre(LivingEntity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
    }

    /** In front of the caster's chest, towards the target; {@code side} -1 or 1 for a left or right hand. */
    private static Vec3 muzzle(ServerPlayer player, Vec3 aim, int side) {
        Vec3 eye = player.getEyePosition();
        Vec3 gap = aim.subtract(eye);
        Vec3 forward = gap.lengthSqr() > 1.0e-6 ? gap.normalize() : player.getLookAngle();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() > 1.0e-6 ? right.normalize() : new Vec3(1, 0, 0);
        return eye.add(forward.scale(0.9)).add(right.scale(0.45 * side)).add(0.0, -0.3, 0.0);
    }
}
