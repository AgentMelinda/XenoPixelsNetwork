package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single owner of a combat-induced {@code NoAI} freeze.
 *
 * <p>{@code Mob#addAdditionalSaveData} writes {@code NoAI} and {@code readAdditionalSaveData} reads
 * it back, verified against the pinned 1.21.1 jar. A freeze that is applied and never released is
 * therefore <b>not</b> a transient state: it goes into the chunk save and returns on the next load as
 * a permanently frozen NPC. That is the bug this class exists to make unrepresentable.
 *
 * <p>Four systems used to call {@code setNoAi} directly — the V3 post-attack hold, the V3 technique
 * runtime, the V2 ultimate finisher, and {@code XenoNpcEntity#aiStep} itself — and each restored only
 * its own idea of the previous value. An interrupted cast, a victim whose {@code VICTIMS} entry was
 * dropped before the restore ran, or a chunk unload mid-cast each left the flag stuck, and because
 * {@code isControlledVictim} then stayed true forever {@code aiStep} took its early-return branch and
 * never cleared it again.
 *
 * <p>Callers now acquire a {@link Reason}. The first reason to arrive captures the real pre-freeze
 * value, the last one to leave restores it, and the capture is stamped into the entity's persistent
 * data so a crash or an unload between the two is repaired exactly on the next join — see
 * {@link FreezeLifecycle}.
 *
 * <p>Structured the same way as {@code V3Motion}: {@link Hold} is the pure lease state and carries no
 * entity reference, so the bookkeeping is unit-testable without booting Minecraft, and the statics
 * here are the thin entity-touching layer on top. Tracking is keyed by UUID for the same reason — an
 * unloaded NPC must not be retained by a static map. Server thread only, like the other combat maps
 * in this package.
 *
 * <p>Scope is deliberately {@code NoAI} alone. {@code NoGravity} is already captured and restored
 * symmetrically for every target type by {@code V3PostAttackFreeze}, and for players it is owned by
 * {@code V3Motion}; taking it here too would give two systems the same flag.
 *
 * <p>The Nearby-NPCs screen's operator freeze ({@code XenoNpcNearbyService#freezeToggle}) is also
 * deliberately <b>outside</b> this owner. It is meant to survive a reload — that is the whole point of
 * an admin freeze — so routing it through here would make {@link #repairOnLoad} undo it on the next
 * boot. It stays a direct writer with its own {@code FROZEN_TAG} marker. The two coexist correctly
 * because capture happens at acquire time: a combat freeze taken on an already-frozen NPC records
 * {@code true} and puts it back, rather than waking an NPC an operator parked.
 */
public final class FreezeOwnership {

    /**
     * Who is holding a freeze.
     *
     * <p><b>One holder per reason.</b> Holding is set membership, not a refcount, so acquiring the
     * same reason twice is idempotent and one release clears it. That is deliberate: the
     * {@link #RECOVERY} holder is {@code XenoNpcEntity#aiStep}, which borrows and returns the flag
     * every single tick, and a refcount there would climb forever and freeze the NPC permanently the
     * moment one release was missed. Two <i>different</i> systems that both need to hold a freeze at
     * once must therefore use two different reasons — which is why the technique runtime, the
     * post-attack hold and the finisher are separate constants rather than one shared value.
     */
    public enum Reason {
        /** A finished cinematic's victim hold ({@code V3PostAttackFreeze}). */
        CINEMATIC,
        /** An in-flight rush/ultimate controlling its victim ({@code V3TechniqueRuntime}). */
        TECHNIQUE,
        /** {@code XenoNpcEntity#aiStep}'s transient per-tick borrow while it runs vanilla bookkeeping. */
        RECOVERY,
        /** A V2 ultimate finisher holding its target ({@code UltimateFinisher}). */
        FINISHER
    }

    /**
     * Persistent-data stamp recording the {@code NoAI} value to restore. Presence of the tag — not
     * its value — is what means "XenoPixels froze this and owes it a restore", which is what lets a
     * reload repair a freeze whose owner died with the previous JVM.
     */
    static final String STAMP_AI_BEFORE = "xenopixelsmod:freeze_ai_before";

    private static final Map<UUID, Hold> HELD = new HashMap<>();

    private FreezeOwnership() {
    }

    /**
     * Pure freeze-lease state for one entity: which reasons are holding, and the {@code NoAI} value to
     * put back when the last of them lets go. Holds no entity reference, so it is safe to keep in a
     * static map and safe to test on its own.
     */
    public static final class Hold {
        private final EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
        private boolean noAiBefore;

        /**
         * Takes {@code reason}'s share of the freeze.
         *
         * @param noAiNow the entity's current {@code NoAI} value, read by the caller so this class
         *                never has to touch an entity
         * @return true when this acquire is the first holder, meaning the caller must now apply the
         *         freeze and stamp it. False when another reason already had it, so the flag is
         *         already correct and the captured value must not be re-snapshotted.
         */
        public boolean acquire(Reason reason, boolean noAiNow) {
            if (reason == null) return false;
            if (!reasons.isEmpty()) {
                reasons.add(reason);
                return false;
            }
            reasons.add(reason);
            noAiBefore = noAiNow;
            return true;
        }

        /**
         * Drops {@code reason}'s share.
         *
         * @return the {@code NoAI} value to restore, or null when another reason still holds and the
         *         caller must therefore leave the flag exactly as it is. Null is also returned for a
         *         reason that was never held, which makes this idempotent.
         */
        public Boolean release(Reason reason) {
            if (reason == null || !reasons.remove(reason)) return null;
            if (!reasons.isEmpty()) return null;
            return noAiBefore;
        }

        /**
         * Lets go of every reason at once, for teardown where holder identity no longer matters — the
         * entity is leaving the level, died, or the server is stopping.
         *
         * @return the {@code NoAI} value to restore, or null when nothing was held
         */
        public Boolean releaseAll() {
            if (reasons.isEmpty()) return null;
            reasons.clear();
            return noAiBefore;
        }

        public boolean held() {
            return !reasons.isEmpty();
        }

        public boolean heldBy(Reason reason) {
            return reason != null && reasons.contains(reason);
        }

        public List<Reason> reasons() {
            return List.copyOf(reasons);
        }

        /** The value a release would restore, for diagnostics. Meaningless while nothing is held. */
        public boolean noAiBefore() {
            return noAiBefore;
        }
    }

    /**
     * Takes {@code reason}'s share of this mob's freeze, applying {@code NoAI} and stamping the
     * pre-freeze value if nobody had it yet.
     *
     * @return true when {@code reason} is now held. False only for a null mob or null reason; a repeat
     *         acquire of an already-held reason is a no-op that still reports true, so callers never
     *         have to track whether they already asked.
     */
    public static boolean acquire(Mob mob, Reason reason) {
        if (mob == null || reason == null) return false;
        Hold hold = HELD.computeIfAbsent(mob.getUUID(), ignored -> new Hold());
        // Read the flag before the hold can consider itself first, so what gets captured is the
        // entity's real state and not one we already changed.
        boolean first = hold.acquire(reason, mob.isNoAi());
        if (first) {
            // Stamp before the flag changes, so a crash between here and the restore is still
            // repairable from the save alone.
            mob.getPersistentData().putBoolean(STAMP_AI_BEFORE, hold.noAiBefore());
            if (!mob.isNoAi()) mob.setNoAi(true);
        }
        return true;
    }

    /**
     * Drops {@code reason}'s share. The pre-freeze value goes back only when the last holder leaves,
     * so overlapping cinematics cannot wake a mob one of them is still holding.
     *
     * <p>Idempotent, which is what makes the teardown paths in {@link FreezeLifecycle} safe to call
     * from several events at once.
     */
    public static void release(Mob mob, Reason reason) {
        if (mob == null || reason == null) return;
        Hold hold = HELD.get(mob.getUUID());
        if (hold == null) return;
        Boolean restore = hold.release(reason);
        if (restore == null) {
            // Another reason still holds, or this one never did. Drop the tracking entry only when
            // nothing is left, so a repeated release cannot leave an empty Hold behind.
            if (!hold.held()) HELD.remove(mob.getUUID(), hold);
            return;
        }
        HELD.remove(mob.getUUID(), hold);
        mob.getPersistentData().remove(STAMP_AI_BEFORE);
        // An editor-placed or deliberately static NPC was NoAI before we arrived and must stay so.
        if (mob.isNoAi() != restore) mob.setNoAi(restore);
    }

    /**
     * Restores every reason at once and stops tracking. For teardown where the holder identity no
     * longer matters — the mob is leaving the level, died, or the server is stopping.
     */
    public static void releaseAll(Mob mob) {
        if (mob == null) return;
        Hold hold = HELD.remove(mob.getUUID());
        if (hold == null) return;
        Boolean restore = hold.releaseAll();
        if (restore == null) return;
        mob.getPersistentData().remove(STAMP_AI_BEFORE);
        if (mob.isNoAi() != restore) mob.setNoAi(restore);
    }

    /**
     * Stops tracking without touching any entity, for when the entity is already unreachable. The
     * stamp stays in its save data so the next load repairs it — see {@link #repairOnLoad}.
     */
    public static void forget(UUID id) {
        if (id != null) HELD.remove(id);
    }

    public static void forget(Entity entity) {
        if (entity != null) HELD.remove(entity.getUUID());
    }

    /**
     * Undoes a freeze that outlived its owner, using the value stamped into the save. Called when an
     * entity joins a level, which is the only point where a freeze leaked by an older build — or by a
     * crash — can be detected and repaired.
     *
     * <p>Only ever acts on an entity carrying our own stamp, so a mod or map author's deliberately
     * NoAI mob is left alone.
     *
     * @return true when a stale freeze was actually repaired
     */
    public static boolean repairOnLoad(LivingEntity entity) {
        if (entity == null) return false;
        UUID id = entity.getUUID();
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(STAMP_AI_BEFORE)) {
            // No stamp, so we never froze it. Drop any tracking left over from a previous life of
            // this UUID rather than assuming the flag belongs to us.
            HELD.remove(id);
            return false;
        }
        boolean restore = data.getBoolean(STAMP_AI_BEFORE);
        data.remove(STAMP_AI_BEFORE);
        HELD.remove(id);
        if (!(entity instanceof Mob mob)) return false;
        if (mob.isNoAi() == restore) return false;
        mob.setNoAi(restore);
        XenoPixelsMod.LOGGER.debug("Repaired a leaked combat freeze on {} (NoAI -> {})",
                mob.getType().toShortString(), restore);
        return true;
    }

    /** True while any reason holds this entity's freeze. */
    public static boolean isFrozen(UUID id) {
        Hold hold = id == null ? null : HELD.get(id);
        return hold != null && hold.held();
    }

    public static boolean isFrozen(Entity entity) {
        return entity != null && isFrozen(entity.getUUID());
    }

    /** True while this specific reason holds the freeze. */
    public static boolean isHeld(UUID id, Reason reason) {
        Hold hold = id == null ? null : HELD.get(id);
        return hold != null && hold.heldBy(reason);
    }

    public static boolean isHeld(Entity entity, Reason reason) {
        return entity != null && isHeld(entity.getUUID(), reason);
    }

    /**
     * The reasons currently holding this entity, for debug reporting. Allocates, so this is for
     * commands and tests — never a per-tick path.
     */
    public static List<Reason> reasonsFor(UUID id) {
        Hold hold = id == null ? null : HELD.get(id);
        return hold == null ? List.of() : hold.reasons();
    }

    /** Number of entities currently tracked. Bounded by live frozen mobs, not by uptime. */
    public static int trackedCount() {
        return HELD.size();
    }

    /**
     * Drops all tracking without restoring anything, for a JVM-level teardown where the entities are
     * gone. Stamps survive in the save data, so the next load repairs them.
     */
    public static void clearTracking() {
        HELD.clear();
    }
}
