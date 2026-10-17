package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3PostAttackFreeze;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Teardown and repair for combat freezes — the half of the freeze machinery that did not exist.
 *
 * <p>{@code NoAI} is written into the entity's save data by vanilla, so a freeze whose owner stopped
 * running is not a temporary state: it is baked into the chunk and the NPC comes back immobile after
 * every reload. The systems that apply freezes cleaned up after players (logout, clone, server stop)
 * but had no hook at all for a <b>mob</b> that chunk-unloaded or died mid-cast, and no way to undo
 * damage an older build had already written to disk.
 *
 * <p>Three jobs, all cheap and all off the tick hot path:
 * <ul>
 *   <li><b>Leave / death</b> — release the freeze and drop every tracking entry keyed on that entity,
 *       so {@code isControlledVictim} cannot stay true for an entity that is no longer here.</li>
 *   <li><b>Join from disk</b> — repair a freeze that outlived its owner, using the value
 *       {@link FreezeOwnership} stamped into the persistent data before it took the flag. This is
 *       what fixes worlds that are <i>already</i> broken, and it only ever touches entities carrying
 *       our own stamp, so a mod or map author's deliberately NoAI mob is left alone.</li>
 *   <li><b>Server stopping</b> — drop tracking. The stamps stay in the save, so a shutdown that
 *       interrupts a cinematic is repaired by the join path on the next boot.</li>
 * </ul>
 *
 * <p>Handlers run only on entities that are actually tracked or actually stamped, so the join path —
 * the one that fires on every chunk load — is a single {@code CompoundTag#contains} for the common
 * case and allocates nothing.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class FreezeLifecycle {

    private FreezeLifecycle() {
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        teardown(event.getEntity());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        teardown(event.getEntity());
    }

    /**
     * Order matters: the hold and the cast each restore through {@link FreezeOwnership} while their
     * own bookkeeping is still intact, and {@code releaseAll} runs last as the guarantee that nothing
     * can leave the level still frozen even if one of them had already been torn down out of order.
     */
    private static void teardown(Entity entity) {
        if (entity == null) return;
        try {
            V3PostAttackFreeze.forget(entity);
            V3TechniqueRuntime.forgetVictim(entity.getUUID());
            if (entity instanceof Mob mob) FreezeOwnership.releaseAll(mob);
            FreezeOwnership.forget(entity);
        } catch (Throwable t) {
            // A teardown fault must not cancel the unload or the death.
            XenoPixelsMod.LOGGER.error("Combat freeze teardown failed for {}", entity.getUUID(), t);
        }
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide()) return;
        if (!(entity instanceof LivingEntity living)) return;
        // Cheap gate first: the stamp is only present on an entity we froze and never released.
        if (!living.getPersistentData().contains(FreezeOwnership.STAMP_AI_BEFORE)) return;
        FreezeOwnership.repairOnLoad(living);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        FreezeOwnership.clearTracking();
    }

    /** One line for {@code /xenodebug}, so a growing count is visible before it becomes a symptom. */
    public static String statusLine() {
        return "freezeTracked=" + FreezeOwnership.trackedCount()
                + " cinematicHolds=" + V3PostAttackFreeze.holdCount();
    }
}
