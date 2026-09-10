package net.bullettrain.xenopixelsmod.combat.clone;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.bullettrain.xenopixelsmod.missile.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Owns a fighter's other bodies: who is split, into how many, and what that costs them.
 *
 * <p>Shi Shin No Ken divides a fighter rather than duplicating them. Splitting into four means
 * four bodies each hitting for a quarter, so the technique buys reach, angles and a target the
 * opponent has to guess at — never raw damage. {@link #damageShare} is the single place that
 * division is expressed, so the melee path cannot drift away from the clone count.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class XenoCloneSystem {

    /** Live copies per fighter, in formation-slot order. */
    private static final Map<UUID, CloneSplitState<XenoCloneEntity>> SPLIT = new HashMap<>();
    private static final Map<UUID, Set<XenoCloneEntity>> OWNED = new HashMap<>();
    /** Zanzoken rings, tracked as a group so striking one image disperses the rest. */
    private static final Map<UUID, List<XenoCloneEntity>> RINGS = new HashMap<>();

    /** Server-side lock-on target tracker; receives client sync via CloneTargetPacket. */
    public static final CloneTargetTracker<net.minecraft.world.entity.LivingEntity> TARGET_TRACKER =
            new CloneTargetTracker<>();

    private XenoCloneSystem() {
    }

    /** Bodies a split fighter is spread across, counting their own. */
    public static int bodyCount() {
        return Math.max(1, XenoServerConfig.multiFormBodies);
    }

    /**
     * Fraction of full power each body of a split fighter deals.
     *
     * <p>Mastery closes the gap the split normally costs; at
     * {@link CloneFormation#PERFECT_MASTERY} dividing is free and every body hits full.
     */
    public static float damageShare(ServerPlayer player) {
        return isSplit(player) ? CloneFormation.damageShare(SPLIT.get(player.getUUID()).bodies(), mastery(player)) : 1.0f;
    }

    public static boolean isSplit(ServerPlayer player) {
        return !clonesOf(player).isEmpty();
    }

    /** Reconciles membership against actual bodies, including non-death removals. */
    public static List<XenoCloneEntity> clonesOf(ServerPlayer player) {
        if (player == null) return List.of();
        CloneSplitState<XenoCloneEntity> state = SPLIT.get(player.getUUID());
        if (state == null) return List.of();
        for (XenoCloneEntity clone : state.members()) {
            if (!clone.isAlive() || clone.isRemoved() || clone.level() != player.level()) {
                state.remove(clone);
                clone.discard();
            }
        }
        if (state.isEmpty()) SPLIT.remove(player.getUUID(), state);
        return state.members();
    }

    static boolean owns(XenoCloneEntity clone) {
        CloneSplitState<XenoCloneEntity> state = SPLIT.get(clone.ownerUuid());
        return state != null && state.contains(clone);
    }

    private static void track(XenoCloneEntity clone) {
        OWNED.computeIfAbsent(clone.ownerUuid(), ignored -> new HashSet<>()).add(clone);
    }

    /** Starts a split, or requests recall. True only when a new split was created. */
    public static boolean toggleSplit(ServerPlayer player) {
        if (player == null) return false;
        if (isSplit(player)) {
            reunite(player);
            return false;
        }
        return split(player);
    }

    private static boolean split(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) return false;
        int bodies = bodyCount();
        if (bodies <= 1) return false;

        // Every body starts inside the fighter and travels out to its slot, so the split reads as
        // one person coming apart rather than three copies blinking into existence beside them.
        Vec3 origin = player.position();
        float availableHealth = player.getHealth();
        float share = CloneSplitState.healthShare(availableHealth, bodies);

        List<XenoCloneEntity> copies = new ArrayList<>();
        for (int slot = 1; slot < bodies; slot++) {
            XenoCloneEntity clone = ModEntities.CLONE.get().create(level);
            if (clone == null) continue;
            clone.moveTo(origin.x, origin.y, origin.z, player.getYRot(), player.getXRot());
            clone.configure(player, slot, Integer.MAX_VALUE, share);
            if (level.addFreshEntity(clone)) {
                copies.add(clone);
            }
        }
        if (copies.isEmpty()) return false;
        // A rejected spawn must not consume a share or leave a hole in the formation.
        CloneSplitState<XenoCloneEntity> state = new CloneSplitState<>(copies);
        bodies = state.bodies();
        share = CloneSplitState.healthShare(availableHealth, bodies);
        for (int i = 0; i < copies.size(); i++) {
            XenoCloneEntity clone = copies.get(i);
            clone.configure(player, i + 1, Integer.MAX_VALUE, share);
            clone.setFormationBodies(bodies);
            track(clone);
        }
        player.setHealth(availableHealth - share * copies.size());
        SPLIT.put(player.getUUID(), state);

        // The fighter takes the back slot. An opponent in front meets the copies first and cannot
        // tell from position which body is the one that can actually be hurt.
        double[] back = CloneFormation.slotOffset(player.getYRot(),
                CloneFormation.backSlot(bodies), bodies, XenoCloneEntity.FORMATION_RADIUS);
        player.teleportTo(origin.x + back[0], origin.y, origin.z + back[1]);

        int mastery = mastery(player);
        player.displayClientMessage(Component.literal(
                CloneFormation.isPerfected(mastery)
                        ? "§bShi Shin No Ken — " + bodies + " bodies at full power"
                        : "§bShi Shin No Ken — " + bodies + " bodies, "
                                + Math.round(CloneFormation.damageShare(bodies, mastery) * 100f)
                                + "% each"), true);
        return true;
    }

    /**
     * The fighter's locked-on target, read from the client-synced tracker rather than DMZ's
     * transient homing field (which resets after each technique fire and is unreliable for
     * sustained clone combat).
     */
    public static net.minecraft.world.entity.LivingEntity lockedTarget(Player owner) {
        if (!(owner instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        long now = level.getGameTime();
        net.minecraft.world.entity.LivingEntity target = TARGET_TRACKER.target(player.getUUID(), now,
                living -> living.isAlive() && !living.isRemoved() && living.level() == level);
        if (target != null) return target;
        // Fallback: DMZ homing target for cases where the client sync hasn't arrived yet.
        try {
            com.dragonminez.common.stats.StatsData data = com.dragonminez.common.stats.StatsProvider
                    .get(com.dragonminez.common.stats.StatsCapability.INSTANCE, player).orElse(null);
            if (data == null || data.getTechniques() == null) return null;
            int id = data.getTechniques().getHomingTargetId();
            if (id < 0) return null;
            return level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity living
                    && living.isAlive() ? living : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Fires clone-owned waves on the same server return as a successful owner wave release. */
    public static void mirrorKiWave(ServerPlayer owner,
                                    com.dragonminez.common.stats.techniques.KiAttackData attack,
                                    float chargeMultiplier) {
        if (owner == null || attack == null || attack.getKiType()
                != com.dragonminez.common.stats.techniques.KiAttackData.KiType.WAVE) return;
        net.minecraft.world.entity.LivingEntity target = lockedTarget(owner);
        for (XenoCloneEntity clone : List.copyOf(clonesOf(owner))) {
            if (!CloneCombatBridge.active(clone)) continue;
            if (target != null && (!CloneCombatBridge.validTarget(owner, target)
                    || owner.distanceToSqr(target) > CloneCombatPolicy.LEASH * CloneCombatPolicy.LEASH)) {
                target = null;
            }
            net.bullettrain.xenopixelsmod.compat.npc.NpcKiAttackDispatcher.fireMirroredWave(
                    clone, clone.combatProfile(), attack, chargeMultiplier, target);
        }
    }

    /** This fighter's mastery of the technique, 0 to {@link CloneFormation#PERFECT_MASTERY}. */
    public static int mastery(ServerPlayer player) {
        if (player == null) return 0;
        return net.bullettrain.xenopixelsmod.capability.XenoCapabilities.get(player)
                .map(d -> d.getMultiFormMastery()).orElse(0);
    }

    /** Recall stays pending until every surviving body arrives or is lost. */
    public static void reunite(ServerPlayer player) {
        if (player == null) return;
        List<XenoCloneEntity> copies = clonesOf(player);
        CloneSplitState<XenoCloneEntity> state = SPLIT.get(player.getUUID());
        if (state == null || !state.beginRecall()) return;
        for (XenoCloneEntity clone : copies) clone.recall();
        player.displayClientMessage(Component.literal("§7Reuniting"), true);
    }

    static void onRecallArrived(XenoCloneEntity clone, Player owner) {
        CloneSplitState<XenoCloneEntity> state = SPLIT.get(clone.ownerUuid());
        if (state == null || !owner.isAlive() || owner.level() != clone.level()) return;
        float returned = state.arrive(clone, clone.isAlive() ? clone.getHealth() : 0f);
        if (returned > 0f) owner.setHealth(CloneSplitState.reunitedHealth(
                owner.getHealth(), owner.getMaxHealth(), returned));
        if (state.isEmpty()) SPLIT.remove(clone.ownerUuid(), state);
    }

    /**
     * Leaves a single copy standing where a fighter was, for Zanzoken.
     *
     * <p>Stationary on purpose: the attacker has already committed to a swing at that spot, and an
     * image that walks away is not an image.
     */
    public static XenoCloneEntity leaveStationary(ServerPlayer player, Vec3 at, int lifetimeTicks) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return null;
        XenoCloneEntity clone = ModEntities.CLONE.get().create(level);
        if (clone == null) return null;
        clone.moveTo(at.x, at.y, at.z, player.getYRot(), player.getXRot());
        clone.configure(player, XenoCloneEntity.SLOT_STATIONARY, lifetimeTicks, 1.0f);
        if (!level.addFreshEntity(clone)) return null;
        track(clone);
        return clone;
    }

    /**
     * Surrounds a target with a closed ring of copies, for Zanzoken.
     *
     * <p>The read is that the fighter did not merely step aside — they are suddenly everywhere the
     * attacker could turn. Each copy faces inward at the target, and they hold position rather
     * than following, because a ring that drifts stops being a ring.
     *
     * @return how many copies were actually placed
     */
    public static List<XenoCloneEntity> encircle(ServerPlayer owner, Entity target, int count,
                                                 double radius, int lifetimeTicks, int skipSlot) {
        List<XenoCloneEntity> placed = new ArrayList<>();
        if (owner == null || target == null || !(owner.level() instanceof ServerLevel level)) {
            return placed;
        }
        int slots = Math.max(1, count);
        for (int i = 0; i < slots; i++) {
            // The dodger stands in one slot themselves, so no image is placed there.
            if (i == skipSlot) continue;
            double[] off = CloneFormation.ringOffset(i, slots, radius);
            double x = target.getX() + off[0];
            double z = target.getZ() + off[1];
            XenoCloneEntity clone = ModEntities.CLONE.get().create(level);
            if (clone == null) continue;
            // Face the middle of the ring, so every copy is looking at whoever is trapped in it.
            float yaw = ringFacing(target, x, z);
            clone.moveTo(x, target.getY(), z, yaw, 0f);
            clone.configure(owner, XenoCloneEntity.SLOT_STATIONARY, lifetimeTicks, 1.0f);
            if (level.addFreshEntity(clone)) {
                track(clone);
                placed.add(clone);
            }
        }
        if (!placed.isEmpty()) {
            disperseRing(owner.getUUID());
            RINGS.put(owner.getUUID(), new ArrayList<>(placed));
        }
        return placed;
    }

    /**
     * The Zanzoken images currently standing for this fighter, or an empty list.
     *
     * <p>Separate from {@link #clonesOf}: that is the Shi Shin No Ken split, bodies that fight.
     * These are the ring, bodies that only stand there and mislead, and the confusion the technique
     * applies to AI has to aim at these and not at the split.
     */
    public static List<XenoCloneEntity> ringOf(UUID ownerId) {
        List<XenoCloneEntity> ring = RINGS.get(ownerId);
        if (ring == null) return List.of();
        ring.removeIf(clone -> !clone.isAlive() || clone.isRemoved());
        if (ring.isEmpty()) {
            RINGS.remove(ownerId);
            return List.of();
        }
        return List.copyOf(ring);
    }

    /** Yaw that points from a ring slot at whoever is standing in the middle of it. */
    public static float ringFacing(Entity target, double x, double z) {
        return (float) (Math.toDegrees(Math.atan2(target.getZ() - z, target.getX() - x)) - 90.0);
    }

    /**
     * Drops the whole ring at once.
     *
     * <p>Called when any single image is struck: the attacker has committed and guessed, so the
     * trick has resolved either way and leaving the rest standing would only look like a bug.
     */
    public static void disperseRing(UUID ownerId) {
        List<XenoCloneEntity> ring = RINGS.remove(ownerId);
        if (ring == null) return;
        for (XenoCloneEntity clone : ring) {
            if (clone.isAlive()) clone.discard();
        }
        // The disguise ends with the bodies. Leaving the mark standing kept the fighter
        // untargetable after they had already been found.
        net.bullettrain.xenopixelsmod.combat.Bt3CombatEvents.clearAfterimages(ownerId);
    }

    /**
     * Stops anything but a player destroying a standing Zanzoken image.
     *
     * <p>The images exist to be swung at. Letting a mob delete one per hit -- and, since
     * {@code ZanzokenConfusion} now points every nearby attacker at an image, that is the first
     * thing that happens -- ended a ten-second disguise in a tick.
     *
     * @see net.bullettrain.xenopixelsmod.combat.ZanzokenRing
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void protectRingImages(
            net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof XenoCloneEntity image)) return;
        if (image.level().isClientSide()) return;
        List<XenoCloneEntity> ring = RINGS.get(image.ownerUuid());
        if (ring == null || !ring.contains(image)) return;

        boolean byPlayer = event.getSource().getEntity() instanceof Player
                || event.getSource().getDirectEntity() instanceof Player;
        if (!net.bullettrain.xenopixelsmod.combat.ZanzokenRing.canDestroyImage(byPlayer)) {
            // The swing still happened and the attacker is still committed to this body; it simply
            // does not remove it. Nothing about the attacker's state is touched.
            event.setCanceled(true);
            return;
        }
        // A player has guessed. Recorded here rather than at the removal, which is the only point
        // that still knows what dealt the damage.
        image.markRevealedByPlayer();
    }

    /** Death and all other removals forfeit the body's remaining health. */
    public static void onClonePopped(XenoCloneEntity clone) {
        if (clone == null || clone.level().isClientSide()) return;
        List<XenoCloneEntity> ring = RINGS.get(clone.ownerUuid());
        if (ring != null && ring.remove(clone)) {
            // One image lost is not the trick resolved. The ring ends when a player has picked a
            // body -- they have committed and guessed -- or when nothing is left standing.
            if (net.bullettrain.xenopixelsmod.combat.ZanzokenRing.disperseWholeRing(
                    clone.revealedByPlayer(), ring.size())) {
                disperseRing(clone.ownerUuid());
            } else {
                // Whoever was drawn onto this body is moved to another one rather than being handed
                // back the real fighter, so losing an image degrades the disguise instead of ending
                // it.
                net.bullettrain.xenopixelsmod.combat.ZanzokenConfusion.rehome(clone, ring);
            }
        }
        CloneSplitState<XenoCloneEntity> state = SPLIT.get(clone.ownerUuid());
        if (state != null) {
            state.remove(clone);
            if (state.isEmpty()) SPLIT.remove(clone.ownerUuid(), state);
        }
        Set<XenoCloneEntity> owned = OWNED.get(clone.ownerUuid());
        if (owned != null) {
            owned.remove(clone);
            if (owned.isEmpty()) OWNED.remove(clone.ownerUuid());
        }
    }

    /**
     * A divided fighter hits for a share of their power, wherever the hit came from.
     *
     * <p>Applied on the damage event rather than at the ten places this mod deals melee damage,
     * so it also covers DragonMineZ's own attacks and cannot drift out of step with the body
     * count. Dividing into four and hitting four times as hard would make the technique strictly
     * better than not using it; dividing is meant to buy angles, not damage.
     */
    private static final Map<UUID, Long> LAST_MASTERY = new HashMap<>();

    private static XenoCloneEntity damageClone(net.minecraft.world.damagesource.DamageSource source) {
        XenoCloneEntity clone = CloneCombatBridge.sourceClone(source.getEntity());
        return clone != null ? clone : CloneCombatBridge.sourceClone(source.getDirectEntity());
    }

    private static ServerPlayer damageOwner(net.minecraft.world.damagesource.DamageSource source) {
        XenoCloneEntity clone = damageClone(source);
        return clone != null ? CloneCombatBridge.owner(clone)
                : source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void protectCloneTargets(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        XenoCloneEntity clone = damageClone(event.getSource());
        if (clone == null) return;
        ServerPlayer owner = CloneCombatBridge.owner(clone);
        if (!CloneCombatBridge.active(clone) || !CloneCombatBridge.validTarget(owner, event.getEntity())
                || lockedTarget(owner) != event.getEntity()
                || owner.distanceToSqr(event.getEntity()) > CloneCombatPolicy.LEASH * CloneCombatPolicy.LEASH) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onSplitFighterDealsDamage(LivingDamageEvent.Pre event) {
        ServerPlayer attacker = damageOwner(event.getSource());
        if (attacker == null || !isSplit(attacker)) return;
        event.setNewDamage(event.getNewDamage() * damageShare(attacker));
    }

    @SubscribeEvent
    public static void onSuccessfulSplitDamage(LivingDamageEvent.Post event) {
        ServerPlayer owner = damageOwner(event.getSource());
        if (owner == null || !isSplit(owner) || !CloneCombatBridge.permittedTarget(owner, event.getEntity())) return;
        long now = owner.level().getGameTime();
        if (!CloneCombatPolicy.earnsMastery(now, LAST_MASTERY.getOrDefault(owner.getUUID(), Long.MIN_VALUE),
                event.getNewDamage(), mastery(owner))) return;
        net.bullettrain.xenopixelsmod.capability.XenoCapabilities.get(owner).ifPresent(data -> {
            data.addMultiFormMastery(1);
            LAST_MASTERY.put(owner.getUUID(), now);
        });
    }

    /** A fighter who logs out divided must not leave bodies behind. */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    /** Dying reunites you the hard way. */
    @SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    @SubscribeEvent
    public static void onLeaveLevel(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof XenoCloneEntity clone) onClonePopped(clone);
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        List<XenoCloneEntity> copies = OWNED.values().stream().flatMap(Set::stream).toList();
        SPLIT.clear();
        RINGS.clear();
        OWNED.clear();
        LAST_MASTERY.clear();
        TARGET_TRACKER.clear();
        for (XenoCloneEntity clone : copies) clone.discard();
    }

    /** Forced cleanup never refunds health and never starts a delayed recall. */
    public static void clear(ServerPlayer player) {
        if (player == null) return;
        TARGET_TRACKER.forget(player.getUUID());
        SPLIT.remove(player.getUUID());
        RINGS.remove(player.getUUID());
        LAST_MASTERY.remove(player.getUUID());
        Set<XenoCloneEntity> copies = OWNED.remove(player.getUUID());
        if (copies != null) for (XenoCloneEntity clone : copies) clone.discard();
    }

}
