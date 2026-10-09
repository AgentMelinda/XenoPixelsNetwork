package net.bullettrain.xenopixelsmod.combat.v3.technique;

import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.MeleeAnimationS2C;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.CombatKnockback;
import net.bullettrain.xenopixelsmod.combat.v3.V3CombatServer;
import net.bullettrain.xenopixelsmod.combat.v3.V3Direction;
import net.bullettrain.xenopixelsmod.combat.v3.V3Motion;
import net.bullettrain.xenopixelsmod.combat.v3.ki.V3KiShots;
import net.bullettrain.xenopixelsmod.combat.v3.ki.V3KiStyle;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.CombatV3CameraPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Runs V3 technique timelines on the server.
 *
 * <p>A cast is admitted once, paid for once, and then its beats fire in order at their ticks.
 * Damage happens only at STRIKE and RADIAL beats and through V3's own ki shots ({@link V3KiShots});
 * nothing is applied by the client. Losing the lock, dying, changing dimension or switching
 * controller ends the cast.
 */
public final class V3TechniqueRuntime {
    /** Server-owned: {@code /xenoset v3.strikeApproachRange}. */
    private static double approachRange() {
        return net.bullettrain.xenopixelsmod.combat.v3.V3Config.get().strikeApproachRange();
    }
    private static double kiRange() {
        return net.bullettrain.xenopixelsmod.combat.v3.V3Config.get().strikeKiRange();
    }
    private static final Map<UUID, Cast> CASTS = new HashMap<>();
    private static final Map<UUID, CastGate> GATES = new HashMap<>();
    private static final Map<UUID, Cast> VICTIMS = new HashMap<>();
    static final int KI_LIFETIME_TICKS = 120;

    private V3TechniqueRuntime() {}

    /**
     * Pure admission: melee/mixed need the approved lock when {@code v3.strikeRequireLock} is true
     * (default). Pure Ki needs it only when {@code v3.strikeKiRequireLock} is true (default false).
     */
    public static boolean requiresApprovedLock(boolean pureKi, boolean strikeRequireLock,
                                               boolean strikeKiRequireLock) {
        return pureKi ? strikeKiRequireLock : strikeRequireLock;
    }

    /** @deprecated use {@link #requiresApprovedLock(boolean, boolean, boolean)} */
    @Deprecated
    public static boolean requiresApprovedLock(boolean pureKi, boolean strikeKiRequireLock) {
        return requiresApprovedLock(pureKi,
                net.bullettrain.xenopixelsmod.combat.v3.V3Config.Values.DEFAULT_STRIKE_REQUIRE_LOCK,
                strikeKiRequireLock);
    }

    /** Technique JSON {@code range} wins; otherwise pure Ki uses strikeKiRange, melee uses approach. */
    public static double effectiveRange(Double techniqueRange, boolean pureKi, double strikeKiRange,
                                        double strikeApproachRange) {
        if (techniqueRange != null && Double.isFinite(techniqueRange) && techniqueRange > 0) {
            return techniqueRange;
        }
        return pureKi ? strikeKiRange : strikeApproachRange;
    }

    /**
     * A live cast may keep working on its own aim target even when the player never acquired a
     * DMZ/V3 lock (lockless pure Ki). An approved lock still authorizes its own identity.
     */
    public static boolean castTargetMatches(UUID castTarget, UUID approvedTarget, UUID candidate) {
        return candidate != null && (candidate.equals(castTarget) || candidate.equals(approvedTarget));
    }

    public static UUID activeCastTarget(UUID owner) {
        Cast cast = owner == null ? null : CASTS.get(owner);
        return cast == null ? null : cast.target;
    }

    /** Live cast victim: prefer the cast's entity handle, then the approved lock if it matches. */
    static LivingEntity resolveCastVictim(ServerPlayer player, Cast cast) {
        if (player == null || cast == null || cast.victim == null) return null;
        if (cast.victim.isAlive() && !cast.victim.isRemoved() && cast.victim.level() == player.level()
                && cast.victim.getUUID().equals(cast.target)) {
            return cast.victim;
        }
        if (player.serverLevel().getEntity(cast.target) instanceof LivingEntity live
                && live.isAlive() && !live.isRemoved() && live.level() == player.level()) {
            return live;
        }
        LivingEntity approved = V3CombatServer.lockedTarget(player);
        return approved != null && approved.getUUID().equals(cast.target) ? approved : null;
    }

    /** Ordered beats that each fire exactly once, however the server's ticks fall. Pure. */
    public static final class Timeline {
        private final List<V3Beat> beats;
        private long start;
        private int next;
        private boolean cancelled;

        public Timeline(List<V3Beat> beats, long start) {
            this.beats = List.copyOf(beats);
            this.start = start;
        }

        public List<V3Beat> due(long now) {
            return due(now, null);
        }

        /** Consume beats before the held gate; leave its subsequent wind-up/contact beats pending. */
        public List<V3Beat> due(long now, V3Beat.Kind stopBefore) {
            if (cancelled || next >= beats.size() || now < start) return List.of();
            List<V3Beat> out = new ArrayList<>(2);
            while (next < beats.size() && beats.get(next).tick() <= now - start) {
                if (beats.get(next).kind() == stopBefore) break;
                out.add(beats.get(next++));
            }
            return out;
        }

        V3Beat take(long now, V3Beat.Kind stopBefore) {
            if (cancelled || next >= beats.size() || now < start || beats.get(next).tick() > now - start
                    || beats.get(next).kind() == stopBefore) return null;
            return beats.get(next++);
        }

        /** Whether the next beat is a {@code kind} beat whose time has come. */
        public boolean due(V3Beat.Kind kind, long now) {
            return !cancelled && next < beats.size() && beats.get(next).kind() == kind
                    && beats.get(next).tick() <= now - start;
        }

        /** Pushes every beat not yet fired {@code ticks} later: the cast is being held. */
        public void delay(long ticks) {
            if (ticks > 0) start += ticks;
        }

        public int elapsed(long now) { return (int) Math.clamp(now - start, 0, V3Beat.MAX_TICK); }

        public void cancel() { cancelled = true; }
        public boolean finished() { return cancelled || next >= beats.size(); }
    }

    /** One fighter's "may this cast start" memory: one cast at a time, per-technique cooldowns. Pure. */
    public static final class CastGate {
        private final Map<String, Long> ready = new HashMap<>();

        public boolean admit(String id, long now, int cooldownTicks, boolean casting) {
            if (casting || id == null || now < ready.getOrDefault(id, Long.MIN_VALUE)) return false;
            ready.put(id, now + Math.max(0, cooldownTicks));
            return true;
        }

        void refund(String id) { ready.remove(id); }
    }

    private static final class Cast {
        final V3TechniqueDefinition technique;
        final Timeline timeline;
        final V3Beat.Kind holdKind;
        final UUID id = UUID.randomUUID();
        final UUID session;
        final UUID target;
        final int targetId;
        final Vec3 freezeAt;
        final LivingEntity victim;
        final List<V3CameraBeat> camera;
        final boolean controlled;
        boolean victimGravity;
        boolean victimOwned;
        boolean victimNoAi;
        boolean victimAiOwned;
        long approachUntil;
        boolean approaching;
        double approachSpeed;
        net.bullettrain.xenopixelsmod.combat.v2.V2TravelPose.Snapshot travelPose;
        long freezeUntil;
        /** Keep cinematic + freeze after END (owner: like Ultimate Finisher). */
        final long cameraHoldUntil;
        final long startedAt;
        /** The slot key is still down: the ki release waits and the charge grows. */
        boolean held = true;
        int chargeTicks;
        boolean cameraPaused;
        /** Commit flag: false while the cast is only provisionally registered during admission. */
        boolean started;

        boolean acceptedContact;
        /** Solar Flare fires Taiyoken blind once on its flash POSE. */
        boolean solarFlashed;

        Cast(V3TechniqueDefinition technique, LivingEntity victim, UUID session, long now, boolean acceptedContact) {
            this.technique = technique;
            this.session = session;
            this.target = victim.getUUID();
            this.targetId = victim.getId();
            this.freezeAt = victim.position();
            this.victim = victim;
            this.controlled = controlsVictim(technique.beats());
            var config = net.bullettrain.xenopixelsmod.combat.v3.V3Config.get();
            // /xenoset v3.strikeCinematicCamera false keeps the player's own view for every Strike.
            // Opening hold (ki/charge) and post-END hold are separate xenoset keys.
            int openingHold = Math.max(0, config.strikeCinematicCameraHoldTicks());
            int postHold = Math.max(0, config.strikeCameraHoldTicks());
            this.camera = config.strikeCinematicCamera()
                    ? cameraFor(technique, controlled, openingHold) : List.of();
            this.startedAt = now;
            this.cameraHoldUntil = now + technique.durationTicks() + postHold;
            this.freezeUntil = Math.max(now + freezeTicks(technique.beats()), this.cameraHoldUntil);
            this.timeline = new Timeline(technique.beats(), now);
            this.holdKind = holdKind(technique.beats());
            this.acceptedContact = acceptedContact;
        }
    }

    /**
     * How long a technique holds its victim in place: until the shove that throws them, or until
     * the technique ends when nothing throws them.
     */
    static int freezeTicks(List<V3Beat> beats) {
        for (V3Beat beat : beats) {
            if (beat.kind() == V3Beat.Kind.SHOVE) return beat.tick();
        }
        return beats.isEmpty() ? 0 : beats.getLast().tick();
    }

    static boolean nonKi(List<V3Beat> beats) {
        return beats.stream().noneMatch(beat -> beat.kind() == V3Beat.Kind.KI_CHARGE
                || beat.kind() == V3Beat.Kind.KI_RELEASE);
    }

    static boolean controlsVictim(List<V3Beat> beats) {
        return nonKi(beats) || beats.stream().anyMatch(beat -> beat.kind() == V3Beat.Kind.APPROACH
                || beat.kind() == V3Beat.Kind.STRIKE || beat.kind() == V3Beat.Kind.HOLD_TARGET);
    }

    static List<V3CameraBeat> cameraFor(V3TechniqueDefinition technique, boolean melee) {
        return cameraFor(technique, melee, 0);
    }

    /**
     * Resolves the Strike cinematic timeline. {@code openingHoldTicks} lengthens the first close
     * angle on non-rush (no APPROACH) techniques by stealing time from later shots.
     * Rush/APPROACH cameras stay authored — stretching them fought lock-on and flickered.
     */
    static List<V3CameraBeat> cameraFor(V3TechniqueDefinition technique, boolean melee, int openingHoldTicks) {
        List<V3CameraBeat> base;
        if (!technique.camera().isEmpty() || !melee || technique.durationTicks() < 1) {
            base = technique.camera();
        } else {
            int shove = technique.beats().stream().filter(beat -> beat.kind() == V3Beat.Kind.SHOVE)
                    .mapToInt(V3Beat::tick).findFirst().orElse(0);
            if (shove > 0 && shove < technique.durationTicks()) {
                base = List.of(new V3CameraBeat(0, shove, new Vec3(4, 2, -7), Vec3.ZERO, 0f, V3CameraBeat.Easing.CUT),
                        new V3CameraBeat(shove, technique.durationTicks() - shove, new Vec3(24, 7, -7),
                                Vec3.ZERO, 0.5f, V3CameraBeat.Easing.CUT));
            } else {
                base = List.of(new V3CameraBeat(0, technique.durationTicks(), new Vec3(24, 7, -7),
                        Vec3.ZERO, 0.5f, V3CameraBeat.Easing.CUT));
            }
        }
        boolean rush = technique.beats().stream().anyMatch(beat -> beat.kind() == V3Beat.Kind.APPROACH);
        if (rush || openingHoldTicks <= 0) return base;
        return extendOpeningHold(base, technique.durationTicks(), openingHoldTicks);
    }

    /**
     * Keeps the opening close angle on screen longer. Later beats shift later and the last beat
     * shortens so the timeline still ends at {@code durationTicks}.
     */
    static List<V3CameraBeat> extendOpeningHold(List<V3CameraBeat> camera, int durationTicks, int holdTicks) {
        if (camera == null || camera.isEmpty() || holdTicks <= 0 || durationTicks < 1) {
            return camera == null ? List.of() : camera;
        }
        V3CameraBeat first = camera.getFirst();
        if (first.tick() != 0) return camera;
        int maxOpening = camera.size() == 1 ? durationTicks : Math.max(1, durationTicks - (camera.size() - 1));
        int want = Math.min(Math.max(first.duration(), holdTicks), maxOpening);
        if (want <= first.duration()) return camera;
        int delta = want - first.duration();
        java.util.ArrayList<V3CameraBeat> out = new java.util.ArrayList<>(camera.size());
        out.add(new V3CameraBeat(0, want, first.position(), first.look(), first.focus(), first.easing()));
        for (int i = 1; i < camera.size(); i++) {
            V3CameraBeat beat = camera.get(i);
            int tick = beat.tick() + delta;
            int duration = beat.duration();
            if (i == camera.size() - 1) {
                duration = Math.max(1, durationTicks - tick);
            }
            out.add(new V3CameraBeat(tick, duration, beat.position(), beat.look(), beat.focus(), beat.easing()));
        }
        return List.copyOf(out);
    }

    /** Authored wind-up gates precede release; older timelines keep their original release gate. */
    static V3Beat.Kind holdKind(List<V3Beat> beats) {
        return beats.stream().anyMatch(beat -> beat.kind() == V3Beat.Kind.KI_HOLD)
                ? V3Beat.Kind.KI_HOLD : V3Beat.Kind.KI_RELEASE;
    }

    /** Whether a held victim has moved far enough from its spot to be put back. */
    public static boolean drifted(double distanceSquared) {
        return !(distanceSquared <= 0.3 * 0.3);
    }

    /** No ki shot outlives this, whatever happens to its target: nothing stays in the world. */
    public static boolean kiExpired(long releasedAt, long now) {
        return now < releasedAt || now - releasedAt >= KI_LIFETIME_TICKS;
    }

    /** Techniques that move or directly control a victim must start inside the approach envelope. */
    static boolean requiresCloseControl(List<V3Beat> beats) {
        return beats != null && beats.stream().anyMatch(beat -> beat.kind() == V3Beat.Kind.APPROACH
                || beat.kind() == V3Beat.Kind.HOLD_TARGET);
    }

    /** Pure admission rule used before a cooldown or ki cost is consumed (default approach range). */
    static boolean inCastRange(boolean closeControl, double distance) {
        return inCastRange(closeControl, distance, net.bullettrain.xenopixelsmod.combat.v3.V3Config.Values.DEFAULT_STRIKE_APPROACH);
    }

    static boolean inCastRange(boolean closeControl, double distance, double approachRange) {
        return Double.isFinite(distance) && distance >= 0.0 && (!closeControl || distance <= approachRange);
    }

    /** A control-only technique has no damage contact beat of its own. */
    static boolean controlOnly(List<V3Beat> beats) {
        if (beats == null) return false;
        boolean control = false;
        for (V3Beat beat : beats) {
            if (beat.kind() == V3Beat.Kind.HOLD_TARGET) control = true;
            if (beat.kind() == V3Beat.Kind.STRIKE || beat.kind() == V3Beat.Kind.RADIAL
                    || beat.kind() == V3Beat.Kind.KI_RELEASE) return false;
        }
        return control;
    }

    public static boolean owns(ServerPlayer player) {
        return player != null && CASTS.containsKey(player.getUUID());
    }

    /** Scripted throws release physics but retain the victim's combat exclusion until END. */
    public static boolean isControlledVictim(UUID victim) {
        return victim != null && VICTIMS.containsKey(victim);
    }

    /**
     * Starts {@code technique}. Melee/mixed need the approved lock when
     * {@code /xenoset v3.strikeRequireLock true} (default). Pure Ki may look-aim without a lock when
     * {@code /xenoset v3.strikeKiRequireLock false}.
     *
     * @return false when nothing started; nothing was spent in that case
     */
    public static boolean cast(ServerPlayer player, V3TechniqueDefinition technique, UUID target) {
        if (player == null || technique == null) return false;
        if (!V3CombatServer.owns(player)) {
            hint(player, technique.name() + " needs the V3 combat controller");
            return false;
        }
        boolean pureKi = net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi.pureKi(technique);
        var config = net.bullettrain.xenopixelsmod.combat.v3.V3Config.get();
        boolean needLock = requiresApprovedLock(pureKi, config.strikeRequireLock(), config.strikeKiRequireLock());
        LivingEntity locked = V3CombatServer.lockedTarget(player);
        if (locked != null && target != null && !locked.getUUID().equals(target)) {
            hint(player, "Lock a target first");
            return false;
        }
        if (locked == null) {
            if (needLock) {
                hint(player, "Lock a target first");
                return false;
            }
            locked = lookAimTarget(player, effectiveRange(technique.range(), pureKi, config.strikeKiRange(),
                    config.strikeApproachRange()));
            if (locked == null) {
                hint(player, technique.name() + " needs a target in look range");
                return false;
            }
        }
        if (!V3CombatServer.canStartTechnique(player)) return false;
        UUID session = V3CombatServer.session(player);
        if (session == null) return false;
        boolean closeControl = requiresCloseControl(technique.beats());
        double range = effectiveRange(technique.range(), pureKi, config.strikeKiRange(), config.strikeApproachRange());
        if (!inCastRange(closeControl || controlsVictim(technique.beats()) || pureKi,
                player.distanceTo(locked), range)) {
            hint(player, technique.name() + " is out of range");
            return false;
        }
        boolean controlOnly = controlOnly(technique.beats());
        boolean controlled = controlsVictim(technique.beats());
        if ((closeControl || controlOnly || controlled) && !CombatKnockback.canKnockBack(locked)) return false;
        if (controlled && (VICTIMS.containsKey(locked.getUUID())
                || locked instanceof ServerPlayer victim && !V3CombatServer.canStartTechnique(victim))) return false;
        if ((controlOnly || controlled) && net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new net.bullettrain.xenopixelsmod.api.event.TechniqueControlEvent(player, locked, technique.id())).isCanceled()) {
            return false;
        }
        // Admission listeners may change the world or rotate the fighter's identity.
        LivingEntity approved = V3CombatServer.lockedTarget(player);
        if (!V3CombatServer.canStartTechnique(player) || !session.equals(V3CombatServer.session(player))
                || (approved != null && approved != locked)
                || (!needLock && approved == null && (locked.level() != player.level() || !locked.isAlive()))
                || (needLock && approved != locked)
                || controlled && (VICTIMS.containsKey(locked.getUUID())
                || !CombatKnockback.canKnockBack(locked)
                || locked instanceof ServerPlayer victim && !V3CombatServer.canStartTechnique(victim))) return false;
        long now = player.getServer().getTickCount();
        CastGate gate = GATES.computeIfAbsent(player.getUUID(), id -> new CastGate());
        if (!gate.admit(technique.id(), now, technique.cooldownTicks(), owns(player))) {
            hint(player, technique.name() + " is not ready");
            return false;
        }
        Cast cast = new Cast(technique, locked, session, now, controlOnly);
        boolean committed = false;
        // Provisional scoped registration: from here until commit, this fighter "owns" a cast, so a
        // reentrant cast() reached from any callback below is refused by the gate and cannot be
        // overwritten by the CASTS.put that used to run last. Rollback removes exactly this cast.
        CASTS.put(player.getUUID(), cast);
        try {
            // The cast token distinguishes reentrant casts inside the same fighter session.
            net.bullettrain.xenopixelsmod.combat.v2.V2ChargedArc.cancel(player);
            if (!V3Motion.acquire(player, V3Motion.Owner.CINEMATIC, cast.id)) return false;
            if (controlled) net.bullettrain.xenopixelsmod.combat.v2.V2ChargedArc.cancel(locked);
            if (controlled && locked instanceof ServerPlayer victim
                    && !V3Motion.acquire(victim, V3Motion.Owner.CINEMATIC, cast.id)) return false;
            if (controlled) {
                cast.victimGravity = locked.isNoGravity();
                cast.victimOwned = true;
                VICTIMS.put(locked.getUUID(), cast);
                if (locked instanceof Mob mob) {
                    cast.victimNoAi = mob.isNoAi();
                    cast.victimAiOwned = true;
                    mob.setNoAi(true);
                    net.bullettrain.xenopixelsmod.compat.npc.NpcCombatBrain.disengage(locked);
                }
                locked.setNoGravity(true);
                // addEffect/teleport inside techniqueFreeze run external listeners (effect events,
                // NPC brains, protection addons) that may cancel, rotate identity or move entities.
                V3CombatServer.techniqueFreeze(player, locked, cast.freezeAt);
                if (locked instanceof ServerPlayer victim) {
                    if (CASTS.get(player.getUUID()) != cast || VICTIMS.get(locked.getUUID()) != cast
                            || !cast.victimOwned || !V3Motion.holdCinematic(victim, cast.id)) return false;
                    V3CombatServer.setCinematic(victim, true);
                }
            }
            // Exact post-callback validation before any cost is spent: identity, session, lock,
            // protection and both motion tokens must still be the ones this cast established.
            if (!stillAdmissible(player, cast, locked, controlled, needLock)) return false;
            if (!V3CombatServer.spendKi(player, (float) technique.kiCost())) {
                hint(player, "Not enough ki");
                return false;
            }
            // Resource writes can also fire listeners; re-check before the fighter is committed.
            if (!stillAdmissible(player, cast, locked, controlled, needLock)) return false;
            net.bullettrain.xenopixelsmod.combat.v3.V3DashCamera.stop(player);
            cast.started = true;
            if (net.bullettrain.xenopixelsmod.combat.v3.V3KaiokenBurst.isKaiokenTechnique(
                    technique.id(), technique.name())) {
                net.bullettrain.xenopixelsmod.combat.v3.V3KaiokenBurst.begin(player,
                        technique.durationTicks()
                                + net.bullettrain.xenopixelsmod.combat.v3.V3Config.get().strikeCameraHoldTicks());
            }
            V3CombatServer.setCinematic(player, true);
            camera(player, cast, now, false);
            committed = true;
            return true;
        } finally {
            if (!committed) {
                gate.refund(technique.id());
                end(player, cast, true, true);
            }
        }
    }

    /** Nearest living entity along the eye ray within {@code range}, or null. */
    static LivingEntity lookAimTarget(ServerPlayer player, double range) {
        if (player == null || !Double.isFinite(range) || range <= 0) return null;
        Vec3 from = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        if (!net.bullettrain.xenopixelsmod.combat.v3.V3TargetingRules.finite(from)
                || !net.bullettrain.xenopixelsmod.combat.v3.V3TargetingRules.finite(look)) return null;
        Vec3 to = from.add(look.scale(range));
        var search = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        List<LivingEntity> candidates = new ArrayList<>(64);
        player.serverLevel().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(LivingEntity.class),
                search, entity -> entity != player && entity.isAlive() && entity.isPickable(), candidates, 64);
        LivingEntity nearest = null;
        double nearestHit = range * range;
        for (LivingEntity entity : candidates) {
            var box = entity.getBoundingBox().inflate(entity.getPickRadius());
            double distance = net.bullettrain.xenopixelsmod.combat.v3.V3TargetingRules.rayDistance(box, from, to);
            if (distance < nearestHit || (distance == 0 && nearest == null)) {
                nearest = entity;
                nearestHit = distance;
            }
        }
        return nearest;
    }

    /**
     * Whether the cast registered provisionally in {@link #cast} is still exactly the one every
     * subsystem knows about. Any external callback that cancelled, re-targeted or re-leased in the
     * meantime makes the answer false, and the caller rolls back without spending.
     */
    private static boolean stillAdmissible(ServerPlayer player, Cast cast, LivingEntity locked, boolean controlled) {
        var config = net.bullettrain.xenopixelsmod.combat.v3.V3Config.get();
        boolean needLock = requiresApprovedLock(
                net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi.pureKi(cast.technique),
                config.strikeRequireLock(), config.strikeKiRequireLock());
        return stillAdmissible(player, cast, locked, controlled, needLock);
    }

    private static boolean stillAdmissible(ServerPlayer player, Cast cast, LivingEntity locked, boolean controlled,
                                           boolean needLock) {
        if (CASTS.get(player.getUUID()) != cast) return false;
        if (!V3CombatServer.canStartTechnique(player)) return false;
        if (!cast.session.equals(V3CombatServer.session(player))) return false;
        if (!locked.isAlive() || locked.isRemoved() || locked.level() != player.level()) return false;
        LivingEntity approved = V3CombatServer.lockedTarget(player);
        if (needLock) {
            if (approved != locked) return false;
        } else if (approved != null && approved != locked) {
            return false;
        }
        if (!V3Motion.holdCinematic(player, cast.id)) return false;
        if (controlled) {
            if (VICTIMS.get(locked.getUUID()) != cast || !cast.victimOwned) return false;
            if (!CombatKnockback.canKnockBack(locked)) return false;
            if (locked instanceof ServerPlayer victim
                    && (!V3Motion.holdCinematic(victim, cast.id) || !V3CombatServer.inCinematic(victim))) return false;
        }
        return true;
    }

    public static void tick(ServerPlayer player, int now) {
        Cast cast = CASTS.get(player.getUUID());
        if (cast == null) return;
        try {
            tickCast(player, cast, now);
        } catch (RuntimeException | Error failure) {
            end(player, cast, true, true);
            throw failure;
        }
    }

    private static void tickCast(ServerPlayer player, Cast cast, int now) {
        if (!V3CombatServer.inCinematic(player)
                || !V3CombatServer.canContinueTechnique(player, cast.session, cast.target)
                || !V3Motion.holdCinematic(player, cast.id)) {
            // Something else already reset the fighter (lost lock, session change, death).
            end(player, cast, true, true);
            return;
        }
        LivingEntity target = resolveCastVictim(player, cast);
        if (target == null) {
            end(player, cast, true, true);
            return;
        }
        if (cast.victimOwned && target instanceof ServerPlayer victim
                && (!V3CombatServer.inCinematic(victim) || !V3Motion.holdCinematic(victim, cast.id))) {
            end(player, cast, true, true); return;
        }
        // The victim of a technique stays exactly where the technique caught them.
        if ((cast.victimOwned || !cast.controlled && cast.acceptedContact) && now < cast.freezeUntil) {
            if (!CombatKnockback.canKnockBack(target)) { end(player, cast, true, true); return; }
            V3CombatServer.techniqueFreeze(player, target, cast.freezeAt);
        }
        if (CASTS.get(player.getUUID()) != cast) return;
        if (cast.approaching) {
            if (!approach(player, target, cast, now)) return;
        }
        if (cast.held) {
            V3Beat beat;
            while ((beat = cast.timeline.take(now, cast.holdKind)) != null) {
                run(player, target, cast, beat, now);
                if (CASTS.get(player.getUUID()) != cast) return;
                if (cast.approaching) return;
            }
        }
        if (cast.held && cast.timeline.due(cast.holdKind, now)) {
            if (cast.chargeTicks < MAX_HOLD_TICKS) {
                if (!cast.cameraPaused) {
                    camera(player, cast, now, true);
                    cast.cameraPaused = true;
                }
                // Holding the slot key: keep gathering instead of firing.
                cast.chargeTicks++;
                cast.timeline.delay(1);
                cast.freezeUntil++;
                if (cast.chargeTicks % 16 == 0) V3KiShots.charge(player, target, style(cast.technique), cast.id);
                if (cast.chargeTicks % 5 == 0) {
                    hint(player, cast.technique.name() + " " + Math.round(chargePower(cast.chargeTicks) * 100f) + "%");
                }
                return;
            }
            cast.held = false;
        }
        if (cast.cameraPaused) {
            camera(player, cast, now, false);
            cast.cameraPaused = false;
        }
        V3Beat beat;
        while ((beat = cast.timeline.take(now, null)) != null) {
            run(player, target, cast, beat, now);
            if (CASTS.get(player.getUUID()) != cast) return;
            if (cast.approaching) return;
        }
        if (cast.timeline.finished()) {
            // Owner: hold camera + freeze after END like Ultimate Finisher
            // (/xenoset v3.strikeCinematicCameraHoldTicks).
            if (now < cast.cameraHoldUntil) {
                if ((cast.victimOwned || cast.acceptedContact) && CombatKnockback.canKnockBack(target)) {
                    V3CombatServer.techniqueFreeze(player, target, cast.freezeAt);
                }
                if (!cast.camera.isEmpty() && !cast.cameraPaused) {
                    camera(player, cast, cast.startedAt + cast.technique.durationTicks(), true);
                    cast.cameraPaused = true;
                }
                return;
            }
            end(player, cast, true, false);
        }
    }

    /** How long a ki technique's release can be held back, and when its charge is full. */
    static final int MAX_HOLD_TICKS = 100;
    static final int FULL_CHARGE_TICKS = 40;

    /** 1.0 for a tap, rising to 2.0 once the slot key has been held for the full charge. */
    public static float chargePower(int heldTicks) {
        return 1f + Math.clamp(heldTicks, 0, FULL_CHARGE_TICKS) / (float) FULL_CHARGE_TICKS;
    }

    /** The slot key was let go: a held ki technique fires at its next beat. */
    public static void release(ServerPlayer player) {
        Cast cast = player == null ? null : CASTS.get(player.getUUID());
        if (cast != null) cast.held = false;
    }

    public static void cancel(ServerPlayer player) {
        Cast cast = player == null ? null : CASTS.get(player.getUUID());
        if (cast != null) end(player, cast, true, true);
    }

    /** Disconnect: forget the fighter entirely. */
    public static void forget(ServerPlayer player) {
        cancel(player);
        GATES.remove(player.getUUID());
    }

    /** Process teardown: no cast or cooldown memory survives a server restart in the same JVM. */
    public static void clearAll() {
        for (Map.Entry<UUID, Cast> entry : List.copyOf(CASTS.entrySet())) {
            Cast cast = entry.getValue();
            cast.timeline.cancel();
            V3KiShots.cancelCast(entry.getKey(), cast.id);
            finishVictim(cast);
        }
        CASTS.clear();
        GATES.clear();
        VICTIMS.clear();
    }

    private static void end(ServerPlayer player, Cast cast, boolean resetState, boolean cancelShots) {
        cast.timeline.cancel();
        boolean removed = CASTS.remove(player.getUUID(), cast);
        if (removed && cast.started
                && net.bullettrain.xenopixelsmod.combat.v3.V3KaiokenBurst.isKaiokenTechnique(
                        cast.technique.id(), cast.technique.name())) {
            net.bullettrain.xenopixelsmod.combat.v3.V3KaiokenBurst.end(player);
        }
        try {
            try {
                finishVictim(cast);
            } finally {
                var pose = cast.travelPose;
                cast.travelPose = null;
                try {
                    net.bullettrain.xenopixelsmod.combat.v2.V2TravelPose.end(player, pose);
                } finally {
                    V3Motion.releaseCinematic(player, cast.id);
                }
            }
        } finally {
            try {
                try {
                    // Replace a held pose while the cinematic perspective still renders DMZ melee.
                    // DMZ discards melee requests in vanilla first person after camera restoration.
                    if (removed && cast.started && cast.technique.beats().stream().anyMatch(beat -> beat.kind() == V3Beat.Kind.POSE
                                && beat.payload().startsWith(net.bullettrain.xenopixelsmod.combat.v3.anim.V3AnimationCatalog.PREFIX))) {
                        NetworkHandler.sendToTrackingEntityAndSelf(new MeleeAnimationS2C(player.getId(),
                                net.bullettrain.xenopixelsmod.combat.v3.anim.V3AnimationCatalog.CONTROL_RELEASE,
                                false, 1.0f), player);
                    }
                } finally {
                    if (cancelShots) V3KiShots.cancelCast(player.getUUID(), cast.id);
                    if (cast.started && !cast.camera.isEmpty()) {
                        ModNetwork.sendToPlayer(player, CombatV3CameraPacket.stop(cast.session, cast.id, cast.target, cast.targetId));
                    }
                }
            } finally {
                if (resetState && removed && cast.started && cast.session.equals(V3CombatServer.session(player))
                        && !V3Motion.owns(player) && !CASTS.containsKey(player.getUUID())) {
                    V3CombatServer.setCinematic(player, false);
                }
            }
        }
    }

    private static void camera(ServerPlayer player, Cast cast, long now, boolean paused) {
        if (cast.camera.isEmpty()) return;
        ModNetwork.sendToPlayer(player, new CombatV3CameraPacket(cast.session, cast.id, cast.target, cast.targetId,
                cast.technique.durationTicks(), Math.min(cast.technique.durationTicks(), cast.timeline.elapsed(now)),
                paused, cast.camera));
    }

    private static void run(ServerPlayer player, LivingEntity target, Cast cast, V3Beat beat, int now) {
        switch (beat.kind()) {
            case POSE -> {
                try {
                    NetworkHandler.sendToTrackingEntityAndSelf(
                            new MeleeAnimationS2C(player.getId(), beat.payload(), false, 1.0f), player);
                } catch (RuntimeException | LinkageError unavailable) {
                    // Presentation only.
                }
                // Solar Flare: DMZ Taiyoken blind on the flash pose (not cast start — that no-op'd
                // when taiyoken was locked / hands weren't empty).
                if (!cast.solarFlashed && net.bullettrain.xenopixelsmod.combat.v3.V3SolarFlare
                        .isSolarFlare(cast.technique.id(), cast.technique.name())) {
                    cast.solarFlashed = true;
                    net.bullettrain.xenopixelsmod.combat.v3.V3SolarFlare.flash(player);
                }
            }
            case APPROACH -> {
                if (player.distanceTo(target) > 2.4) {
                    cast.approaching = true;
                    cast.travelPose = net.bullettrain.xenopixelsmod.combat.v2.V2TravelPose.begin(player, true, true);
                    cast.approachSpeed = Math.clamp((player.distanceTo(target) - 2) / Math.max(1, beat.duration()), 0.15, 3);
                    cast.approachUntil = now + Math.max(beat.duration(), (int) Math.ceil(player.distanceTo(target) / cast.approachSpeed) + 20);
                    camera(player, cast, now, true);
                    cast.cameraPaused = true;
                }
            }
            case STRIKE -> {
                float accepted = V3CombatServer.techniqueStrike(player, target, beat.value());
                if (CASTS.get(player.getUUID()) == cast && V3CombatServer.canContinueTechnique(player, cast.session, cast.target)
                        && resolveCastVictim(player, cast) == target && accepted > 0f) {
                    cast.acceptedContact = true;
                    if (beat.value() >= 1f) net.bullettrain.xenopixelsmod.combat.v3.V3AttackSounds.heavyHit(player, target);
                    else net.bullettrain.xenopixelsmod.combat.v3.V3AttackSounds.contact(player, target);
                }
            }
            case SHOVE -> {
                cast.freezeUntil = now;
                releaseVictim(cast);
                if (cast.acceptedContact) {
                    Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() < 1e-6) away = player.getLookAngle().multiply(1, 0, 1);
                    Vec3 launch = shoveDirection(beat.payload(), away);
                    // Sideways launch distance is server tuning (/xenoset v3.strikeLaunchDistance).
                    net.bullettrain.xenopixelsmod.combat.v2.V2ChargedArc.start(target, launch, now,
                            net.bullettrain.xenopixelsmod.combat.v3.V3Config.get().strikeLaunchDistance(),
                            Math.abs(launch.y) > 0.99 ? 0 : 3);
                }
            }
            case RADIAL -> {
                double radius = radius(beat.payload());
                if (player.distanceToSqr(target) <= radius * radius) {
                    float accepted = V3CombatServer.techniqueHit(player, target, beat.value());
                    if (CASTS.get(player.getUUID()) == cast && V3CombatServer.canContinueTechnique(player, cast.session, cast.target)
                            && resolveCastVictim(player, cast) == target && accepted > 0f) {
                        cast.acceptedContact = true;
                        V3CombatServer.techniqueShove(player, target, V3Direction.FORWARD);
                    }
                }
                // Radial timelines have no KI_RELEASE (catalog: kiTechnique iff KI_RELEASE).
                // Still fire DMZ final_explosion for hitboxes + colours in DMZ visual mode.
                if (net.bullettrain.xenopixelsmod.config.XenoServerConfig.kiAttackVisual()
                        == net.bullettrain.xenopixelsmod.combat.v3.ki.KiAttackVisualMode.DMZ) {
                    String radialKi = cast.technique.kiTechnique();
                    if (radialKi == null || radialKi.isBlank()) radialKi = "final_explosion";
                    net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi.fire(player, radialKi);
                }
            }
            case HOLD_TARGET -> {
                if (cast.acceptedContact) V3CombatServer.techniqueHold(player, target, Math.max(1, beat.duration()));
            }
            case KI_CHARGE -> {
                if (net.bullettrain.xenopixelsmod.config.XenoServerConfig.kiAttackVisual().usesOwnedHdVisuals()) {
                    V3KiShots.charge(player, target, style(cast.technique), cast.id);
                }
                // DMZ mode charges via the dispatcher on release; charge pose still plays.
            }
            case KI_RELEASE -> {
                if (net.bullettrain.xenopixelsmod.config.XenoServerConfig.kiAttackVisual()
                        == net.bullettrain.xenopixelsmod.combat.v3.ki.KiAttackVisualMode.DMZ) {
                    String nativeId = cast.technique.kiTechnique();
                    if (nativeId == null || nativeId.isBlank()) {
                        nativeId = net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi.nativeId(cast.technique.id());
                    }
                    if (!net.bullettrain.xenopixelsmod.combat.v3.ki.V3NativeKi.fire(player, nativeId, cast.technique)) {
                        // Fall back to owned shots if the DMZ template is missing.
                        V3KiShots.fire(player, target, cast.id, cast.session,
                                style(cast.technique).charged(chargePower(cast.chargeTicks)), now);
                    }
                } else {
                    V3KiShots.fire(player, target, cast.id, cast.session,
                            style(cast.technique).charged(chargePower(cast.chargeTicks)), now);
                }
            }
            case KI_HOLD -> { }
            case END -> { }
        }
    }

    private static V3KiStyle style(V3TechniqueDefinition technique) {
        return V3KiStyle.of(technique);
    }

    private static boolean approach(ServerPlayer player, LivingEntity target, Cast cast, int now) {
        Vec3 to = target.position().subtract(player.position());
        if (to.lengthSqr() <= 2.4 * 2.4) {
            cast.approaching = false;
            net.bullettrain.xenopixelsmod.combat.v2.V2TravelPose.end(player, cast.travelPose);
            cast.travelPose = null;
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.hasImpulse = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            return true;
        }
        if (net.bullettrain.xenopixelsmod.combat.v2.V2TravelPose.flightLost(player, cast.travelPose)) {
            end(player, cast, true, true); return false;
        }
        if (now > cast.approachUntil || to.length() > approachRange()) { end(player, cast, true, true); return false; }
        Vec3 movement = to.normalize().scale(Math.min(cast.approachSpeed, Math.max(0.05, to.length() - 2)));
        Vec3 next = player.position().add(movement);
        if (!V3CombatServer.loaded(player, next)
                || !player.level().getWorldBorder().isWithinBounds(player.getBoundingBox().move(movement))
                || !player.level().noCollision(player, player.getBoundingBox().expandTowards(movement))) {
            end(player, cast, true, true); return false;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
        player.setYRot(yaw);
        player.setXRot((float) -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))));
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;
        player.setDeltaMovement(movement);
        player.hurtMarked = true;
        player.hasImpulse = true;
        player.fallDistance = 0f;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        cast.timeline.delay(1);
        cast.freezeUntil++;
        return false;
    }

    private static void releaseVictim(Cast cast) {
        if (!cast.victimOwned || VICTIMS.get(cast.target) != cast) return;
        cast.victimOwned = false;
        if (cast.victim instanceof ServerPlayer victim) {
            if (V3Motion.holdCinematic(victim, cast.id)) {
                try {
                    V3Motion.releaseCinematic(victim, cast.id);
                } finally {
                    V3CombatServer.setCinematic(victim, false);
                }
            }
        } else cast.victim.setNoGravity(cast.victimGravity);
    }

    private static void finishVictim(Cast cast) {
        try {
            releaseVictim(cast);
        } finally {
            if (VICTIMS.remove(cast.target, cast) && cast.victimAiOwned && cast.victim instanceof Mob mob) {
                cast.victimAiOwned = false;
                mob.setNoAi(cast.victimNoAi);
            }
        }
    }

    static Vec3 shoveDirection(String payload, Vec3 forward) {
        Vec3 flat = forward.multiply(1, 0, 1);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        if ("UP".equals(payload)) return new Vec3(0, 1, 0);
        if ("DOWN".equals(payload)) return new Vec3(0, -1, 0);
        return switch (direction(payload)) {
            case LEFT -> new Vec3(flat.z, 0, -flat.x);
            case RIGHT -> new Vec3(-flat.z, 0, flat.x);
            case BACK -> flat.scale(-1);
            case UP -> new Vec3(0, 1, 0);
            case NONE, FORWARD -> flat;
        };
    }

    private static V3Direction direction(String payload) {
        try {
            return V3Direction.valueOf(payload);
        } catch (IllegalArgumentException unknown) {
            return V3Direction.FORWARD;
        }
    }

    private static double radius(String payload) {
        try {
            double radius = Double.parseDouble(payload);
            return Double.isFinite(radius) ? Math.clamp(radius, 1.0, 16.0) : 6.0;
        } catch (NumberFormatException unknown) {
            return 6.0;
        }
    }

    private static void hint(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }
}
