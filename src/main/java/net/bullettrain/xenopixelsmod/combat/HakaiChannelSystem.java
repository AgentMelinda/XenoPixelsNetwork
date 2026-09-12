package net.bullettrain.xenopixelsmod.combat;

import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.compat.util.LazyOptional;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFx;
import net.bullettrain.xenopixelsmod.combat.fx.CombatFxKind;
import net.bullettrain.xenopixelsmod.combat.fx.HakaiFx;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.event.DmzMasterProtection;
import net.bullettrain.xenopixelsmod.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative Hakai channel: an interruptible, multi-tick hold that ends in the
 * target's erasure. No existing ability in this mod runs over several seconds — every other
 * FX call fires once and returns — so this mirrors {@code ChaseFlightSystem}'s shape instead
 * (a per-caster tracked state, re-validated every server tick, cancelled the instant any
 * condition fails) since that is the only other system here that runs a move across ticks.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class HakaiChannelSystem {

    private static final Map<UUID, Channel> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Restore> RESTORES = new ConcurrentHashMap<>();

    private static final class Restore {
        final int targetId;
        float keepBelow;
        final float step;

        Restore(int targetId, float keepBelow, float step) {
            this.targetId = targetId;
            this.keepBelow = keepBelow;
            this.step = step;
        }
    }

    private static final class Channel {
        final int targetId;
        final int totalTicks;
        /**
         * Where the caster is expected to stand. Re-baselined when they are knocked back, so
         * being hit costs poise (below) rather than instantly failing the movement check too.
         */
        Vec3 startPos;
        int ticksElapsed;
        /** Damage absorbed so far this channel, against the caster's poise budget. */
        float damageTaken;
        /** Command-started: do not cancel for LOS or stepping. */
        final boolean skipEnvironment;
        /** Force-use: skip resist and complete in {@link #FORCE_CHANNEL_TICKS}. */
        final boolean forceErase;
        final GlowLease glow;

        Channel(int targetId, int totalTicks, Vec3 startPos, boolean skipEnvironment, boolean forceErase,
                GlowLease glow) {
            this.targetId = targetId;
            this.totalTicks = totalTicks;
            this.startPos = startPos;
            this.skipEnvironment = skipEnvironment;
            this.forceErase = forceErase;
            this.glow = glow;
        }
    }

    /** {@code /xenohakai use force} completes in this many ticks so the orb is visible. */
    private static final int FORCE_CHANNEL_TICKS = 10;
    /** Target max-HP or melee this many times the caster's resists a full erase. */
    private static final float RESIST_STAT_RATIO = 3.0f;

    /** Poise budget in health points for this caster, from the configured fraction. */
    private static float poiseBudget(ServerPlayer caster) {
        return Math.max(0.0f, caster.getMaxHealth() * XenoServerConfig.hakaiPoiseFraction);
    }

    private HakaiChannelSystem() {
    }

    public static boolean isChanneling(ServerPlayer caster) {
        return caster != null && ACTIVE.containsKey(caster.getUUID());
    }

    /** Starts (or restarts) a channel. Caller has already applied every gate. */
    public static void start(ServerPlayer caster, LivingEntity target) {
        start(caster, target, false);
    }

    public static void start(ServerPlayer caster, LivingEntity target, boolean skipEnvironment) {
        start(caster, target, skipEnvironment, false);
    }

    public static void start(ServerPlayer caster, LivingEntity target, boolean skipEnvironment,
                             boolean forceQuick) {
        int totalTicks = forceQuick
                ? FORCE_CHANNEL_TICKS
                : Math.max(10, XenoServerConfig.hakaiChannelTicks);
        Channel previous = ACTIVE.remove(caster.getUUID());
        if (previous != null) {
            clearGlow(caster, previous);
            if (previous.targetId != target.getId()) {
                beginRestore(caster, previous.targetId,
                        1.0f - previous.ticksElapsed / (float) Math.max(1, previous.totalTicks));
            }
        }
        Restore restoring = RESTORES.get(caster.getUUID());
        if (restoring != null && restoring.targetId == target.getId()) RESTORES.remove(caster.getUUID());
        GlowLease glow = new GlowLease(target::hasGlowingTag, target::setGlowingTag,
                XenoServerConfig.hakaiTargetGlow);
        ACTIVE.put(caster.getUUID(), new Channel(target.getId(), totalTicks, caster.position(),
                skipEnvironment, forceQuick, glow));

        caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                ModSounds.HAKAI_CHARGE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        DmzAnimHelper.broadcastHakaiHold(caster);
    }

    public static void cancel(ServerPlayer caster, String message) {
        Channel removed = ACTIVE.remove(caster.getUUID());
        if (removed != null) {
            clearGlow(caster, removed);
            DmzAnimHelper.broadcastHakaiStop(caster);
            float progress = removed.ticksElapsed / (float) Math.max(1, removed.totalTicks);
            beginRestore(caster, removed.targetId, 1.0f - progress);
            if (message != null) {
                caster.displayClientMessage(Component.literal(message), true);
                caster.sendSystemMessage(Component.literal(message));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)) return;
        if (!(caster.level() instanceof ServerLevel level)) {
            cancel(caster, null);
            return;
        }
        tickRestore(caster, level);
        Channel channel = ACTIVE.get(caster.getUUID());
        if (channel == null) return;

        // Each failure gets its own message: "lost the target" for all three made it
        // impossible to tell walking out of range from breaking line of sight.
        Entity raw = level.getEntity(channel.targetId);
        if (!(raw instanceof LivingEntity target) || !target.isAlive()) {
            cancel(caster, "§7Hakai lost the target");
            return;
        }
        if (caster.distanceTo(target) > XenoServerConfig.hakaiMaxRange) {
            cancel(caster, "§7Hakai target moved out of range");
            return;
        }
        boolean skipEnv = channel.skipEnvironment
                || XenoServerConfig.lockOnThroughBlocks;
        if (!skipEnv && !hasLineOfSight(caster, target)) {
            cancel(caster, "§7Hakai lost line of sight");
            return;
        }
        double moveLimit = XenoServerConfig.hakaiMoveInterruptDistance;
        if (!skipEnv && caster.position().distanceToSqr(channel.startPos) > moveLimit * moveLimit) {
            cancel(caster, "§7Hakai interrupted — hold still");
            return;
        }

        if (!channel.forceErase) {
            float perTick = XenoServerConfig.hakaiKiCost / Math.max(1, channel.totalTicks);
            if (!spendKi(caster, perTick)) {
                cancel(caster, "§7Hakai stopped — no KI");
                return;
            }
        }

        channel.ticksElapsed++;
        float progress = channel.ticksElapsed / (float) channel.totalTicks;
        net.bullettrain.xenopixelsmod.compat.npc.NpcDissolve.apply(target, progress);
        if (channel.ticksElapsed % 2 == 0) {
            HakaiFx.tick(level, caster, target, progress);
            caster.displayClientMessage(progressBar(progress), true);
        }

        if (channel.ticksElapsed >= channel.totalTicks) {
            boolean forceErase = channel.forceErase;
            ACTIVE.remove(caster.getUUID());
            clearGlow(caster, channel);
            net.bullettrain.xenopixelsmod.compat.npc.NpcDissolve.clear(target);
            finish(level, caster, target, forceErase);
        }
    }

    private static void tickRestore(ServerPlayer caster, ServerLevel level) {
        Restore restore = RESTORES.get(caster.getUUID());
        if (restore == null) return;
        Entity raw = level.getEntity(restore.targetId);
        if (!(raw instanceof LivingEntity target) || !target.isAlive()) {
            RESTORES.remove(caster.getUUID());
            return;
        }
        restore.keepBelow = Math.min(1.0f, restore.keepBelow + restore.step);
        HakaiFx.restore(level, target, restore.keepBelow);
        net.bullettrain.xenopixelsmod.compat.npc.NpcDissolve.apply(target, 1.0f - restore.keepBelow);
        if (restore.keepBelow >= 1.0f) {
            HakaiFx.reveal(target);
            net.bullettrain.xenopixelsmod.compat.npc.NpcDissolve.clear(target);
            RESTORES.remove(caster.getUUID());
        }
    }

    /**
     * Puts the target's outline back the way the channel found it. Safe to call for a target that
     * has since died or unloaded — there is simply nothing to restore.
     */
    private static void clearGlow(ServerPlayer caster, Channel channel) {
        if (channel != null) channel.glow.release();
    }

    private static void beginRestore(ServerPlayer caster, int targetId, float keepBelow) {
        float start = Math.max(0.02f, Math.min(1.0f, keepBelow));
        Entity raw = caster.level().getEntity(targetId);
        if (start >= 0.98f) {
            if (raw instanceof LivingEntity living) {
                net.bullettrain.xenopixelsmod.compat.npc.NpcDissolve.clear(living);
            }
            return;
        }
        // The restore is driven by the caster's own tick. If they disconnect the ramp is lost,
        // so the effect is cleared here instead of being left to expire on its 10-tick duration.
        int restoreTicks = XenoServerConfig.hakaiFadeRestoreTicks;
        if (restoreTicks <= 0) {
            if (raw instanceof LivingEntity living) {
                HakaiFx.reveal(living);
                net.bullettrain.xenopixelsmod.compat.npc.NpcDissolve.clear(living);
            }
            return;
        }
        float step = (1.0f - start) / restoreTicks;
        RESTORES.put(caster.getUUID(), new Restore(targetId, start, Math.max(0.01f, step)));
    }

    private static boolean spendKi(ServerPlayer player, float cost) {
        if (cost <= 0f) return true;
        try {
            LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, player);
            StatsData data = opt.orElse(null);
            Resources res = data != null ? data.getResources() : null;
            if (res == null || res.getCurrentEnergy() < cost) return false;
            res.removeEnergy(cost);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ten-segment actionbar readout of how far along the channel is. */
    private static Component progressBar(float progress) {
        int filled = Math.max(0, Math.min(10, Math.round(progress * 10.0f)));
        return Component.literal("§dHakai §8[§d" + "|".repeat(filled)
                + "§8" + "|".repeat(10 - filled) + "§8]");
    }

    private static void finish(ServerLevel level, ServerPlayer caster, LivingEntity target,
                               boolean forceErase) {
        Vec3 pos = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        DmzAnimHelper.broadcastHakaiFire(caster);
        CombatFx.cue(level, pos, CombatFxKind.HAKAI_ERASE, 1.0f);

        HakaiFx.burst(level, target, true);
        HakaiFx.reveal(target);
        net.bullettrain.xenopixelsmod.compat.npc.NpcDissolve.clear(target);
        if (target instanceof Player
                || net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync.isCustomNpc(target)) {
            eraseLivingTarget(target);
        } else {
            target.discard();
        }
    }

    private static void eraseLivingTarget(LivingEntity target) {
        var source = target.level().damageSources().genericKill();
        target.hurt(source, Float.MAX_VALUE);
        if (target.isAlive()) {
            target.setHealth(0.0f);
            target.die(source);
        }
    }

    private static boolean resistsErase(ServerPlayer caster, LivingEntity target) {
        if (target instanceof Player player
                && (player.isCreative() || player.isSpectator() || player.getAbilities().invulnerable)) {
            return true;
        }
        if (target.getMaxHealth() > caster.getMaxHealth() * RESIST_STAT_RATIO) return true;
        float casterMelee = meleeOf(caster);
        float targetMelee = meleeOf(target);
        return casterMelee > 0.0f && targetMelee > casterMelee * RESIST_STAT_RATIO;
    }

    private static float meleeOf(LivingEntity entity) {
        try {
            LazyOptional<StatsData> opt = StatsProvider.get(StatsCapability.INSTANCE, entity);
            StatsData data = opt.orElse(null);
            return data != null ? (float) data.getMeleeDamage() : 0.0f;
        } catch (Throwable ignored) {
            return 0.0f;
        }
    }

    /**
     * Damage staggers a channeling caster, and enough of it breaks the channel.
     *
     * <p>This used to cancel on <em>any</em> hit, which is why Hakai could never be landed on
     * anything that fights back: a hostile mob or an aggressive NPC lands a chip hit well inside
     * the ~4 second channel, and the knockback from that same hit also tripped the
     * hold-still check. Against a player standing still neither ever fired, which is why it
     * looked like Hakai simply "did not work on NPCs and mobs".
     *
     * <p>Now the caster has a poise budget and the movement baseline is reset by knockback, so a
     * committed cast survives being harassed while a real burst still breaks it.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCasterDamaged(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer caster)) {
            return;
        }
        Channel channel = ACTIVE.get(caster.getUUID());
        if (channel == null) {
            return;
        }
        channel.damageTaken += Math.max(0.0f, event.getNewDamage());
        float budget = poiseBudget(caster);
        if (channel.damageTaken > budget) {
            cancel(caster, "§7Hakai broken — too much damage taken");
            return;
        }
        // Absorbed: re-baseline so the knockback this hit is about to apply does not also fail
        // the hold-still check on the next tick.
        channel.startPos = caster.position();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearGlow(player, ACTIVE.remove(player.getUUID()));
            Restore pending = RESTORES.remove(player.getUUID());
            if (pending != null) {
                dropRestore(player, pending);
            }
        } else if (event.getEntity() != null) {
            ACTIVE.remove(event.getEntity().getUUID());
        }
    }

    /**
     * Clears a pending restore's effect on the target. Nothing can advance the ramp once the
     * caster is gone, and the effect would otherwise fade the body out over its own duration.
     */
    private static void dropRestore(ServerPlayer caster, Restore restore) {
        Entity raw = caster.level().getEntity(restore.targetId);
        if (raw instanceof LivingEntity living) {
            HakaiFx.reveal(living);
            net.bullettrain.xenopixelsmod.compat.npc.NpcDissolve.clear(living);
        }
    }

    /**
     * A channel that is still running when the server stops would otherwise leave its target
     * outlined forever, because {@code Glowing} is written to the entity's saved NBT while
     * {@link #ACTIVE} only ever lived in memory.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (event.getServer() == null) return;
        for (Map.Entry<UUID, Channel> entry : ACTIVE.entrySet()) {
            ServerPlayer caster = event.getServer().getPlayerList().getPlayer(entry.getKey());
            clearGlow(caster, entry.getValue());
        }
        for (Map.Entry<UUID, Restore> entry : RESTORES.entrySet()) {
            ServerPlayer caster = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (caster != null) {
                dropRestore(caster, entry.getValue());
            }
        }
        ACTIVE.clear();
        RESTORES.clear();
    }

    /**
     * Lock-on is client-side; this is the server look-target used by J and {@code /xenohakai use}.
     * Ray, then cone, then nearest living in range. Masters are skipped.
     */
    public static LivingEntity findLookTarget(ServerPlayer player, double range) {
        if (player == null || range <= 0) return null;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> e instanceof LivingEntity living && living.isAlive() && living != player
                        && !DmzMasterProtection.isDmzMaster(living),
                range * range);
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            return living;
        }
        LivingEntity best = null;
        double bestDot = 0.85;
        for (Entity e : player.level().getEntities(player, box, ent -> ent instanceof LivingEntity)) {
            if (!(e instanceof LivingEntity living) || !living.isAlive()) continue;
            if (DmzMasterProtection.isDmzMaster(living)) continue;
            Vec3 to = living.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 1.0e-4) continue;
            double dot = look.dot(to.scale(1.0 / dist));
            if (dot > bestDot) {
                bestDot = dot;
                best = living;
            }
        }
        if (best != null) return best;
        double nearest = range * range;
        for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range),
                e -> e != player && e.isAlive() && !DmzMasterProtection.isDmzMaster(e))) {
            double d = player.distanceToSqr(living);
            if (d < nearest) {
                nearest = d;
                best = living;
            }
        }
        return best;
    }

    /** Same clip-based line-of-sight check {@code LockOnValidator} uses, minus its seat gate. */
    public static boolean hasLineOfSight(ServerPlayer caster, Entity target) {
        Vec3 from = caster.getEyePosition();
        Vec3 to = target.getEyePosition();
        HitResult hit = caster.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS;
    }
}
